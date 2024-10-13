package dev.munky.instantiated.dungeon.sstatic

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.data.loader.ComponentStorage
import dev.munky.instantiated.dungeon.component.NeedsInitialized
import dev.munky.instantiated.dungeon.component.NeedsShutdown
import dev.munky.instantiated.dungeon.component.TraitContext
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.toLocation
import org.joml.Vector3f
import org.joml.Vector3i
import org.koin.core.component.get

class StaticRoomInstance(
    override val parent: StaticInstance,
    override val format: StaticRoomFormat
) : RoomInstance {
    override val origin = format.origin
    override val identifier: IdKey = format.identifier
    private val shift = Vector3i(parent.locationInWorld.x.toInt(),parent.locationInWorld.y.toInt(),parent.locationInWorld.z.toInt()).add(origin)
    override val realVector = shift.toLocation(parent.locationInWorld.world)
    override var box = format.box.copy() + Vector3f(shift.x.toFloat(), shift.y.toFloat(), shift.z.toFloat())
    override var areMobsSpawned: Boolean = false

    init{
        val components = plugin.get<ComponentStorage>()[this.format] ?: ArrayList()
        for (c in components){
            if (c is NeedsInitialized) c.initialize(TraitContext(this, null))
        }
    }

    fun remove() {
        val components = plugin.get<ComponentStorage>()[this.format] ?: ArrayList()
        for (c in components){
            if (c is NeedsShutdown) c.shutdown(TraitContext(this, null))
        }
        parent.activeMobs[identifier].keys.forEach {
            it.remove() // mark living entity for removal if present
        }
        parent.activeMobs[identifier].clear()
    }
}