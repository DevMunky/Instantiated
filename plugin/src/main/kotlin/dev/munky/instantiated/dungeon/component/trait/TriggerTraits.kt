package dev.munky.instantiated.dungeon.component.trait

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.data.loader.ComponentStorage
import dev.munky.instantiated.dungeon.component.DungeonComponent
import dev.munky.instantiated.dungeon.currentDungeon
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.edit.QuestionElement
import dev.munky.instantiated.event.DungeonCacheEvent
import dev.munky.instantiated.event.InstantiatedStateEvent
import dev.munky.instantiated.event.ListenerFactory
import dev.munky.instantiated.event.room.DungeonRoomEvent
import dev.munky.instantiated.event.room.DungeonRoomPlayerEnterEvent
import dev.munky.instantiated.event.room.DungeonRoomPlayerLeaveEvent
import dev.munky.instantiated.event.room.mob.DungeonMobKillEvent
import dev.munky.instantiated.plugin
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.koin.core.component.get
import java.util.*
import kotlin.reflect.KClass

abstract class TriggerTrait<T: Trait<T>>(
    id: String
): FunctionalTrait<T>(id){
    protected abstract val targets: List<UUID>
    final override fun invoke0(room: RoomInstance, component: DungeonComponent?){
        for (target in targets) {
            val resolved = plugin.get<ComponentStorage>().getByUUID(target)
            resolved?.invoke(room) ?: plugin.logger.debug("TriggerTrait's target ($target) could not resolve")
        }
    }
}

object EventTraitListenerHelper{

    private val holders = ArrayList<ListenerHolder<*>>()

    init{
        ListenerFactory.registerEvent(InstantiatedStateEvent::class.java) { event ->
            val i = holders.iterator()
            while (i.hasNext()) {
                val h = i.next()
                h.shutdown()
                i.remove()
            }
        }
    }

    fun <T: Event> registerEvent(clazz: KClass<T>, f: (T) -> Unit){
        val holder = holders.firstOrNull { it.clazz == clazz } ?: run {
            val ret = ListenerHolder(clazz)
            holders.add(ret)
            ret
        }
        holder.reg(f)
    }

    private class ListenerHolder<T: Event>(
        val clazz: KClass<T>
    ){
        private val things = HashSet<(T) -> Unit>()
        val listener = ListenerFactory.registerEvent(clazz.java) { event ->
            for (con in things){ con(event) }
        }
        fun reg(f: Any){
            // have to cast because of type erasure
            // is pretty much enforced because of the class checks and
            // type parameter on the register function
            @Suppress("UNCHECKED_CAST")
            val fu = f as? (T) -> Unit ?: throw IllegalArgumentException("listener reg function is not (T) -> Unit it is ${f::class.simpleName}")
            things.add(fu)
        }
        fun shutdown(){
            HandlerList.unregisterAll(listener)
        }
    }
}

// will have to make a class for custom events specifically because of the question and things so
abstract class EventTriggerTrait<T: Trait<T>,E: Event>(
    val event: KClass<E>,
    val uses: Int,
    public override val targets: List<UUID>
): TriggerTrait<T>("event-trigger"){

    private val used: WeakHashMap<Instance, Int> = WeakHashMap()

    init{
        EventTraitListenerHelper.registerEvent(event) { handle(it) }
    }

    private fun handle(event: E){
        val room = resolveRoom(event) ?: return
        if (!handleUses(room)) return
        val components = plugin.get<ComponentStorage>()[room.format] ?: return
        if (components.none { it.hasTraitByClass(this::class) }) return
        this.invoke0(room, null)
    }

    private fun handleUses(room: RoomInstance): Boolean {
        if (uses < 0) return true
        val instanceUsed = used[room.parent] ?: 0
        if (instanceUsed >= uses) return false
        used[room.parent] = instanceUsed + 1
        return true
    }

    /**
     * Ive been kind of using this as a condition as well, which works pretty nicely
     */
    open fun resolveRoom(event: E): RoomInstance? = when (event) {
        is PlayerEvent -> {
            val instance = event.player.currentDungeon
            instance?.getRoomAt(event.player.location)
        }
        is DungeonRoomEvent -> {
            event.room
        }
        else -> {
            plugin.logger.debug("Event class '${event::class.qualifiedName}' cannot resolve a room getter")
            null
        }
    }

    init{
        EventTraitListenerHelper.registerEvent(DungeonCacheEvent::class) {
            used[it.instance] = 0
        }
    }
}

class RoomEnterTriggerTrait(
    uses: Int,
    override val targets: List<UUID>
): EventTriggerTrait<RoomEnterTriggerTrait, DungeonRoomPlayerEnterEvent>(DungeonRoomPlayerEnterEvent::class, uses, targets){
    override fun resolveRoom(event: DungeonRoomPlayerEnterEvent): RoomInstance? = event.room
    override fun question(res: EditingTraitHolder<RoomEnterTriggerTrait>): QuestionElement = QuestionElement.ForTrait(
        this,
        QuestionElement.Clickable("Uses"){

        }
    )
}

class RoomLeaveTriggerTrait(
    uses: Int,
    override val targets: List<UUID>
): EventTriggerTrait<RoomLeaveTriggerTrait, DungeonRoomPlayerLeaveEvent>(DungeonRoomPlayerLeaveEvent::class, uses, targets){
    override fun resolveRoom(event: DungeonRoomPlayerLeaveEvent): RoomInstance? = event.room
    override fun question(res: EditingTraitHolder<RoomLeaveTriggerTrait>): QuestionElement = QuestionElement.ForTrait(
        this,
        QuestionElement.Clickable("Uses"){

        }
    )
}

class DungeonMobKillTriggerTrait(
    val mob: IdKey,
    uses: Int,
    override val targets: List<UUID>
): EventTriggerTrait<DungeonMobKillTriggerTrait, DungeonMobKillEvent>(DungeonMobKillEvent::class, uses, targets){
    override fun resolveRoom(event: DungeonMobKillEvent): RoomInstance? =
        if (event.mob.identifier != mob) null
        else event.room

    override fun question(res: EditingTraitHolder<DungeonMobKillTriggerTrait>): QuestionElement = QuestionElement.ForTrait(
        this,
        QuestionElement.Clickable("Mob"){

        },
        QuestionElement.Clickable("Uses"){

        }
    )
}