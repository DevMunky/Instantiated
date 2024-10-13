package dev.munky.instantiated.dungeon.interfaces

import dev.munky.instantiated.common.structs.Identifiable
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.exception.DungeonException
import org.bukkit.Location
import org.joml.Vector3f

interface Format : Identifiable {
    override val identifier : IdKey
    val instances : MutableSet<out Instance>
    var spawnVector : Vector3f
    val cached : Set<Instance> get(){
        return instances.filter { i -> i.cache.isCached }.toSet()
    }
    val rooms : MutableMap<IdKey, out RoomFormat>
    @Throws(DungeonException::class)
    fun instance(location: Location, option: InstanceOption) : Instance
    enum class InstanceOption{
        CACHE,
        NEW_NON_CACHED,
        CONSUME_CACHE,
    }
}
// option to create a new dungeon with no players if you wanted for some reason