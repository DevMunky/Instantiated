package dev.munky.instantiated.dungeon.mob

import dev.munky.instantiated.common.structs.Identifiable
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import org.bukkit.entity.LivingEntity

abstract class DungeonMob : Identifiable {

    open fun getEntity(room: RoomInstance) : LivingEntity? {
        return room.parent.activeMobs[room.identifier].entries.firstOrNull { it.value == this }?.key
    }

    abstract var isMarked : Boolean

    /**
     * Lets anyone add arbitrary persistent data to any mob.
     */
    abstract val custom : HashMap<String,String>

    /**
     * In-case you dont have kotlin or something idk
     */
    val javaCustom: java.util.HashMap<String,String> get() = custom

    /**
     * Return true if the death is valid.
     * Called before the actual death of the mob
     * @return whether the death should be cancelled, keeping this mob alive at its health before the killing blow.
     */
    abstract fun onDeath(room: RoomInstance, victim: LivingEntity, killer: LivingEntity) : Boolean
    abstract override fun toString(): String
    abstract fun clone() : DungeonMob
}
