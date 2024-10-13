package dev.munky.instantiated.dungeon.mob

import com.destroystokyo.paper.ParticleBuilder
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.plugin
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity

class SimpleDungeonMob(
    override val identifier: IdKey,
    override var isMarked: Boolean,
    override val custom: HashMap<String, String>,
) : DungeonMob() {

    constructor(
        identifier: IdKey,
        marked: Boolean,
    ) : this (identifier, marked, HashMap())

    override fun onDeath(room: RoomInstance, victim: LivingEntity, killer: LivingEntity): Boolean {
        val particle = ParticleBuilder(Particle.TOTEM_OF_UNDYING).count(1).extra(0.1)
        val location = victim.location
        val radius = 0.5
        plugin.logger.debug("mob ondeath $this")
        return true
    }

    override fun toString(): String {
        return "simple.$identifier"
    }

    override fun clone(): SimpleDungeonMob {
        return SimpleDungeonMob(identifier, isMarked, custom)
    }
}