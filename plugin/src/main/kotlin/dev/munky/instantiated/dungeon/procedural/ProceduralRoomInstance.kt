package dev.munky.instantiated.dungeon.procedural

import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.util.toLocation
import dev.munky.instantiated.util.toVector3f
import org.bukkit.Location
import org.joml.Vector3f

class ProceduralRoomInstance(
    override val parent: ProceduralInstance,
    override val format: ProceduralRoomFormat
) : RoomInstance {
    override var box: Box = format.box
    override val origin = format.origin
    override val inWorldLocation: Location = parent.locationInWorld.toVector3f.add(Vector3f(origin)).toLocation(parent.locationInWorld.world)
    override val identifier: IdKey = format.identifier
}