package dev.munky.instantiated.dungeon.component

import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import org.bukkit.entity.Player

open class TraitContext(
    val room: RoomInstance,
    var component: DungeonComponent?
)

class TraitContextWithPlayer(
    room: RoomInstance,
    component: DungeonComponent?,
    val player: Player,
): TraitContext(room, component)

val TraitContext.player: Player? get() = (this as? TraitContextWithPlayer)?.player