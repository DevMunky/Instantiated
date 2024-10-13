package dev.munky.instantiated.dungeon.mob

import com.destroystokyo.paper.ParticleBuilder
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.plugin
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity

class BossDungeonMob(
    override val identifier: IdKey,
    override val custom: MutableMap<String, String>,
) : DungeonMob() {

    override var isMarked: Boolean = true

    constructor(
        identifier: IdKey,
    ) : this (identifier, mutableMapOf())

    override fun onDeath(room: RoomInstance, victim: LivingEntity, killer: LivingEntity): Boolean {
        val particle = ParticleBuilder(Particle.FALLING_DRIPSTONE_LAVA).count(1).extra(0.1)
        val location = victim.location
        val radius = 0.5
        plugin.logger.debug("mob $this onDeath")
        return true
    }

    override fun toString(): String {
        return "boss.$identifier"
    }

    override fun clone(): BossDungeonMob {
        return BossDungeonMob(identifier, custom)
    }
}