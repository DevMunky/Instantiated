package dev.munky.instantiated.dungeon.component.trait

import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.common.util.times
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.getIntraData
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.setIntraData
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.data.loader.MobStorage
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.component.DungeonComponent
import dev.munky.instantiated.dungeon.currentDungeon
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.dungeon.mob.DungeonMob
import dev.munky.instantiated.edit.EditModeHandler
import dev.munky.instantiated.edit.PromptFactory
import dev.munky.instantiated.edit.QuestionElement
import dev.munky.instantiated.event.room.mob.DungeonMobSpawnEvent
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import dev.munky.instantiated.util.fromMini
import dev.munky.instantiated.util.toVector3f
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.util.Tick
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockType
import org.bukkit.block.data.BlockData
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Zombie
import org.bukkit.persistence.PersistentDataType
import org.joml.Vector3i
import org.koin.core.component.get
import java.util.concurrent.CompletableFuture
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.toKotlinDuration

abstract class FunctionalTrait<T: Trait<T>>(id: String): Trait<T>(id){
    fun invoke(room: RoomInstance, component: DungeonComponent?){
        if (Thread.currentThread() != Schedulers.COMPONENT_PROCESSING.thread)
            throw IllegalStateException("Trait invocation did not occur on the dedicated component processing thread")
        invoke0(room, component)
    }
    protected abstract fun invoke0(room: RoomInstance, component: DungeonComponent?)
}

class SpawnerTrait(
    val mob: DungeonMob,
    val quantity: IntRange = 1..1,
    val radius: Float = 0f
): FunctionalTrait<SpawnerTrait>("spawner") {
    override fun question(res: EditingTraitHolder<SpawnerTrait>): QuestionElement = QuestionElement.ForTrait(
        this,
        QuestionElement.Clickable("Mob"){
            val id = PromptFactory.promptString("Mob Id", it)
            val mobStorage = plugin.get<MobStorage>()
            val mob = mobStorage[IdType.MOB with id]
            if (mob == null){
                it.sendMessage("Mob `$id` does not exist".fromMini)
                return@Clickable
            }
            res.trait = SpawnerTrait(mob, res.trait.quantity, res.trait.radius)
        },
        QuestionElement.Clickable("Quantity"){
            val q = PromptFactory.promptIntegers(2, it) ?: return@Clickable
            res.trait = SpawnerTrait(res.trait.mob, q[0]..q[1], res.trait.radius)
        },
        QuestionElement.Clickable("Radius"){
            val rad = PromptFactory.promptFloats(1, it) ?: return@Clickable
            res.trait = SpawnerTrait(res.trait.mob, res.trait.quantity, rad[0])
        }
    )

    override fun invoke0(room: RoomInstance, component: DungeonComponent?){
        component ?: return
        val locTrait = component.getTraitOrNull<LocatableTrait>() ?: return
        val rLoc = room.realVector.toVector3f
        // fixed -> Most mob calculations are done off-main, only spawning unhandled mobs must be done sync
        val count = quantity.random()
        val called = arrayOfNulls<DungeonMobSpawnEvent>(count);
        count.times { i ->
            val mobLocation = Location(
                room.realVector.world,
                (locTrait.vector.x + rLoc.x).toDouble(),
                (locTrait.vector.y + rLoc.y).toDouble(),
                (locTrait.vector.z + rLoc.z).toDouble(),
                locTrait.yaw,
                locTrait.pitch
            )
            // fixed -> radius is now an actual radius rather than a square
            if (radius in 0.1f..50f){
                val angle = Random.nextDouble(0.0, 2 * PI)
                val r = Random.nextDouble(
                    0.0,
                    radius.toDouble()
                ) // get a random magnitude to spawn within the defined circle rather than only on
                mobLocation.add(r * cos(angle), 0.0, r * sin(angle))
            }
            // its possible people won't want to have this event called asynchronous
            val event = DungeonMobSpawnEvent(room, mob, mobLocation)
            event.callEvent()
            if (!event.isCancelled) called[i] = event
        }
        called.forEach { event ->
            event ?: return@forEach // handle cancelled spawn events
            val eventEntity = event.livingEntity
            val newDungeonMob = event.dungeonMob.clone()
                if (eventEntity != null) {
                    eventEntity.isPersistent = false
                    handleSpawned(eventEntity, room, newDungeonMob)
                } else {
                    val future = CompletableFuture<LivingEntity>()
                    // possibly handle mobs better in this spawn section, although unhandled mobs are an edge case
                    Schedulers.SYNC.submit {
                        future.complete(handleUnhandledMob(event, room))
                    }
                    future.thenAcceptAsync({
                        handleSpawned(it, room, newDungeonMob)
                    }, Schedulers.COMPONENT_PROCESSING)
                }
        }
    }

    private fun handleSpawned(l: LivingEntity, room: RoomInstance, d: DungeonMob){
        l.setIntraData(DungeonManager.NO_DESPAWN_ENTITY, Unit)
        l.persistentDataContainer.set(
            DungeonManager.INIT_TIME,
            PersistentDataType.LONG,
            plugin.initTime
        )
        room.parent.activeMobs.put(room.identifier, l to d)
    }

    private fun handleUnhandledMob(event: DungeonMobSpawnEvent, room: RoomInstance): Zombie =
        event.spawnLocation.world.spawn(event.spawnLocation, Zombie::class.java) { zomb ->
            zomb.customName("<red>I AM UN-CONFIGURED. Contact your server owner".fromMini)
            zomb.isCustomNameVisible = true
            zomb.setAI(false)
            zomb.isSilent = true
            plugin.logger.warning("Spawned an un-configured mob '${event.dungeonMob.identifier}'")
            plugin.logger.warning("in '${room.identifier}'")
            plugin.logger.warning("in '${room.parent.identifier}'")
            plugin.logger.warning("at ${event.spawnLocation.x}, ${event.spawnLocation.y}, ${event.spawnLocation.z}")
        }
}

@Suppress("UnstableApiUsage") // i want to use new material api (Typed things)
class SetBlocksTrait(
    val openType: BlockType,
    val closeType: BlockType,
    val changeFunction: ChangeFunction,
    val blocks: MutableCollection<Vector3i>
): FunctionalTrait<SetBlocksTrait>("set-blocks") {
    private val openData by lazy { openType.createBlockData() }
    private val closeData by lazy { closeType.createBlockData() }
    val isOpen get() = _isOpen
    private var _isOpen = false

    override fun question(res: EditingTraitHolder<SetBlocksTrait>): QuestionElement = QuestionElement.ForTrait(
        this,
        QuestionElement.Clickable("Open block"){
            val block = PromptFactory.promptRegistry(RegistryKey.BLOCK, it) ?: return@Clickable
            res.trait = SetBlocksTrait(block, res.trait.closeType, res.trait.changeFunction, res.trait.blocks)
        },
        QuestionElement.Clickable("Close block"){
            val block = PromptFactory.promptRegistry(RegistryKey.BLOCK, it) ?: return@Clickable
            res.trait = SetBlocksTrait(res.trait.openType, block, res.trait.changeFunction, res.trait.blocks)
        },
        QuestionElement.Clickable("Set blocks"){
            if (it !is Player) throw IllegalStateException("how did non player click dis")
            try{
                val id = it.getIntraData(EditModeHandler.StateKeys.EDIT_MODE)!!.selectedRoom
                val dungeon = it.currentDungeon
                val room = dungeon!!.rooms[id]!!
                it.setIntraData(EditModeHandler.StateKeys.SETTING_BLOCKS, room to this)
            }catch (t: Throwable){
                val b = plugin.get<TheConfig>().DEBUG.set(true)
                t.log("So many assumptions made here")
                plugin.get<TheConfig>().DEBUG.set(b)
            }
        },
        QuestionElement.ListOf(
            "Set change-function",
            ChangeFunction.entries.map { func ->
                QuestionElement.Clickable(func.name){
                    res.trait = SetBlocksTrait(res.trait.openType, res.trait.closeType, func, res.trait.blocks)
                }
            }
        )
    )

    override fun invoke0(room: RoomInstance, component: DungeonComponent?) {
        component ?: return
        val world = room.realVector.world
        val data = if (_isOpen) closeData else openData
        val location = room.realVector.toVector3f
        val shift = blocks.map { Vector3i(it).add(location.x.toInt(), location.y.toInt(), location.z.toInt()) }
        changeFunction.setBlocks(world, shift, data) // this sets blocks and whatnot
        _isOpen = !_isOpen
    }

    private interface IChangeFunction{
        fun setBlocks(world: World, vList: List<Vector3i>, data: BlockData)
        fun execute(interval: Long, i: Iterator<Int>, map: Map<Int, List<Vector3i>>, data: BlockData, world: World){
            Schedulers.COMPONENT_PROCESSING.repeat(Tick.of(interval).toKotlinDuration()){
                if (!i.hasNext()) {
                    it.cancel()
                    return@repeat
                }
                val list = map[i.next()]!!
                Schedulers.SYNC.submit {
                    for (v in list) {
                        world.setBlockData(v.x, v.y, v.z, data)
                    }
                }
            }
        }
    }

    enum class ChangeFunction: IChangeFunction {
        TOP_DOWN {
            override fun setBlocks(world: World, vList: List<Vector3i>, data: BlockData) {
                val map = HashMap<Int, ArrayList<Vector3i>>()
                for (v in vList){
                    map.computeIfAbsent(v.y) { ArrayList<Vector3i>() }.add(v)
                }
                val sortedKeys = map.keys.sorted().reversed()
                val i = sortedKeys.iterator()
                execute(1L, i, map, data, world)
            }
        },
        BOTTOM_UP {
            override fun setBlocks(world: World, vList: List<Vector3i>, data: BlockData) {
                val map = HashMap<Int, ArrayList<Vector3i>>()
                for (v in vList){
                    map.computeIfAbsent(v.y) { ArrayList<Vector3i>() }.add(v)
                }
                val sortedKeys = map.keys.sorted()
                val i = sortedKeys.iterator()
                execute(1L, i, map, data, world)
            }
        },
        NEGATIVE_2_POSITIVE {
            override fun setBlocks(world: World, vList: List<Vector3i>, data: BlockData) {
                val xAxis = isAlongXAxis(vList)
                val map = HashMap<Int, ArrayList<Vector3i>>()
                for (v in vList){
                    map.computeIfAbsent(if (xAxis) v.x else v.z) { ArrayList<Vector3i>() }.add(v)
                }
                val sortedKeys = map.keys.sorted()
                val i = sortedKeys.iterator()
                execute(1L, i, map, data, world)
            }
        },
        POSITIVE_2_NEGATIVE {
            override fun setBlocks(world: World, vList: List<Vector3i>, data: BlockData) {
                val xAxis = isAlongXAxis(vList)
                val map = HashMap<Int, ArrayList<Vector3i>>()
                for (v in vList){
                    map.computeIfAbsent(if (xAxis) v.x else v.z) { ArrayList<Vector3i>() }.add(v)
                }
                val sortedKeys = map.keys.sorted().reversed()
                val i = sortedKeys.iterator()
                execute(1L, i, map, data, world)
            }
        };
    }
}

private fun isAlongXAxis(vList: List<Vector3i>): Boolean{
    val min = vList.minBy { it.x - it.y - it.z }
    val max = vList.maxBy { it.x + it.y + it.z }
    return Vector3i(max).sub(min).let {
        it.x.absoluteValue > it.z.absoluteValue
    }
}