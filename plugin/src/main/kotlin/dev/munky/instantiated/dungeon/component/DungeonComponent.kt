package dev.munky.instantiated.dungeon.component

import com.destroystokyo.paper.ParticleBuilder
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.structs.Identifiable
import dev.munky.instantiated.data.loader.ComponentStorage
import dev.munky.instantiated.dungeon.component.trait.*
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.edit.AbstractRenderer
import dev.munky.instantiated.edit.BlockDisplayRenderer
import dev.munky.instantiated.edit.QuestionElement
import dev.munky.instantiated.event.ComponentReplacementEvent
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import dev.munky.instantiated.theConfig
import dev.munky.instantiated.util.toVector3f
import net.kyori.adventure.text.Component
import org.bukkit.Particle
import org.bukkit.block.BlockType
import org.bukkit.entity.Player
import org.jetbrains.annotations.ApiStatus
import org.joml.Vector3f
import org.koin.core.component.get
import java.util.*
import kotlin.reflect.KClass

/**
 * A Dungeon Component is a versatile object. Dungeon Components (herein called Components)
 * are stored in the [ComponentStorage] class, which is stored in the KoinContext.
 *
 * The storage matches a [RoomFormat][dev.munky.instantiated.dungeon.interfaces.RoomFormat] to a collection of components.
 * This way, the components are *completely separate* from the instances of each RoomFormat, and therefore do not require every component to be
 * re-instantiated everytime a room is created. This approach *does* mean that Components (although ultimately the [Traits][Trait]) must have
 * an instance room supplied to them for context as to which instance is invoking a given component, to obviously avoid syncing between completely separate instances.
 *
 *
 * @sample DoorComponent
 */
abstract class DungeonComponent(
    override val identifier: IdKey,
    traits: Collection<Trait>
): Identifiable{
    private val _traits = HashSet(traits)

    @ApiStatus.Internal
    @JvmField
    internal val `@traits`: Collection<Trait> = _traits

    abstract val uuid: UUID

    val headedQuestion: QuestionElement.Header get() = QuestionElement.Header(question)

    abstract val question: QuestionElement

    inline fun <reified T: Trait> question(noinline f: (T) -> DungeonComponent): QuestionElement{
        val trait = getTrait<T>()
        if (trait !is EditableTrait<*>) throw IllegalStateException("Editable Trait does not extend Trait")
        val hold = EditingTraitHolder(trait) {
            val new = f(trait)
            replaceCompInStorage(this, new)
        }
        val question = trait.question(hold)
        return question
    }

    constructor(
        id: String,
        traits: Collection<Trait>
    ): this(IdType.COMPONENT with id, traits)

    init{
        // probably dont need this anymore as traits are stored in LinkedHashSet
        val seen = mutableSetOf<KClass<*>>()
        _traits.forEach {
            if (seen.contains(it::class)) throw IllegalStateException("A component cannot have multiple traits of the same type.")
            seen.add(it::class)
        }
    }

    inline fun <reified T: Trait> hasTrait(): Boolean = hasTraitByClass(T::class)

    inline fun <reified T: Trait> getTrait(): T {
        return getTraitOrNull()
            ?: throw DungeonExceptions.ComponentNotFound.consume(
                IdType.TRAIT with (T::class.simpleName ?: "unknown")
            )
    }

    inline fun <reified T: Trait> getTraitOrNull(): T? = getTraitOrNullByClass(T::class)

    @Suppress("UNCHECKED_CAST") // it is actually checked
    fun <T: Trait> getTraitOrNullByClass(clazz: KClass<T>): T? = _traits.firstOrNull { clazz.isInstance(it) } as? T

    fun hasTraitByClass(clazz: KClass<out Trait>): Boolean = _traits.any { clazz.isInstance(it) }

    operator fun <T: TraitContext> invoke(ctx: T){
        ctx.component = this
        if (!Schedulers.COMPONENT_PROCESSING.onThread()) {
            plugin.logger.debug("Invocation thread moved")
            Schedulers.COMPONENT_PROCESSING.submit {
                invoke0(ctx)
            }
        } else invoke0(ctx)
    }

    protected open fun <T: TraitContext> invoke0(ctx: T) {
        if (theConfig.componentLogging.value){
            plugin.logger.debug("Component invoked (${this.uuid}) on thread '${Thread.currentThread().name}'")
        }
        for (trait in _traits) {
            if (trait !is FunctionalTrait) continue
            trait(ctx)
        }
    }

    @Suppress("UnstableApiUsage")
    protected val componentData = AbstractRenderer.RenderData(
        ParticleBuilder(Particle.OMINOUS_SPAWNING).extra(0.0).count(1).data(null),
        BlockDisplayRenderer.BlockRenderData(
            BlockType.RED_CONCRETE,
            null // NamedTextColor.RED
        )
    )

    fun render(renderer: AbstractRenderer, room: RoomInstance, editor: Player) {
        if (!hasTrait<LocatableTrait<*>>()) return
        val location = Vector3f(getTrait<LocatableTrait<*>>().vector).add(room.inWorldLocation.toVector3f)
        val oneUp = Vector3f(location.x, location.y + 1f, location.z)
        val world = room.inWorldLocation.world
        renderer.renderText(
            world,
            oneUp,
            Component.text(identifier.toString()),
            editor
        )
        renderer.renderLine(
            world,
            location,
            oneUp,
            componentData,
            editor,
            2f
        )
        render0(renderer, room, editor)
    }

    open fun render0(renderer: AbstractRenderer, room: RoomInstance, editor: Player){}
}

fun replaceCompInStorage(old: DungeonComponent, new: DungeonComponent){
    val storage = plugin.get<ComponentStorage>()
    val room = storage.getRoomByComponent(old)
    val comps = storage[room]?.toMutableList() ?: return
    if (!comps.remove(old)) plugin.logger.debug("comp storage for room ${room.identifier.key} expected $old to be present, but it was not")
    comps.add(new)
    storage.register(room to comps)
    ComponentReplacementEvent().callEvent()
    plugin.logger.debug("Replaced component $old with $new")
}

interface NeedsInitialized{
    fun <T: TraitContext> initialize(ctx: T)
}

interface NeedsShutdown{
    fun <T: TraitContext> shutdown(ctx: T)
}

/**
 * A class that other plugins can extend to create custom components using traits (or realistically anything)
 */
class CustomComponent(
    traits: Collection<Trait>,
    override val uuid: UUID
): DungeonComponent("custom", traits){
    override val question: QuestionElement = QuestionElement.Label("Cannot edit a custom component in-game")
}