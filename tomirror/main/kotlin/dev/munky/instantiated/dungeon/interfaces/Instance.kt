package dev.munky.instantiated.dungeon.interfaces

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.Identifiable
import dev.munky.instantiated.dungeon.mob.Id2WeakDungeonMobMap
import dev.munky.instantiated.exception.DungeonException
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.*

interface Instance : Identifiable {
    val uuid: UUID // have to initialize it in the implementation
    val format : Format
    var cache : CacheState
    var difficulty : Double
    val rooms: LinkedHashMap<IdKey, out RoomInstance>
    val activeMobs : Id2WeakDungeonMobMap // TODO move active mobs to outside of the instance
    val locationInWorld: Location
    var doorKeys : Int
    val players : List<UUID>
    val onlinePlayers : List<Player> get() = players.mapNotNull { Bukkit.getPlayer(it) }
    fun spawnPlayer(player:Player) {
        val l = locationInWorld.add(
            format.spawnVector.x.toDouble(),
            format.spawnVector.y.toDouble(),
            format.spawnVector.z.toDouble()
        )
        val oldY = l.y
        l.y += 5.5f
        val result = l.world.rayTrace(
            l,
            Vector(0, -1, 0),
            255.0,
            FluidCollisionMode.ALWAYS,
            true,
            1.0,
            null
        )
        if (result != null) l.y = result.hitPosition.y + 0.25
        else l.y = oldY + 0.25
        player.teleport(l)
    }
    fun addPlayer(player:Player)
    fun addPlayers(players: List<Player>){
        players.forEach { addPlayer(it) }
    }
    fun removePlayer(player: UUID)
    fun removePlayers(players: List<UUID>){
        players.forEach { removePlayer(it) }
    }
    fun getClosestRoom(player: Player) : RoomInstance?
    fun getRoomAt(location: Location) : RoomInstance?
    @Throws(DungeonException::class)
    fun remove(context: RemovalReason, cache: Boolean)

    enum class CacheState{
        CACHED,
        NEVER_CACHED,
        PREVIOUSLY_CACHED;
        val isCached : Boolean get() {
            return this == CACHED
        }
        val wasCached : Boolean get() {
            return this == CACHED || this == PREVIOUSLY_CACHED
        }
        val neverCached : Boolean get() {
            return this == NEVER_CACHED
        }
    }

    enum class RemovalReason {
        NO_PLAYERS_LEFT,
        FORMAT_CHANGE,
        PLUGIN_RELOAD,
        EXCEPTION_THROWN,
        PLUGIN_DISABLE
    }

    val playerLocations: Map<UUID, Location>
}