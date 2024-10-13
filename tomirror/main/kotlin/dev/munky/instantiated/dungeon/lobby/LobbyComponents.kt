package dev.munky.instantiated.dungeon.lobby

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operation
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.world.block.BlockTypes
import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.util.asOptional
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomFormat
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.dungeon.mob.Id2WeakDungeonMobMap
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.stackMessage
import dev.munky.instantiated.util.toBlockVector3
import dev.munky.instantiated.util.toVector3f
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector3ic
import org.koin.core.component.get
import java.io.File
import java.util.*

class LobbyFormat(
    override val identifier: IdKey,
    override var spawnVector: Vector3f,
    var schematicFile: File
) : Format{
    override val instances: MutableSet<LobbyInstance> = mutableSetOf() // functions normally
    val lobbyRoom = object : LobbyRoomFormat {
        override val parent: Format = this@LobbyFormat
        override var origin: Vector3ic = Vector3i(0)
        override val identifier: IdKey = parent.identifier // rooms of lobbies share id
        override var keyDropMode: RoomFormat.KeyDropMode = RoomFormat.KeyDropMode.MARKED_ROOM_MOB_KILL // unused
        override var keyMaterial: Material = Material.STONE // unused
        override val schematic: Clipboard = ClipboardFormats
            .findByFile(schematicFile).asOptional
            .orElseThrow { DungeonExceptions.Generic.consume("Schematic for lobby '$identifier' does not exist (${schematicFile.name})") }
            .getReader(schematicFile.inputStream())
            .use { it.read() }
        override var box = run {
            val cuboid = schematic.region as CuboidRegion
            Box(cuboid.pos1.toVector3f,cuboid.pos2.toVector3f)
        }
        override fun instance(instance: Instance): RoomInstance {
            TODO("Not yet implemented")
        }
    }
    override val rooms: MutableMap<IdKey, RoomFormat> = mutableMapOf(identifier to lobbyRoom)
    override fun instance(location: Location, option: Format.InstanceOption): Instance {
        // TODO caching
        return LobbyInstance(this, location, option == Format.InstanceOption.CACHE)
    }
}
interface LobbyRoomFormat : RoomFormat {
    val schematic : Clipboard
}
/**
 * The lobby room format holds physical info, the lobby dungeon instance holds player info
 */
class LobbyInstance(
    override val format: LobbyFormat,
    override val locationInWorld: Location,
    cache : Boolean
) : Instance {
    override val identifier: IdKey get() = format.identifier
    override val uuid: UUID = UUID.randomUUID()
    override var cache: Instance.CacheState = if (cache) Instance.CacheState.CACHED else Instance.CacheState.NEVER_CACHED
    override var difficulty: Double = DungeonManager.DEFAULT_DIFFICULTY
    private val lobbyRoom = object : RoomInstance {
        override val parent: LobbyInstance = this@LobbyInstance
        override val format: LobbyRoomFormat = parent.format.lobbyRoom
        override val identifier: IdKey = format.identifier
        override val realVector get() = parent.locationInWorld
        override var areMobsSpawned: Boolean = false
        override val origin = format.origin
        override var box: Box = format.box
    }
    override val rooms: LinkedHashMap<IdKey, RoomInstance> = LinkedHashMap(mutableMapOf(identifier to lobbyRoom))
    override val activeMobs: Id2WeakDungeonMobMap = Id2WeakDungeonMobMap() // unused
    override var doorKeys: Int = 0
    private val playerMap: MutableMap<UUID, Location> = HashMap()
    override val onlinePlayers : List<Player> get() = playerMap.keys.mapNotNull{ uuid -> Bukkit.getPlayer(uuid) }
    override val playerLocations : Map<UUID,Location> get() = playerMap
    override val players : List<UUID> get() = playerMap.keys.toList()
    private val pastedRegion : CuboidRegion
    init{
        val editSession = WorldEdit.getInstance().newEditSessionBuilder()
            .world(BukkitAdapter.adapt(locationInWorld.world))
            .fastMode(true)
            .checkMemory(false)
            .build()
        val holder = ClipboardHolder(format.lobbyRoom.schematic)
            .createPaste(editSession)
            .copyEntities(false)
            .to(locationInWorld.toBlockVector3())
            .ignoreAirBlocks(false)
        val operation: Operation = holder.build()
        Operations.complete(operation)
        pastedRegion = format.lobbyRoom.schematic.region.clone() as CuboidRegion
        println("schem origin ${format.lobbyRoom.schematic.origin} and ${format.lobbyRoom.schematic.region}")
        pastedRegion.shift(format.lobbyRoom.schematic.region.dimensions.multiply(-1))
        pastedRegion.shift(locationInWorld.toBlockVector3())
        println("min point ${pastedRegion.minimumPoint} max point ${pastedRegion.maximumPoint}")
        editSession.close()
        if (!this.cache.wasCached)
            format.instances.add(this)
        plugin.logger.debug("Injected instance '$identifier', modified ${pastedRegion.volume}")
    }
    override fun addPlayer(player: Player) {
        var location = plugin.get<DungeonManager>().getCurrentDungeon(player.uniqueId)?.playerLocations?.get(player.uniqueId)
            ?: player.location
        if (location.world == locationInWorld.world) {
            val maybeLocation = playerMap[player.uniqueId].asOptional
            location = if (maybeLocation.isPresent) maybeLocation.get() else Bukkit.getWorlds().first().spawnLocation
        }
        playerMap[player.uniqueId] = location
        plugin.logger.debug("Player (${player.name}) added to '$identifier'")
        spawnPlayer(player)
        if (cache.isCached) cache = Instance.CacheState.PREVIOUSLY_CACHED
    }
    override fun removePlayer(player: UUID) {
        val onlinePlayer = Bukkit.getPlayer(player)
        val teleportLocation = playerLocations[player]
        playerMap.remove(player)
        if (onlinePlayer != null && onlinePlayer.isOnline && teleportLocation.asOptional.isPresent && onlinePlayer.location != teleportLocation) {
            onlinePlayer.teleport(teleportLocation!!)
        }
        plugin.logger.debug("Removed player '${onlinePlayer?.name ?: player}' from dungeon '$identifier'")
        if (players.isEmpty()) {
            try {
                this.remove(Instance.RemovalReason.NO_PLAYERS_LEFT, true)
            } catch (e: Throwable) {
                plugin.logger.severe("Could not remove instance:${e.stackMessage()}")
            }
        }
    }
    override fun getClosestRoom(player: Player): RoomInstance? = getRoomAt(player.location)?.let {
        if (this.lobbyRoom.box.center.distance(player.location.toVector3f) < 50) return@let this.lobbyRoom else null
    }
    override fun getRoomAt(location: Location): RoomInstance? = if (lobbyRoom.box.contains(location.toVector3f)) lobbyRoom else null
    override fun remove(
        context: Instance.RemovalReason,
        cache: Boolean
    ) {
        onlinePlayers.forEach{ removePlayer(it.uniqueId) }
        if (this.cache.wasCached && cache){
            plugin.logger.debug("Re-caching instance of '$identifier' instead of explicit removal")
            this.cache = Instance.CacheState.CACHED
            plugin.logger.info("Re-Cached instance of '$identifier'")
        }else{
            format.instances.remove(this)
            WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(locationInWorld.world)).use {
                it.setBlocks(pastedRegion as Region, BlockTypes.AIR)
            }
            plugin.logger.info("Removed instance of '$identifier'")
        }
        System.gc()
    }
}