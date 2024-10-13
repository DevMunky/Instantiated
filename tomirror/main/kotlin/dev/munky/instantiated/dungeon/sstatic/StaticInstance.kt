package dev.munky.instantiated.dungeon.sstatic

import com.fastasyncworldedit.core.extent.clipboard.CPUOptimizedClipboard
import com.fastasyncworldedit.core.extent.transform.PatternTransform
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.WorldEditException
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operation
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.world.block.BlockTypes
import dev.munky.instantiated.PluginState
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.util.asOptional
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.dungeon.mob.Id2WeakDungeonMobMap
import dev.munky.instantiated.event.DungeonCacheEvent
import dev.munky.instantiated.exception.DungeonException
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.exception.PhysicalRemovalException
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import dev.munky.instantiated.util.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.joml.Vector3f
import org.koin.core.component.get
import java.io.FileInputStream
import java.util.*


class StaticInstance
@Throws(DungeonException::class)
internal constructor(
    override val format : StaticFormat,
    override val locationInWorld : Location,
    cache : Boolean = false
) : Instance {
    companion object{
        val MANAGER: DungeonManager = plugin.get<DungeonManager>()
    }
    override val uuid: UUID = UUID.randomUUID()
    override var cache = if (cache) Instance.CacheState.CACHED else Instance.CacheState.NEVER_CACHED
    private var pastedClipboard: Result<Clipboard> = Result.failure(IllegalStateException("Not yet initialized"))
    override var difficulty: Double = 1.0
    override val rooms: LinkedHashMap<IdKey, StaticRoomInstance> = LinkedHashMap()
    override var doorKeys = 0 // the number of keys that are left for the dungeon.
    /**
     * Keys are the identifier of the room, values are
     * weak maps of keys living entity and values dungeon mob.
     * This inner map is Weak, and gives priority to garbage collection
     */
    override val activeMobs: Id2WeakDungeonMobMap = Id2WeakDungeonMobMap()
    // player uuid mapped to locationInWorld before they were teleported in
    private val playerMap: MutableMap<UUID, Location> = HashMap()
    override val onlinePlayers : List<Player> get() {
        return playerMap.keys.mapNotNull{ uuid -> Bukkit.getPlayer(uuid) }
    }
    override val playerLocations : Map<UUID,Location> get() = playerMap
    override val players : List<UUID> get(){
        return playerMap.keys.toList()
    }
    override val identifier : IdKey get() = format.identifier
    init{
        val pasteResult = runCatching {
            val schemfile = format.schematic
            if (schemfile == null) {
                val clipboard = CPUOptimizedClipboard(
                    CuboidRegion(
                        BlockVector3.ZERO,
                        BlockVector3.at(10,10,10)
                    )
                )
                return@runCatching clipboard
            }
            val clipboardFormat = ClipboardFormats.findByFile(schemfile)
                ?: throw IllegalArgumentException("No schematic found by file name '${schemfile.name}'." +
                        " Make sure it is a valid schematic file.")
            var clipboard: Clipboard
            val blockVector3 = BlockVector3.at(locationInWorld.x, locationInWorld.y, locationInWorld.z)
            clipboardFormat.getReader(FileInputStream(schemfile)).use { clipboardReader ->
                val editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(locationInWorld.world))
                    .fastMode(true)
                    .checkMemory(false)
                    .build()
                clipboard = clipboardReader.read()

                val origin = clipboard.origin

                val holder = ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .copyEntities(false)
                    .to(blockVector3)
                    .ignoreAirBlocks(false)

                val operation: Operation = holder.build()

                clipboard = editSession.lazyCopy(clipboard.region)

                clipboard.origin = origin
                Operations.complete(operation)
                plugin.logger.debug("Injected instance '$identifier', modified ${clipboard.volume}")
                editSession.close()
                if (!this.cache.wasCached)
                    format.instances.add(this)
            }
            clipboard
        }
        pastedClipboard = pasteResult
        pastedClipboard.onSuccess {
            for (room in format.rooms.values) {
                rooms[room.identifier] = room.instance(this)
            }
        }.onFailure {
            runCatching{ // do nothing with this exception
                this.remove(
                    Instance.RemovalReason.EXCEPTION_THROWN,
                    plugin.state.isState(PluginState.PROCESSING) && cache && this.cache.wasCached
                )
            }
            throw it
        }
    }

    override fun getClosestRoom(player: Player): RoomInstance? =
        getRoomAt(player.location)
            ?: run {
                val playerVector = Vector3f(player.x.toFloat(), player.y.toFloat(), player.z.toFloat())
                rooms.values
                    .map {
                        it to it.box.center.distance(playerVector)
                    }
                    .filter { it.second < 50 }
                    .sortedWith { o1, o2 -> (o1.second - o2.second).toInt() }
                    .map { it.first }
                    .firstOrNull()
            }

    override fun getRoomAt(location: Location): RoomInstance? {
        return rooms.values.firstOrNull { p ->
            p.box.contains(location.toVector3f)
        }
    }

    @Throws(PhysicalRemovalException::class)
    override fun remove(context: Instance.RemovalReason, cache: Boolean) {
        removePlayers()
        for (room in rooms.values) {
            room.remove()
        }
        rooms.clear()
        removeMobs() // just in case type thing
        if (this.cache.wasCached && cache && plugin.state.isSafe){
            val cacheEvent = DungeonCacheEvent(this,locationInWorld)
            cacheEvent.callEvent()
            if (cacheEvent.isCancelled){
                format.instances.remove(this)
                removePhysical(context)
                return
            }
            plugin.logger.debug("Re-caching instance of '$identifier' instead of explicit removal")
            for (room in rooms) {
                room.value.remove()
            }
            for (roomFormat in format.rooms.values) {
                rooms[roomFormat.identifier] = roomFormat.instance(this)
            }
            this.cache = Instance.CacheState.CACHED
            plugin.logger.info("Re-Cached instance of '$identifier'")
        }else{
            format.instances.remove(this)
            removePhysical(context)
        }
        System.gc()
    }

    private fun removePhysical(context: Instance.RemovalReason) {
        val worldedit = true
        try {
            plugin.logger.info("Removing instance of dungeon '$identifier' because $context")
            if (worldedit){
                if (!WorldEdit.getInstance().platformManager.isInitialized || pastedClipboard.isFailure) { // second condition stops an unchecked exception while disabling
                    plugin.logger.debug("Tried to remove an instance that was never actually instanced")
                }
                WorldEdit.getInstance().newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(locationInWorld.world))
                    .fastMode(true)
                    .checkMemory(false)
                    .build().use { session ->
                        val operation = ClipboardHolder(pastedClipboard.getOrThrow())
                            .createPaste(PatternTransform(session.extent, BlockTypes.AIR))
                            .to(BlockVector3.at(locationInWorld.x, locationInWorld.y, locationInWorld.z))
                            .ignoreAirBlocks(false)
                            .copyEntities(false)
                            .build()
                        try {
                            Operations.complete(operation)
                            plugin.logger
                                .debug("Removed dungeon of '$identifier' , modified ${pastedClipboard.getOrThrow().volume} blocks")
                        } catch (e: WorldEditException) {
                            e.printStackTrace()
                        }finally {
                            pastedClipboard.onSuccess { it.close() }
                        }
                    }
            }
            // removing a chunk with NMS is nigh impossible. Maybe one day ill get the chance to talk to spottedleaf and actually find a method :shrug:
        } catch (e: Exception) {
            throw DungeonExceptions.PhysicalRemoval.consume(this, e)
        }
    }

    private fun removeMobs() {
        for (mobPair in activeMobs.values) {
            for (living in mobPair.keys) try {
                living.remove()
            } catch (e: Exception) {
                plugin.logger.warning("Error while cleaning up '$identifier': ${e.stackMessage()}")
            }
        }
        if (pastedClipboard.isSuccess) {
            val region: Region = pastedClipboard.getOrThrow().region.clone()
            region.shift(BlockVector3.ZERO.subtract(pastedClipboard.getOrThrow().origin))
            region.shift(locationInWorld.toBlockVector3())
            if (Bukkit.isPrimaryThread()){
                for (entity in locationInWorld.world.entities) {
                    // removes EVERY entity that is inside the region that the schematic encompasses
                    if (region.contains(entity.x.toInt(), entity.y.toInt(), entity.z.toInt())) {
                        if (entity !is Player) entity.remove()
                    }
                }
            }else{
                Schedulers.SYNC.submit {
                    for (entity in locationInWorld.world.entities) {
                        // removes EVERY entity that is inside the region that the schematic encompasses
                        if (region.contains(entity.x.toInt(), entity.y.toInt(), entity.z.toInt())) {
                            if (entity !is Player) entity.remove()
                        }
                    }
                }
            }
        }
        activeMobs.clear()
    }

    private fun removePlayers() {
        for (player in players) {
            val online = Bukkit.getPlayer(player)
            val teleportLocation = playerLocations[player]
            if (online != null && online.isOnline && teleportLocation.asOptional.isPresent) {
                if (online.location != teleportLocation)
                    online.teleport(teleportLocation!!)
            }
            playerMap.remove(player)
            plugin.logger.debug("Removed player '$player' from dungeon '$identifier'")
        }
        playerMap.clear()
    }

    private fun handleCurrentDungeon(player: Player): Location {
        val instance = MANAGER.getCurrentDungeon(player.uniqueId) ?: return player.location
        return instance.playerLocations[player.uniqueId]
            ?: throw IllegalStateException("Player did not have a location set in an instance they existed in")
    }

    override fun addPlayer(player: Player){
        var location = handleCurrentDungeon(player)
        if (location.world == locationInWorld.world) {
            val maybeLocation = playerMap[player.uniqueId].asOptional
            location = if (maybeLocation.isPresent) maybeLocation.get() else Bukkit.getWorlds().first().spawnLocation
        }
        playerMap[player.uniqueId] = location
        plugin.logger.debug("Player (${player.name}) added to '$identifier'")
        spawnPlayer(player)
        if (cache.isCached) cache = Instance.CacheState.PREVIOUSLY_CACHED
    }

    private fun movePlayerOut(player: Player){
        val teleportLocation = playerLocations[player.uniqueId]
        playerMap.remove(player.uniqueId)
        if (
            !player.isOnline
            || teleportLocation == null
            || player.location == teleportLocation
        ) return
        player.teleport(teleportLocation)
    }

    override fun removePlayer(player: UUID) {
        val onlinePlayer = Bukkit.getPlayer(player)
        if (onlinePlayer != null) movePlayerOut(onlinePlayer)
        plugin.logger.debug("Removed player '${onlinePlayer?.name ?: player}' from dungeon '$identifier'")
        if (players.isEmpty() && plugin.state.isSafe) {
            try {
                this.remove(Instance.RemovalReason.NO_PLAYERS_LEFT, true)
            } catch (e: DungeonException) {
                plugin.logger.severe("Could not remove instance:${e.stackMessage()}")
            }
        }
    }
}