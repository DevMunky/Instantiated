package dev.munky.instantiated.dungeon

import dev.munky.instantiated.common.logging.NotYetInitializedException
import dev.munky.instantiated.common.util.asOptional
import dev.munky.instantiated.common.util.formatException
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.hasIntraData
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.easyGet
import dev.munky.instantiated.event.room.DungeonRoomPlayerEnterEvent
import dev.munky.instantiated.event.room.DungeonRoomPlayerLeaveEvent
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.plugin
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.KoinComponent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

class TaskManager: KoinComponent{
    private val tasks = TaskLoader::class.sealedSubclasses.mapNotNull { it.objectInstance }

    fun initialize(){
        tasks.forEach { loader ->
            try{
                loader.initialized.onFailure {
                    if (it !is NotYetInitializedException) return@onFailure
                    loader.init()
                    plugin.logger.debug("Task '${loader::class.simpleName}' initialized")
                }
            }catch (t: Throwable){
                t.log("An error occurred in Instantiated's task system")
            }
        }
    }

    @Suppress("unused")
    private sealed class TaskLoader : (ScheduledTask) -> Unit {
        enum class TaskType{
            NOW,
            DELAY,
            REPEAT
        }
        companion object{
            val MANAGER get() = easyGet<DungeonManager>()
        }
        var initialized: Result<Unit> = Result.failure(NotYetInitializedException())
        var instance: Optional<ScheduledTask> = Optional.empty()
        // safeConsumer because bukkit schedulers will eat exceptions
        private val safeConsumer: (ScheduledTask) -> Unit = {
            try {
                initialized = runCatching { this@TaskLoader.invoke(it) }
            } catch (e: Exception) {
                plugin.logger.severe("Error while running task '${this::class.simpleName}' :${e.formatException()}")
            }
        }
        abstract val periodMillis: Long
        abstract val type: TaskType
        abstract val sync: Boolean
        fun init (){
            try{
                if (sync) {
                    val sch = Bukkit.getGlobalRegionScheduler()
                    val periodTicks = periodMillis.fromMillisToTicks.toLong()
                    this.instance = when (type) {
                        TaskType.NOW -> sch.run(plugin, safeConsumer)
                        TaskType.DELAY -> sch.runDelayed(plugin, safeConsumer, periodTicks)
                        TaskType.REPEAT -> sch.runAtFixedRate(plugin, safeConsumer, periodTicks, periodTicks)
                    }.asOptional
                    return
                }
                val sch = Bukkit.getAsyncScheduler()
                this.instance = when (type) {
                    TaskType.NOW -> sch.runNow(plugin, safeConsumer)
                    TaskType.DELAY -> sch.runDelayed(plugin, safeConsumer, periodMillis, TimeUnit.MILLISECONDS)
                    TaskType.REPEAT -> sch.runAtFixedRate(
                        plugin,
                        safeConsumer,
                        periodMillis,
                        periodMillis,
                        TimeUnit.MILLISECONDS
                    )
                }.asOptional
            }catch (e: Exception){
                throw DungeonExceptions.Generic.consume("task ${this::class.simpleName}",e)
            }
        }
        data object EntityCheck : TaskLoader() { // i think this can be changed
            override val periodMillis = 40.fromTicksToMillis
            override val type: TaskType = TaskType.REPEAT
            override val sync: Boolean = true
            override fun invoke(t: ScheduledTask) {
                for (entity in MANAGER.dungeonWorld.entities){
                    checkInitTime(entity)
                    if (entity.hasIntraData(DungeonManager.NO_DESPAWN_ENTITY)){
                        entity.ticksLived = 6;
                    }
                }
            }
            private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("LL-dd hh:mm:ss a")
            private val ZONE_ID: ZoneId = ZoneId.of("America/New_York")
            private fun checkInitTime(entity: Entity): Boolean{
                val pdc = entity.persistentDataContainer
                if (!pdc.has(DungeonManager.INIT_TIME, PersistentDataType.LONG)) return false
                val entityInit = pdc.get(DungeonManager.INIT_TIME, PersistentDataType.LONG) ?: return false
                if (entityInit == plugin.initTime) return false
                val date = Instant.ofEpochMilli(entityInit).atZone(ZONE_ID).format(DATE_FORMATTER)
                plugin.logger.debug("Removed dungeon mob from a session from $date)")
                entity.remove()
                return true
            }
        }
        data object ManageEmptyDungeons : TaskLoader() {
            override val periodMillis: Long = TimeUnit.SECONDS.toMillis(20)
            override val type: TaskType = TaskType.REPEAT
            override val sync: Boolean = false
            override fun invoke(t: ScheduledTask) {
                // dont count online players because there could have been a player that left, which hasnt been removed yet
                val empties = MANAGER.instances.filter{ !it.cache.isCached && it.players.isEmpty() }
                for (dungeon in empties){
                    try {
                        plugin.logger.info("Removing an empty dungeon");
                        // dont continue holding because this dungeon was certainly never cached
                        dungeon.remove(Instance.RemovalReason.NO_PLAYERS_LEFT, false);
                    } catch (e: Exception){
                        e.log("Could not remove an empty '${dungeon.identifier}'")
                    }
                }
            }
        }
        data object CachePlayerLocationsForRoomEnterAndLeaveEvents : TaskLoader() {
            val locations = mutableMapOf<UUID, Location>()
            override val periodMillis: Long = 1.fromTicksToMillis
            override val type: TaskType = TaskType.REPEAT
            override val sync: Boolean = true
            override fun invoke(p0: ScheduledTask) {
                val i = locations.iterator()
                while (i.hasNext()) {
                    i.next()
                    i.remove()
                }
                Bukkit.getOnlinePlayers().forEach {
                    locations[it.uniqueId] = it.location
                }
            }
        }
        data object CallRoomEnterAndLeaveEvents : TaskLoader() {
            val rooms = mutableMapOf<UUID, RoomInstance?>()
            override val periodMillis: Long = 1.fromTicksToMillis
            override val type: TaskType = TaskType.REPEAT
            override val sync: Boolean = false
            override fun invoke(t: ScheduledTask) {
                for ((uuid, cachedLocation) in locations ?: return) {
                    val current: Instance = MANAGER.getCurrentDungeon(uuid) ?: continue
                    val online = Bukkit.getPlayer(uuid) ?: continue
                    val room = current.getRoomAt(cachedLocation)
                    val pastValue = rooms[uuid]
                    if (pastValue != room && room != null) {
                        DungeonRoomPlayerEnterEvent(room, online).callEvent()
                    } else if (pastValue != null && room == null) {
                        DungeonRoomPlayerLeaveEvent(pastValue, online).callEvent()
                    }
                    rooms[uuid] = room
                }
            }
            private val locations get() = runCatching {
                CachePlayerLocationsForRoomEnterAndLeaveEvents.locations.entries.map { it } // map for copying the list
            }.getOrNull()
        }
    }
}