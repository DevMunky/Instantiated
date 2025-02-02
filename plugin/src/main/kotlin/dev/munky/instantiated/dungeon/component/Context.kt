package dev.munky.instantiated.dungeon.component

import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import org.bukkit.entity.Player

open class TraitContext(
    val room: RoomInstance,
    var component: DungeonComponent?
) {
    var isAlive = true
}

/**
 * When resolving the context within a trait, make the component null.
 * It will be changed to an actual component when a component gets the context back.
 */
class TraitContextWithPlayer(
    room: RoomInstance,
    component: DungeonComponent?,
    val player: Player,
): TraitContext(room, component)

val TraitContext.player: Player? get() = (this as? TraitContextWithPlayer)?.player