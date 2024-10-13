package dev.munky.instantiated.dungeon.interfaces

import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.structs.Identifiable
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.setIntraData
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.data.loader.caption
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.mob.DungeonMob
import dev.munky.instantiated.event.ListenerFactory
import dev.munky.instantiated.event.room.mob.DungeonMobKillEvent
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import dev.munky.instantiated.util.asComponent
import dev.munky.instantiated.util.setGlowColorFor
import dev.munky.instantiated.util.stackMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.joml.Vector3ic
import org.koin.core.component.get
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.seconds

interface RoomInstance : Identifiable {
    val origin : Vector3ic
    val realVector : Location
    var box : Box
    var areMobsSpawned : Boolean
    val parent : Instance
    val format : RoomFormat

    /**
     * @return true if the dungeon mob passes all checks and the death is legitimate, false otherwise.
     */
    fun registerDungeonMobDeath(room: RoomInstance, dungeonMob: DungeonMob, victim: LivingEntity, killer: LivingEntity) : Boolean {
        try{
            parent.activeMobs[identifier].remove(victim) ?: throw DungeonExceptions.Generic.consume("Dungeon mob is not in the active mob list")
            val event = DungeonMobKillEvent(this, killer, victim, dungeonMob)
            event.callEvent()
            if (event.isCancelled) return false
            try {
                val cancelled = dungeonMob.onDeath(room,victim,killer)
                if (cancelled) return false
            } catch (e: Exception) {
                throw DungeonExceptions.Generic.consume("onDeath method for mob type '${dungeonMob::class.simpleName}'",e)
            }
            plugin.logger
                .debug("Registered dungeon mob death '${dungeonMob.identifier}' which is a '${victim::class.simpleName}'")
            if (
                (format.keyDropMode === RoomFormat.KeyDropMode.MARKED_ROOM_MOB_KILL && dungeonMob.isMarked)
                || (format.keyDropMode === RoomFormat.KeyDropMode.ROOM_MOBS_CLEAR && parent.activeMobs[identifier].isEmpty())
            ) {
                // drop a key if the corresponding modes predicate it fulfilled
                dropKey(victim.location)
            }
            return true
        }catch (e: Throwable){
            plugin.logger.severe("Could not remove dungeon mob '${dungeonMob.identifier}':${e.stackMessage()}")
        }
        return false
    }
    /**
     * Drops a key at the given locationInWorld, that is not really a real key and cannot be picked up.
     * @param location real locationInWorld, not relative
     * @return the spawned item
     */
    fun dropKey(location: Location) {
        val keyItem = ItemStack(format.keyMaterial)
        val meta = keyItem.itemMeta
        meta.displayName(keyItemName)
        keyItem.setItemMeta(meta)
        val keyDropTitle = generateKeyTitle("<gradient:red:blue:red>Key dropped!".asComponent)
        for (player in this.parent.onlinePlayers) { player.showTitle(keyDropTitle) }
        val keyEntity = location.world.spawn(location, Item::class.java) { itemEntity ->
            itemEntity.isCustomNameVisible = true
            itemEntity.customName(caption("instance.key.item_name"))
            itemEntity.setCanMobPickup(false)
            itemEntity.isUnlimitedLifetime = true
            itemEntity.setIntraData(DungeonManager.NO_DESPAWN_ENTITY, Unit)
            itemEntity.persistentDataContainer.set(DungeonManager.INIT_TIME, PersistentDataType.LONG, plugin.initTime)
            itemEntity.itemStack = keyItem
            if (plugin.get<TheConfig>().keysGlow.value) {
                setGlowColorFor(itemEntity, plugin.get<TheConfig>().keysGlowColor.value)
            }
        }
        Schedulers.SYNC.submit(15.seconds) {
            if (
                !keyEntity.isInWorld
                || keyEntity.isDead
            /* || !keyEntity.isTicking    does it really need to be ticking? */
            ) return@submit
            this.parent.doorKeys++
            keyEntity.remove()
            val keyPickupTitle = generateKeyTitle(caption("instance.key.title.picked_up"))
            this.parent.onlinePlayers.forEach { it.showTitle(keyPickupTitle) }
            plugin.logger.debug("Key automatically picked up after 15 seconds! Current keys = ${this.parent.doorKeys}")
        }
        ListenerFactory.registerEvent(PlayerAttemptPickupItemEvent::class.java) { e, l ->
            if (e.item.itemStack != keyItem || !e.item.persistentDataContainer.has(DungeonManager.KEY_ENTITY_ITEM)) return@registerEvent
            e.isCancelled = true
            e.flyAtPlayer = true
            e.item.remove()
            this.parent.doorKeys++
            val keyPickupTitle = generateKeyTitle(caption("instance.key.title.picked_up"))
            for (player in this.parent.onlinePlayers) {
                player.showTitle(keyPickupTitle)
            }
            plugin.logger.debug("Player '${e.player.name}' picked up a key! Current keys = ${this.parent.doorKeys}")
            HandlerList.unregisterAll(l)
        }
        plugin.logger.debug("Spawned key at $location")
    }
    fun generateKeyTitle(title: Component) : Title {
        return Title.title(
            title,
            caption("instance.key.title.current_keys", this.parent.doorKeys),
            Title.Times.times(
                Duration.of(500, ChronoUnit.MILLIS),
                Duration.of(2, ChronoUnit.SECONDS),
                Duration.of(500, ChronoUnit.MILLIS)
            )
        )
    }
}

private val keyItemName = Component.text("null")