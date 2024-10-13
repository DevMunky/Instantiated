package dev.munky.instantiated.edit

import com.destroystokyo.paper.ParticleBuilder
import dev.munky.instantiated.common.logging.NotYetInitializedException
import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.util.copy
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.getIntraData
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.setIntraData
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.data.loader.ComponentStorage
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.edit.BlockDisplayRenderer.BlockRenderData
import dev.munky.instantiated.event.InstantiatedStateEvent
import dev.munky.instantiated.event.ListenerFactory
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import io.papermc.paper.util.Tick
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.World
import org.bukkit.block.BlockType
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.CompletableFuture
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

abstract class AbstractRenderer: KoinComponent {
    private var controller: ThreadController? = null

    fun initialize() {
        check(controller == null) { "Thread Controller was not shutdown prior to re-initialization" }
        controller = createRenderController(::syncRender, ::asyncRender)
        plugin.logger.debug("Thread Controller initialized for ${this::class.simpleName}")

        initialize0()
    }

    fun shutdown(){
        controller?.shutdown()
        controller = null
        shutdown0()
    }

    protected open fun initialize0(){}
    protected open fun shutdown0(){}
    protected abstract fun asyncRender()
    protected abstract fun syncRender()

    private val text = get<TextRenderer>()

    fun renderText(world: World, location: Vector3f, component: Component, editor: Player, scale: Float = 1f) = text.renderText(world, location, component, editor, scale)

    fun renderInstance(instance: Instance, editor: Player, selectedData: RenderData, unselectedData: RenderData, instanceData: RenderData = RenderOptions.INSTANCE_BOUNDS){
        val selectedRoom = editor.getIntraData(EditModeHandler.StateKeys.EDIT_MODE)!!.selectedRoom
        val min = instance.rooms.map { it.value.box.minimum }.minByOrNull { it.y + it.x + it.z }!!
        val max = instance.rooms.map { it.value.box.maximum }.minByOrNull { it.y + it.x + it.z }!!
        renderBox(instance.locationInWorld.world, Box(min, max), instanceData, editor)
        instance.rooms.values
            .associateWith { it.identifier == selectedRoom }
            .forEach { renderRoom(it.key, editor, if (it.value) selectedData else unselectedData) }
    }

    private val componentStorage = get<ComponentStorage>()

    fun renderRoom(room: RoomInstance, editor: Player, data: RenderData){
        renderBox(room.realVector.world, room.box, data, editor)
        componentStorage[room.format]?.forEach {
            it.render(this, room, editor)
        }
        renderText(room.realVector.world, room.box.center, Component.text(room.identifier.toString()), editor, 3f)
    }

    fun renderEllipse(world: World, location: Vector3f, radiusV: Vector2f, data: RenderData, editor: Player, thetaStepMod: Float = 2f, resMod: Float = 1f){
        val scale = ((thetaStepMod * 10)).toInt()

        val radX = radiusV.x
        val radZ = radiusV.y

        val stepTheta = 2 * PI / scale

        var last: Vector3f? = null
        for (i in 0..scale) {
            val theta = i * stepTheta
            val x = radX * sin(PI / 2) * cos(theta)
            val z = radZ * sin(PI / 2) * sin(theta)
            val p = location.copy
                .add(
                    x.toFloat(),
                    0f,
                    z.toFloat()
                )
            if (last != null ) renderLine(world, last, p, data, editor, resMod)
            last = p
        }
    }

    fun renderBox(world: World, box: Box, data: RenderData, editor: Player, resMod: Float = 1f){
        val min = box.minimum
        val max = box.maximum
        val x1 = min.x
        val y1 = min.y
        val z1 = min.z
        val x2 = max.x
        val y2 = max.y
        val z2 = max.z
        val vertices = mutableListOf<Vector3f>()

        for (x in floatArrayOf(x1, x2)) {
            for (y in floatArrayOf(y1, y2)) {
                for (z in floatArrayOf(z1, z2)) {
                    vertices.add(Vector3f(x, y, z))
                }
            }
        }

        for (edge in TWELVE_EDGES_OF_A_CUBE) {
            val v1 = vertices[edge.first]
            val v2 = vertices[edge.second]
            renderLine(world, v1, v2, data, editor, resMod)
        }
    }

    abstract fun renderLine(world: World, from: Vector3f, to: Vector3f, data: RenderData, editor: Player, resMod: Float = 1f)

    data class RenderData(
        val particle: ParticleBuilder?,
        val block: BlockRenderData?
    )

    @Suppress("UnstableApiUsage")
    object RenderOptions{
        val SELECTED_ROOM = RenderData(
            ParticleBuilder(Particle.FLAME).extra(0.0).count(1).data(null),
            BlockRenderData(
                BlockType.WHITE_CONCRETE,
                NamedTextColor.AQUA
            )
        )
        val UNSELECTED_ROOM = RenderData(
            ParticleBuilder(Particle.DUST).extra(0.0).count(1).data(DustOptions(Color.fromRGB(10, 10, 255), 0.8f)),
            BlockRenderData(
                BlockType.LIGHT_BLUE_CONCRETE,
                null // NamedTextColor.GREEN,
            )
        )
        val INSTANCE_BOUNDS = RenderData(
            ParticleBuilder(Particle.DUST).extra(0.0).count(1).data(DustOptions(Color.fromRGB(10, 10, 255), 0.8f)),
            BlockRenderData(
                BlockType.BLACK_CONCRETE_POWDER,
                NamedTextColor.BLUE
            )
        )
    }
}

class ThreadController(
    val main: () -> Unit,
    val async: () -> Unit,
    private val refreshRate: Duration
){
    companion object{
        val cs = HashSet<ThreadController>()
        init{
            ListenerFactory.registerEvent(InstantiatedStateEvent::class.java) { event ->
                cs.forEach {
                    it.shutdown()
                }
            }
        }
    }
    init{
        cs.add(this)
    }
    private var asyncTask: Result<ScheduledTask> = Result.failure(NotYetInitializedException())
    private var mainTask: Result<ScheduledTask> = Result.failure(NotYetInitializedException())
    var asyncScheduler = Schedulers.ASYNC
        set(value) {
            if (asyncTask.isSuccess) throw IllegalStateException("Already initialized!")
            field = value
        }
    private var syncScheduler = Schedulers.SYNC

    fun initialize(){
        asyncTask.onSuccess { it.cancel() }
        asyncTask = runCatching {
            asyncScheduler.repeat(
                refreshRate
            ) { async() }
        }.onFailure { it.log("Error scheduling async task for thread controller") }
        mainTask.onSuccess { it.cancel() }
        mainTask = kotlin.runCatching {
            syncScheduler.repeat(
                refreshRate
            ) { main() }
        }.onFailure { it.log("Error scheduling main thread task for thread controller") }
    }

    fun shutdown(){
        plugin.logger.debug("Shutdown a thread controller")
        mainTask.getOrNull()?.cancel()
        asyncTask.getOrNull()?.cancel()
    }
}

private fun createRenderController(
    main: () -> Unit,
    async: () -> Unit
): ThreadController{
    val rate = plugin.get<TheConfig>().renderRefreshRate.value.toLong()

    val c = ThreadController(
        main,
        async,
        Tick.of(rate).toKotlinDuration()
    )
    c.asyncScheduler = Schedulers.RENDER
    c.initialize()
    return c
}

class TextRenderer: KoinComponent {
    private var controller: ThreadController? = null

    private val tracker = HashMap<String, CompletableFuture<TextDisplay>>()
    private val accounting = HashSet<String>()

    fun initialize() {
        check(controller == null) { "Thread Controller was not shutdown prior to re-initialization" }
        controller = createRenderController(::syncRender, ::asyncRender)
    }
    fun shutdown(){
        controller?.shutdown()
        controller = null
    }
    private fun syncRender(){}
    private fun asyncRender(){
        if (!plugin.isEnabled) return
        val i = tracker.iterator()
        while (i.hasNext()){
            val entry = i.next()
            val key = entry.key
            if (!accounting.contains(key)){
                val entity = entry.value.getNow(null)
                if (entity != null) { Schedulers.SYNC.submit { entity.remove() } }
                i.remove()
            }
        }
        accounting.clear()
    }

    fun renderText(world: World, location: Vector3f, component: Component, editor: Player, scale: Float = 1f){
        val key = dif(world, location)
        if (accounting.contains(key)) return
        try{
            val textDisplay = tracker[key] ?: createTextDisplay(
                world,
                location,
                component,
                scale,
                editor
            )
            tracker.putIfAbsent(key, textDisplay)
            accounting.add(key)
        }catch (t: Throwable){
            t.log("Spawning text display")
        }
    }

    private fun createTextDisplay(
        world: World, location: Vector3f, content: Component, scale: Float, editor: Player
    ): CompletableFuture<TextDisplay> {
        val future = CompletableFuture<TextDisplay>()
        Schedulers.SYNC.submit {
            val display = world.spawn(
                Location(world, location.x.toDouble(), location.y.toDouble(), location.z.toDouble()),
                TextDisplay::class.java
            ) { display ->
                display.text(content)
                display.backgroundColor = Color.fromRGB(0, 0, 0)
                display.isSeeThrough = true
                display.transformation = Transformation(
                    Vector3f(0f),
                    AxisAngle4f(),
                    Vector3f(scale),
                    AxisAngle4f()
                )
                display.billboard = Display.Billboard.CENTER
                display.isPersistent = false
                display.isVisibleByDefault = false
                editor.showEntity(plugin, display)
                display.persistentDataContainer.set(DungeonManager.INIT_TIME, PersistentDataType.LONG, plugin.initTime)
                display.setIntraData(DungeonManager.NO_DESPAWN_ENTITY, Unit)
            }
            future.complete(display)
        }
        return future
    }

    private fun dif(world: World, location: Vector3f): String = "${world.name}:$location"
}