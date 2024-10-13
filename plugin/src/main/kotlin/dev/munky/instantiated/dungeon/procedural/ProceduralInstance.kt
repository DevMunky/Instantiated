package dev.munky.instantiated.dungeon.procedural

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.dungeon.mob.Id2WeakDungeonMobMap
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*

class ProceduralInstance(
    override val format: ProceduralFormat,
    override val locationInWorld: Location,
    cache: Boolean
) : Instance {
    override val identifier: IdKey = format.identifier
    override val uuid: UUID = UUID.randomUUID()
    override var cache: Instance.CacheState = if (cache) Instance.CacheState.CACHED else Instance.CacheState.NEVER_CACHED
    override var difficulty: Double = DungeonManager.DEFAULT_DIFFICULTY
    override val rooms: LinkedHashMap<IdKey, ProceduralRoomInstance>
    init {
        val generator = SimpleProceduralGenerator(format,10)
        generator.generate()
        rooms = generator.getRoomMap(this)
    }
    override val activeMobs: Id2WeakDungeonMobMap = Id2WeakDungeonMobMap()
    override var doorKeys: Int = 0
    override val players: List<UUID> get() = playerMap.keys.toList()
    override val playerLocations: Map<UUID, Location> get() = playerMap
    // main backing map for players
    private val playerMap: MutableMap<UUID, Location> = HashMap()
    override fun addPlayer(player: Player) {
        TODO("Not yet implemented")
    }
    override fun removePlayer(player: UUID) {
        TODO("Not yet implemented")
    }
    override fun getClosestRoom(player: Player): RoomInstance? {
        TODO("Not yet implemented")
    }
    override fun getRoomAt(location: Location): RoomInstance? {
        TODO("Not yet implemented")
    }
    override fun remove(context: Instance.RemovalReason, cache: Boolean) {
        TODO("Not yet implemented")
    }
}