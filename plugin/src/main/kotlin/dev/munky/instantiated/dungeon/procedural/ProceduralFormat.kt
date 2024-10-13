package dev.munky.instantiated.dungeon.procedural

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.exception.DungeonExceptions
import org.bukkit.Location
import org.joml.Vector3f

class ProceduralFormat(
    override val identifier : IdKey,
    private val startingRoomIdentifier : IdKey,
    override var spawnVector: Vector3f
) : Format {
    override val rooms : MutableMap<IdKey, ProceduralRoomFormat> = HashMap()
    override fun instance(location: Location, option: Format.InstanceOption): Instance {
        return ProceduralInstance(this,location,false)
    }
    override val instances: MutableSet<ProceduralInstance> = mutableSetOf()
    val startingRoom : ProceduralRoomFormat
        get() = rooms[startingRoomIdentifier] ?: throw DungeonExceptions.ComponentNotFound.consume(startingRoomIdentifier)
}