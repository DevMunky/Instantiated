package dev.munky.instantiated.dungeon.component.trait

import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.common.util.times
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.getIntraData
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.setIntraData
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.data.loader.MobStorage
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.component.TraitContext
import dev.munky.instantiated.dungeon.component.TraitContextWithPlayer
import dev.munky.instantiated.dungeon.currentDungeon
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.dungeon.mob.DungeonMob
import dev.munky.instantiated.edit.EditModeHandler
import dev.munky.instantiated.edit.PromptFactory
import dev.munky.instantiated.edit.QuestionElement
import dev.munky.instantiated.event.room.mob.DungeonMobSpawnEvent
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import dev.munky.instantiated.theConfig
import dev.munky.instantiated.util.asComponent
import dev.munky.instantiated.util.toVector3f
import dev.munky.instantiated.util.toVector3i
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.util.Tick
import it.unimi.dsi.fastutil.ints.Int2BooleanArrayMap
import org.bukkit.Bukkit
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

abstract class FunctionalTrait(id: String): Trait(id){
    open val priority: Int = 0
    operator fun <T: TraitContext> invoke(ctx: T){
        if (theConfig.componentLogging.value){
            plugin.logger.debug("Trait invoked (${this.identifier}) on thread '${Thread.currentThread().name}'")
        }
        if (!Schedulers.COMPONENT_PROCESSING.onThread())
            throw IllegalStateException("Trait invocation did not occur on the dedicated component processing thread")
        invoke0(ctx)
    }
    protected abstract fun <T: TraitContext> invoke0(ctx: T)
}

class SpawnerTrait(
    val mob: DungeonMob,
    val quantity: IntRange = 1..1,
    val radius: Float = 0f
): FunctionalTrait("spawner"), EditableTrait<SpawnerTrait> {
    override fun question(res: EditingTraitHolder<SpawnerTrait>): QuestionElement = QuestionElement.ForTrait(
        this,
        QuestionElement.Clickable("Mob"){
            val id = PromptFactory.promptString("Mob Id", it)
            val mobStorage = plugin.get<MobStorage>()
            val mob = mobStorage[IdType.MOB with id]
            if (mob == null){
                it.sendMessage("Mob `$id` does not exist".asComponent)
                return@Clickable
            }
            res.trait = SpawnerTrait(mob, res.trait.quantity, res.trait.radius)
        },
        QuestionElement.Clickable("Quantity"){
            val q = PromptFactory.promptIntegers(2, it)
            res.trait = SpawnerTrait(res.trait.mob, q[0]..q[1], res.trait.radius)
        },
        QuestionElement.Clickable("Radius"){
            val rad = PromptFactory.promptFloats(1, it)
            res.trait = SpawnerTrait(res.trait.mob, res.trait.quantity, rad[0])
        }
    )

    private data class MobData(
        val room: RoomInstance,
        val mob: DungeonMob,
        val location: Location
    )

    override fun <T : TraitContext> invoke0(ctx: T) {
        val component = ctx.component ?: return
        val room = ctx.room
        val locTrait = component.getTraitOrNull<LocatableTrait<*>>() ?: return
        val rLoc = room.realVector.toVector3f
        // fixed -> Most mob calculations are done off-main, only spawning unhandled mobs must be done sync
        val count = quantity.random()
        val mobData = arrayOfNulls<MobData>(count)
        count.times { i ->
            val mobLocation = Location(
                room.realVector.world,
                (locTrait.vector.x + rLoc.x).toDouble(),
                (locTrait.vector.y + rLoc.y).toDouble(),
                (locTrait.vector.z + rLoc.z).toDouble(),
                // TODO randomize yaw and pitch
                locTrait.yaw,
                locTrait.pitch
            )
            // fixed -> radius is now an actual radius rather than a square
            if (radius in 0.1f..50f){
                val angle = Random.nextDouble(0.0, 2 * PI)
                val r = Random.nextDouble(
                    0.0,
                    radius.toDouble()
                ) // get a random magnitude to spawn within the defined circle rather than only on the perimeter
                mobLocation.add(r * cos(angle), 0.0, r * sin(angle))
            }
            // fixed -> the event is always called on the server thread below
            mobData[i] = MobData(room, mob, mobLocation)
        }
        for (data in mobData){
            data ?: throw IllegalStateException("The entire array should be populated")
            val future = CompletableFuture<LivingEntity>()
            future.thenAcceptAsync({
                val newDungeonMob = data.mob.clone()
                handleSpawned(it, room, newDungeonMob)
                plugin.logger.debug("Properly spawned '${newDungeonMob.identifier.key}' as ${it::class.simpleName}")
            }, Schedulers.COMPONENT_PROCESSING)
            Schedulers.SYNC.submit {
                val event = DungeonMobSpawnEvent(data.room, data.mob, data.location)
                event.callEvent()
                future.complete(event.livingEntity ?: handleUnhandledMob(event, room))
            }
        }
    }

    private fun handleSpawned(l: LivingEntity, room: RoomInstance, d: DungeonMob){
        l.setIntraData(DungeonManager.NO_DESPAWN_ENTITY, Unit)
        l.isPersistent = false
        l.persistentDataContainer.set(
            DungeonManager.INIT_TIME,
            PersistentDataType.LONG,
            plugin.initTime
        )
        room.parent.activeMobs.put(room.identifier, l to d)
    }

    private fun handleUnhandledMob(event: DungeonMobSpawnEvent, room: RoomInstance): Zombie =
        event.spawnLocation.world.spawn(event.spawnLocation, Zombie::class.java) { zomb ->
            zomb.customName("<red>I AM UN-CONFIGURED. Contact your server owner".asComponent)
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
): FunctionalTrait("set-blocks"), EditableTrait<SetBlocksTrait> {
    private val openData by lazy { openType.createBlockData() }
    private val closeData by lazy { closeType.createBlockData() }
    val isOpen: (RoomInstance) -> Boolean = { _isOpen[it.hashCode()] }
    private val _isOpen = Int2BooleanArrayMap()

    override fun question(res: EditingTraitHolder<SetBlocksTrait>): QuestionElement = QuestionElement.ForTrait(
        this,
        QuestionElement.Clickable("Open block"){
            val block = PromptFactory.promptRegistry(RegistryKey.BLOCK, it)
            res.trait = SetBlocksTrait(block, res.trait.closeType, res.trait.changeFunction, res.trait.blocks)
        },
        QuestionElement.Clickable("Close block"){
            val block = PromptFactory.promptRegistry(RegistryKey.BLOCK, it)
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
                plugin.get<TheConfig>().debug.push(true)
                t.log("So many assumptions made here")
                plugin.get<TheConfig>().debug.pop()
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

    override fun <T : TraitContext> invoke0(ctx: T) {
        val room = ctx.room
        val open = isOpen(room)
        invoke(ctx, if (open) closeData else openData)
        _isOpen[room.hashCode()] = !open
    }

    fun <T : TraitContext> invoke(ctx: T, block: BlockData){
        val room = ctx.room
        val shift = room.realVector.toVector3i
        val shifted = blocks.map { Vector3i(it).add(shift) }
        changeFunction.setBlocks(room.realVector.world, shifted, block)
    }

    private interface IChangeFunction{
        fun setBlocks(world: World, vList: List<Vector3i>, data: BlockData)
        fun execute(interval: Long, i: Iterator<Int>, map: Map<Int, List<Vector3i>>, data: BlockData, world: World){
            // if the plugin is disabled fuck it, the world wont be saved anyhow
            if (!plugin.state.isDisabled) Schedulers.COMPONENT_PROCESSING.repeat(Tick.of(interval).toKotlinDuration()){
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
                    map.computeIfAbsent(v.y) { ArrayList() }.add(v)
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
                    map.computeIfAbsent(v.y) { ArrayList() }.add(v)
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
                    map.computeIfAbsent(if (xAxis) v.x else v.z) { ArrayList() }.add(v)
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
                    map.computeIfAbsent(if (xAxis) v.x else v.z) { ArrayList() }.add(v)
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

class SendCommandTrait(
    private val command: String,
    private val playerDriven: Boolean = true
): FunctionalTrait("send-command"), EditableTrait<SendCommandTrait>{
    override fun <T : TraitContext> invoke0(ctx: T) {
        val player = (ctx as? TraitContextWithPlayer)?.player
        if (player != null && playerDriven){
            player.performCommand(command)
        }else if (!playerDriven) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        }
    }

    override fun question(res: EditingTraitHolder<SendCommandTrait>): QuestionElement = QuestionElement.ForTrait(
        this,
        QuestionElement.Clickable("Command", command) {
            val cmd = PromptFactory.promptString("command", it)
            res.trait = SendCommandTrait(cmd, res.trait.playerDriven)
        },
        QuestionElement.Clickable("Player Needed", playerDriven){
            res.trait = SendCommandTrait(res.trait.command, !res.trait.playerDriven)
        }
    )
}