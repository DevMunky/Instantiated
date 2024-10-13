package dev.munky.instantiated.dungeon.sstatic

import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomFormat
import org.bukkit.Material
import org.joml.Vector3ic

class StaticRoomFormat(
    override val identifier: IdKey,
    override val parent: StaticFormat,
    override var origin: Vector3ic,
    override var box: Box,
    override var keyDropMode: RoomFormat.KeyDropMode,
    override var keyMaterial: Material
) : RoomFormat {
    override fun instance(instance: Instance): StaticRoomInstance {
        if (instance !is StaticInstance) throw ClassCastException("Instanced Dungeon parameter is not static, yet this is a static room!")
        return StaticRoomInstance(instance,this)
    }
}