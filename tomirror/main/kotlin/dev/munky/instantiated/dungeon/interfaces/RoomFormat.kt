package dev.munky.instantiated.dungeon.interfaces

import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.Identifiable
import org.bukkit.Material
import org.joml.Vector3ic

interface RoomFormat : Identifiable {
    val parent: Format
    var box: Box
    var origin: Vector3ic
    override val identifier: IdKey
    var keyDropMode: KeyDropMode
    var keyMaterial: Material
    fun instance(instance: Instance) : RoomInstance
    enum class KeyDropMode {
        MARKED_ROOM_MOB_KILL,
        ROOM_MOBS_CLEAR
    }
}