package dev.munky.instantiated.edit

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.getIntraData
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.hasIntraData
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.removeIntraData
import dev.munky.instantiated.data.IntraDataStores.EntityIntraData.setIntraData
import dev.munky.instantiated.data.IntraEntry
import dev.munky.instantiated.data.loader.DataOperationResult
import dev.munky.instantiated.data.loader.FormatLoader
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.component.trait.SetBlocksTrait
import dev.munky.instantiated.dungeon.currentDungeon
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.event.ListenerFactory
import dev.munky.instantiated.lang.caption
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.send
import dev.munky.instantiated.util.toVector3i
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.Duration
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties

// i want to make listening to certain events for editing easier
class EditModeHandler: KoinComponent{
    object StateKeys{
        val EDIT_MODE = IntraEntry<States.EditMode.EditModeContext>("edit-mode")
        val SETTING_BLOCKS = IntraEntry<Pair<RoomInstance, SetBlocksTrait>>("setting-blocks")
    }

    val playersInEditMode: List<Player> get() =
        Bukkit.getOnlinePlayers()
        .filter { it.isInEditMode }
    var unsavedChanges = false

    private val states get() /*TODO remove get()*/ = States::class
        .nestedClasses
        .mapNotNull { it.objectInstance }
        .filterIsInstance<CustomEditorState<*>>()

    private val stateKeys get() /*TODO remove get()*/ = StateKeys::class
        .declaredMemberProperties
        .map { it.get(StateKeys) }
        .filterIsInstance<IntraEntry<*>>()

    // maybe edit everytime idk
    internal val hotbars = mutableMapOf(
        HotbarLayer.MAIN to getInvFromMap(
            0 to EditTool.Config.item,
            2 to EditTool.Component.item,
            4 to EditTool.Vertex.item,
            6 to EditTool.Door.item,
        )
    )

    fun initialize(){
        states.forEach { it.init() }
    }

    fun shutdown(){
        states.forEach { it.shutdown() }
        playersInEditMode.forEach { player ->
            takeOutOfEditMode(player)
        }
    }

    object States{
        object EditMode: CustomEditorState<EditMode.EditModeContext>(
            StateKeys.EDIT_MODE,
            {caption("edit.exit").send(it.player)},
            EventHandler<PlayerItemHeldEvent, EditModeContext>(PlayerItemHeldEvent::getPlayer) {
                val event = it.event
                val editMode = plugin.get<EditModeHandler>()
                if (!it.player.isSneaking) return@EventHandler
                val instance = event.player.currentDungeon ?: return@EventHandler
                if (!event.player.isInEditMode) return@EventHandler
                if (event.previousSlot == event.newSlot) return@EventHandler
                val left = event.previousSlot > event.newSlot
                val currentSelected = event.player.getIntraData(StateKeys.EDIT_MODE)!!.selectedRoom
                val lastPos = instance.rooms.keys.indexOf(currentSelected)
                val room = instance.rooms.keys.elementAt(
                    min(max(lastPos + (if (left) -1 else 1), 0), instance.rooms.size - 1)
                )
                editMode.setSelectedRoom(event.player, room)
                event.isCancelled = true
            },
            EventHandler<InventoryClickEvent, EditModeContext>({ it.whoClicked as Player }){ context ->
                val currentItem = context.event.currentItem
                if (EditTool.tools.map { it.value.item }.any { it == currentItem }) {
                    context.event.isCancelled = true
                }
            },
            EventHandler<PlayerInteractEvent, EditModeContext>(PlayerInteractEvent::getPlayer){
                val event = it.event
                if (event.hand != EquipmentSlot.HAND) return@EventHandler
                val dungeon = plugin.get<DungeonManager>().getCurrentDungeon(event.player.uniqueId) ?: return@EventHandler
                val roomKey = it.data.selectedRoom ?: return@EventHandler
                val room = dungeon.rooms[roomKey] ?: return@EventHandler
                var point = event.interactionPoint
                if (point == null && event.clickedBlock != null) point = event.clickedBlock!!.location
                val interaction = EditToolInteraction(event, dungeon, room, point)
                if (EditTool.execute(interaction)) {
                    plugin.get<EditModeHandler>().unsavedChanges = true
                }
            }
        ){
            data class EditModeContext(
                val inventory: InventorySnapshot,
                val selectedRoom: IdKey?
            )
        }
        object SettingBlocks: CustomEditorState<Pair<RoomInstance, SetBlocksTrait>>(
            StateKeys.SETTING_BLOCKS,
            {caption("edit.state.exit", StateKeys.SETTING_BLOCKS.key).send(it.player)},
            EventHandler<PlayerInteractEvent, Pair<RoomInstance, SetBlocksTrait>>(PlayerInteractEvent::getPlayer){ c ->
                if (c.event.hand != EquipmentSlot.HAND) return@EventHandler
                c.event.isCancelled = true
                val room = c.data.first
                val trait = c.data.second
                val block = c.event.clickedBlock?.location?.toVector3i ?: return@EventHandler
                block.sub(room.realVector.toVector3i)
                if (block in trait.blocks) {
                    trait.blocks.remove(block)
                    caption("edit.component.set_blocks_trait.remove", block.x, block.y, block.z).send(c.event.player)
                } else {
                    trait.blocks.add(block)
                    caption("edit.component.set_blocks_trait.add", block.x, block.y, block.z).send(c.event.player)
                }
            }
        )
    }

    enum class HotbarLayer{ // pretty much unused
        MAIN,
        COMPONENT_EDIT
    }

    private fun getInvFromMap(vararg pairs: Pair<Int, ItemStack>): InventorySnapshot{
        return InventorySnapshot(HashMap(mutableMapOf(*pairs)))
    }

    // fixed -> maybe have players scroll to select a room with a tool rather than closest room>? i think it makes way more sense

    private val times = Title.Times.times(
        Duration.ofMillis(150), Duration.ofMillis(350), Duration.ofMillis(150)
    )

    fun setSelectedRoom(player: Player, id: IdKey) {
        if (player.isInEditMode) {
            val data = player.getIntraData(StateKeys.EDIT_MODE)!!
            player.setIntraData(StateKeys.EDIT_MODE, States.EditMode.EditModeContext(data.inventory, id))
            player.sendTitlePart(TitlePart.TIMES, times)
            player.sendTitlePart(TitlePart.SUBTITLE, caption("edit.title.selected_room", id.key))
            player.sendTitlePart(TitlePart.TITLE, Component.empty())
        }
    }

    fun putInEditMode(player: Player) {
        if (get<FormatLoader>().lastLoadResult != DataOperationResult.SUCCESS) {
            player.sendMessage(caption("edit.deny"))
            plugin.logger
                .warning("Denied edit mode access due to dangerous loading state -> ${get<FormatLoader>().lastLoadResult}")
            return
        }
        removeEditStateFor(player)
        val items = EditTool.tools.values.map { it.item }
        val inv = HashMap<Int,ItemStack>()
        player.inventory.withIndex().forEach { if (it.value != null) inv[it.index] = it.value.clone()}
        val snap = InventorySnapshot(inv)
        inv.filter{ items.contains(it.value) }.forEach {
            snap.contents.remove(it.key)
        }
        val current = player.currentDungeon
        val id = current?.rooms?.values?.first()?.identifier
        player.setIntraData(StateKeys.EDIT_MODE, States.EditMode.EditModeContext(snap, id))
        player.inventory.clear()
        hotbars[HotbarLayer.MAIN] applyTo player
        caption("command.edit.enter_mode", current?.identifier?.key ?: "").send(player)
    }

    fun takeOutOfEditMode(player: Player) {
        player.inventory.clear()
        val snap = player.getIntraData(StateKeys.EDIT_MODE)
        snap?.inventory applyTo player
        removeEditStateFor(player)
        val current = player.currentDungeon
        caption("command.edit.exit_mode", current?.identifier?.key ?: "").send(player)
    }

    private fun removeEditStateFor(player: Player) = stateKeys.forEach { player.removeIntraData(it) }

    data class InventorySnapshot(
        val contents: HashMap<Int, ItemStack>
    ){
        override fun toString() : String {
            return contents.toString()
        }

        infix fun applyTo(player: Player){
            this.contents.forEach {
                player.inventory.setItem(it.key, it.value)
            }
        }
    }

    private infix fun InventorySnapshot?.applyTo(player: Player) = this?.applyTo(player)
}

val Player.isInEditMode : Boolean get() = this.hasIntraData(EditModeHandler.StateKeys.EDIT_MODE)

inline fun <reified E: Event, C: Any> EventHandler(
    noinline player: (E) -> Player,
    noinline handler: (CustomEditorState. StateContextWithPlayer<E, C>) -> Unit
): CustomEditorState.EventHandler<E, C> = CustomEditorState.EventHandler(E::class, player, handler)

abstract class CustomEditorState<T : Any>(
    val key: IntraEntry<T>,
    val exitCallback: (StateContextWithPlayer<PlayerToggleSneakEvent, T>) -> Unit = {},
    val events: List<EventHandler<*, T>>
){
    constructor(
        key: IntraEntry<T>,
        exitCallback: (StateContextWithPlayer<PlayerToggleSneakEvent, T>) -> Unit = {},
        vararg events: EventHandler<*, T>
    ): this(key, exitCallback, events.toList())
    private val _listeners = mutableSetOf<Listener>()
    fun init() {
        if (_listeners.isNotEmpty()) shutdown()
        events.forEach {
            val l = ListenerFactory.registerEvent(it.event.java) { event->
                try{
                    val player = it.player(event)
                    if (!player.hasIntraData(key)) return@registerEvent
                    val data = player.getIntraData(key)!!
                    val context = StateContextWithPlayer(player, event, data)
                    it.handle(context)
                }catch (t: Throwable){
                    t.log("Severe edit mode error. key=$key,event=${event::class.simpleName}")
                }
            }
            _listeners.add(l)
        }
        if (this !is EditModeHandler.States.EditMode){
            val sneak = ListenerFactory.registerEvent(PlayerToggleSneakEvent::class.java){ event ->
                if (!event.isSneaking) return@registerEvent
                val data = event.player.getIntraData(key) ?: return@registerEvent
                event.player.removeIntraData(key)
                exitCallback(StateContextWithPlayer(event.player, event, data))
            }
            _listeners.add(sneak)
        }
        plugin.logger.debug("CustomEditorState ${this::class.simpleName} initialized")
    }
    fun shutdown(){
        _listeners.forEach { HandlerList.unregisterAll(it) }
        _listeners.clear()
    }
    data class StateContextWithPlayer<E: Event, T: Any>(
        val player: Player,
        val event: E,
        val data: T
    )
    data class FirstStateContext<T: Any>(
        val player: Player,
        val data: T
    )
    data class EventHandler<E: Event, C: Any>(
        val event: KClass<out E>,
        private val playerer: (E) -> Player,
        private val handler: (StateContextWithPlayer<E, C>) -> Unit
    ){
        fun handle(context: StateContextWithPlayer<*, C>) {
            @Suppress("UNCHECKED_CAST") // actually checked!
            if (event.isInstance(context.event)) handler(context as StateContextWithPlayer<E, C>)
            else throw IllegalStateException("Expected class -> ${event.qualifiedName}, got class -> ${context.event::class.qualifiedName}")
        }
        fun player(event: Any): Player{
            @Suppress("UNCHECKED_CAST") // actually is checked!
            return if (this.event.isInstance(event)) playerer(event as E)
            else throw IllegalStateException("Expected class -> ${this.event.qualifiedName}, got class -> ${event::class.qualifiedName}")
        }
    }
}
