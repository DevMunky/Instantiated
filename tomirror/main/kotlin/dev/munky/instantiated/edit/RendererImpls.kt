package dev.munky.instantiated.edit

import com.destroystokyo.paper.ParticleBuilder
import dev.munky.instantiated.common.util.copy
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.setIntraData
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.exception.DungeonExceptions.Companion.Generic
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import dev.munky.instantiated.util.setGlowColorFor
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockType
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.get
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque


// TODO Optimize particle rendering (packet channel flushing is the culprit)
// TODO abstract render methods and use block displays that are glowing
class ParticleRenderer: AbstractRenderer() {

    private val manager get() = get<DungeonManager>()
    private val editModeHandler get() = get<EditModeHandler>()
    private val config get() = get<TheConfig>()
    // these are all particles that are ready to spawn, ie have a location
    private val particleBuffer = ConcurrentLinkedDeque<() -> Unit>()

    override fun asyncRender(){
        for (editor in editModeHandler.playersInEditMode) {
            val toRender = manager.getCurrentDungeon(editor.uniqueId) ?: continue
            renderInstance(toRender, editor, RenderOptions.SELECTED_ROOM, RenderOptions.UNSELECTED_ROOM)
        }
    }

    override fun syncRender(){
        // when i looked at the particle implementation in minecraft, it appeared that packets sent from off-main
        // force the channel to flush, where packets from main dont. Maybe this will help with lag?
        // (sending packets sync instead of async)

        // fixed -> The massive lag from particles that i saw while profiling actually WAS minecraft flushing the channel after every send, since it was off main.
        synchronized(particleBuffer) {
            val i = particleBuffer.iterator()
            while (i.hasNext()) {
                val particle = i.next()
                try{
                    particle()
                } catch(t: Throwable){
                    t.log("Spawning particle")
                } finally {
                    i.remove()
                }
            }
            particleBuffer.clear()
        }
    }

    private fun renderParticle(particle: ParticleBuilder, world: World, x: Double, y: Double, z: Double, editor: Player){
        particleBuffer.add {
            particle.location(world, x, y, z).receivers(editor).spawn()
        }
    }

    /**
     * Abstract particle spawning to another method.
     * I need to bundle particle packets because even with a low amount of particles
     * many many packets are sent and it can be optimized
     */
    override fun renderLine(world: World, from: Vector3f, to: Vector3f, data: RenderData, editor: Player, resMod: Float){
        if (Bukkit.isPrimaryThread()) throw Generic.consume("dont use particle renderer in main thread")
        data.particle ?: return
        val res = ((config.renderResolution.value * resMod) * from.distance(to)).toInt()
        val dX = (to.x - from.x).toDouble() / res
        val dY = (to.y - from.y).toDouble() / res
        val dZ = (to.z - from.z).toDouble() / res
        for (i in 0..res) {
            val x = from.x + i * dX
            val y = from.y + i * dY
            val z = from.z + i * dZ
            renderParticle(data.particle, world, x, y, z, editor)
        }
    }
}

val TWELVE_EDGES_OF_A_CUBE: Array<Pair<Int,Int>> = arrayOf( // all 12 edges of a cube
    0 to 1, 0 to 2, 0 to 4,
    1 to 3, 1 to 5,
    2 to 3, 2 to 6,
    3 to 7,
    4 to 5, 4 to 6,
    5 to 7,
    6 to 7
)

class BlockDisplayRenderer: AbstractRenderer() {

    private val editModeHandler = get<EditModeHandler>()
    private val manager get() = get<DungeonManager>()

    private val tracker = HashMap<String, CompletableFuture<BlockDisplay>>()
    private val accounting = HashSet<String>()

    override fun initialize0(){}
    override fun syncRender(){}
    override fun asyncRender(){
        if (!plugin.isEnabled) return
        for (editor in editModeHandler.playersInEditMode){
            val toRender = manager.getCurrentDungeon(editor.uniqueId) ?: continue
            renderInstance(toRender, editor, RenderOptions.SELECTED_ROOM, RenderOptions.UNSELECTED_ROOM)
        }
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

    override fun renderLine(world: World, from: Vector3f, to: Vector3f, data: RenderData, editor: Player, resMod: Float) {
        val key = dif(from, to, data.block ?: return, resMod)
        if (accounting.contains(key)) return
        try{
            val display = tracker[key] ?: createDisplay(world, from, to, data.block, editor, resMod)
            tracker[key] = display
            accounting.add(key)
        }catch (t: Throwable){
            t.log("Spawning block display")
        }
    }

    private fun createDisplay(world: World, from: Vector3f, to: Vector3f, data: BlockRenderData, editor: Player, resMod: Float): CompletableFuture<BlockDisplay> {
        val future = CompletableFuture<BlockDisplay>()
        try{
            val trans = generateTransform(from, to, resMod)
            Schedulers.SYNC.submit {
                val spawned = world.spawn(
                    Location(world, from.x.toDouble(), from.y.toDouble(), from.z.toDouble()),
                    BlockDisplay::class.java
                ) { display ->
                    try{
                        display.transformation = trans
                        display.billboard = Display.Billboard.FIXED
                        display.block = data.block.createBlockData()
                        setGlowColorFor(display, data.glowColor)
                        display.isPersistent = false
                        display.isVisibleByDefault = false
                        editor.showEntity(plugin, display)
                        display.persistentDataContainer.set(DungeonManager.INIT_TIME, PersistentDataType.LONG, plugin.initTime)
                        display.setIntraData(DungeonManager.NO_DESPAWN_ENTITY, Unit)
                    }catch (t: Throwable){ future.completeExceptionally(t) }
                }
                future.complete(spawned)
            }
        }catch (t: Throwable) { future.completeExceptionally(t) }
        return future
    }

    private fun generateTransform(from: Vector3f, to: Vector3f, resMod: Float): Transformation {
        // Compute the direction vector from 'from' to 'to'
        val direction = to.copy.sub(from).normalize()

        val width = 0.05f * resMod
        val height = 0.05f * resMod

        // Calculate the rotation needed to align the x-direction with the direction vector
        val rotation = Quaternionf()
        rotation.rotationTo(Vector3f(1f, 0f, 0f), direction)

        // The scale component (assuming uniform scaling here)
        val scale = Vector3f(from.distance(to), height, width)

        // Create the Bukkit Transformation
        return Transformation(
            Vector3f((height / 2), -(height / 2), -(width / 2)),
            rotation,
            scale,
            Quaternionf()
        )
    }

    private fun dif(p1: Vector3f, p2: Vector3f, data: BlockRenderData, resMod: Float): String =
        "[${p1.x},${p1.y},${p1.z}:${p2.x},${p2.y},${p2.z}]:$data:$resMod"

    data class BlockRenderData(
        val block: BlockType,
        val glowColor: NamedTextColor? = null
    ){
        override fun toString(): String = "${block.key.key}:$glowColor"
    }
}

