package dev.munky.instantiated.dungeon

import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.common.util.times
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.getIntraData
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.hasIntraData
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.data.loader.FormatStorage
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.edit.EditModeHandler
import dev.munky.instantiated.event.DungeonTotalCacheEvent
import dev.munky.instantiated.event.ListenerFactory
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.EntitiesUnloadEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds


class EventManager: KoinComponent {
    private val eventHandlers = EventHandler::class.sealedSubclasses.mapNotNull { it.objectInstance }

    fun initialize(){
        eventHandlers.forEach { handler ->
            try {
                handler.init()
                plugin.logger.debug("Event Handler '${handler::class.simpleName}' registered")
            } catch (t: Throwable) {
                t.log("An error occurred in Instantiated's event handler system")
            }
        }
    }

    // dude this shit sucks and blows
    @Suppress("unused")
    private sealed class EventHandler<E : Event>(private val kClass : KClass<E>) {
        companion object{
            protected val MANAGER = plugin.get<DungeonManager>()
            protected val FORMATS = plugin.get<FormatStorage>()
        }
        private var listener: Listener? = null
        protected abstract fun handle(event: E)
        fun init() {
            if (listener != null) HandlerList.unregisterAll(listener!!)
            listener = ListenerFactory.registerEvent(kClass.java) { event ->
                handle(event)
            }
        }
        data object QuitHandler : EventHandler<PlayerQuitEvent>(PlayerQuitEvent::class) {
            override fun handle(event: PlayerQuitEvent) {
                val uuid = event.player.uniqueId
                val dungeon = event.player.currentDungeon?.identifier ?: return
                val name = event.player.name
                Schedulers.ASYNC.submit(5L.seconds){
                    val player = Bukkit.getPlayer(uuid)
                    if (player == null || !player.isOnline) {
                        FORMATS[dungeon]?.instances?.forEach {
                            it.removePlayer(uuid)
                        }
                        plugin.logger.debug("Player '$name' removed from instance due to timeout")
                    } else plugin.logger.debug("Player '$name' reconnected before being kicked out of instance")
                }
            }
        }
        data object PlayerJoin : EventHandler<PlayerJoinEvent>(PlayerJoinEvent::class) {
            override fun handle(event: PlayerJoinEvent) {
                val instance = event.player.currentDungeon
                if (instance == null && (event.player.world.name == MANAGER.dungeonWorld.name)) {
                    // TODO maybe make this location editable
                    event.player.teleport(Bukkit.getWorlds().first().spawnLocation)
                    plugin.logger.debug("Moved '${event.player.name}' out of instancing world (not in instance)")
                }else {
                    plugin.logger.debug("player is in instance $instance")
                }
            }
        }
        data object EntityDeathHandler : EventHandler<EntityDamageEvent>(EntityDamageEvent::class){
            @Suppress("UnstableApiUsage")
            override fun handle(event: EntityDamageEvent) {
                val victim = event.entity
                val pdc = victim.persistentDataContainer
                if (!pdc.has(DungeonManager.INIT_TIME)) return
                if (victim !is LivingEntity) return
                if (victim.health - event.finalDamage > 0) return // certain death

                val data = victim.getIntraData(DungeonManager.DUNGEON_MOB_ENTITY) ?: return

                val killer = (event.damageSource.causingEntity ?: return) as LivingEntity

                val ret = data.first.registerDungeonMobDeath(data.first, data.second, victim, killer)
                if (!ret) event.isCancelled = true
                else victim.getPassengers().forEach { it.remove() } // for name tags and such
            }
        }
        data object EntityUnloadHandler : EventHandler<EntitiesUnloadEvent>(EntitiesUnloadEvent::class) {
            override fun handle(event: EntitiesUnloadEvent) {
                // cant even cancel this event...
            }
        }
        data object DungeonTotalCacheHandler : EventHandler<DungeonTotalCacheEvent>(DungeonTotalCacheEvent::class) {
            override fun handle(event: DungeonTotalCacheEvent) {
                val cacheSize = plugin.get<TheConfig>().dungeonCacheSize.value
                if (cacheSize == 0) return
                plugin.logger.debug("Caching dungeons...")
                if (FORMATS.isEmpty()) {
                    plugin.logger.info("There are no dungeons to cache")
                    return
                }
                FORMATS.values.forEach { dungeon ->
                    dungeon.instances.forEach { it.remove(Instance.RemovalReason.FORMAT_CHANGE, false) }
                    val currentCachedCount = dungeon.cached.size
                    (cacheSize - currentCachedCount).times {
                        MANAGER.startInstance(
                            dungeon,
                            Format.InstanceOption.CACHE,
                            emptyList()
                        )
                    }
                }
                plugin.logger.info("Currently, $cacheSize of each dungeon are cached and ready for players to join")
            }
        }
        data object BlockPlaceHandler : EventHandler<BlockPlaceEvent>(BlockPlaceEvent::class) {
            override fun handle(event: BlockPlaceEvent) {
                val player = event.player
                MANAGER.getCurrentDungeon(player.uniqueId) ?: return
                if (!player.hasIntraData(EditModeHandler.StateKeys.EDIT_MODE)) event.isCancelled = true;
            }
        }
        data object PlayerTeleport : EventHandler<PlayerTeleportEvent>(PlayerTeleportEvent::class){
            override fun handle(event: PlayerTeleportEvent) {
                if (
                    event.from.world != MANAGER.dungeonWorld
                    || event.to.world == MANAGER.dungeonWorld
                ) return
                val uuid = event.player.uniqueId
                event.player.currentDungeon?.removePlayer(uuid)
            }
        }
    }
}