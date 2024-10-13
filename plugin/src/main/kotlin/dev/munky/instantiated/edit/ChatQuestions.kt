package dev.munky.instantiated.edit

import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.loader.FormatLoader
import dev.munky.instantiated.dungeon.component.trait.Trait
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomFormat
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.dungeon.sstatic.StaticInstance
import dev.munky.instantiated.dungeon.sstatic.StaticRoomFormat
import dev.munky.instantiated.dungeon.sstatic.StaticRoomInstance
import dev.munky.instantiated.event.ListenerFactory
import dev.munky.instantiated.lang.caption
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import dev.munky.instantiated.util.ComponentUtil
import dev.munky.instantiated.util.asString
import dev.munky.instantiated.util.fromMini
import dev.munky.instantiated.util.send
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Keyed
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.joml.Vector3f
import org.joml.Vector3i
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

object ChatQuestions{

    fun getRoomConfigQuestion(room: RoomInstance): Component = ComponentConfiguration(
        ComponentLabel("Configuring '${room.identifier}'".fromMini),
        ComponentQuestion("Key-Drop-Mode".fromMini,
            ComponentOption(RoomFormat.KeyDropMode.ROOM_MOBS_CLEAR.name.fromMini){
                room.format.keyDropMode = RoomFormat.KeyDropMode.ROOM_MOBS_CLEAR
                it.sendMessage("<green>Set key drop mode to ROOM_MOBS_CLEAR".fromMini)
            },
            ComponentOption(RoomFormat.KeyDropMode.MARKED_ROOM_MOB_KILL.name.fromMini){
                room.format.keyDropMode = RoomFormat.KeyDropMode.MARKED_ROOM_MOB_KILL
                it.sendMessage("<green>Set key drop mode to MARKED_ROOM_MOB_KILL".fromMini)
            }
        ),
        ComponentQuestion("Key-material".fromMini,
            ComponentOption("Click to set".fromMini){ player->
                val type = PromptFactory.promptRegistry(RegistryKey.ITEM, player) ?: return@ComponentOption
                room.format.keyMaterial = type.asMaterial()!!
                player.sendMessage(ComponentUtil.toComponent("<green>Set key material of room to ${type.key.key}"))
            }
        )
    ).component

    fun getDungeonQuestion(dungeon: Instance): Component = ComponentConfiguration(
        ComponentLabel("Configuring '${dungeon.identifier}'".fromMini),
        ComponentQuestion("Schematic".fromMini,
            ComponentOption("Click to set schematic".fromMini){ player ->
                player.sendMessage(ComponentUtil.toComponent("<blue>Enter the schematic file name"))
                val schem = PromptFactory.promptString("schematic name", player)
                if (dungeon !is StaticInstance) return@ComponentOption
                val file = handleSchematicCallback(dungeon, schem)
                file.onFailure {
                    player.sendMessage("Error: ${it.message}".fromMini)
                    return@ComponentOption
                }
                dungeon.format.schematic = file.getOrThrow()
            }
        ),
        ComponentLabel("Rooms".fromMini),
        ComponentQuestion("Add a room".fromMini,
            ComponentOption("Click to add".fromMini){ audience ->
                val name = PromptFactory.promptString("room name", audience)
                val box = Box(Vector3f(0f), Vector3f(5f))
                val room = StaticRoomInstance(
                    dungeon as StaticInstance,
                    StaticRoomFormat(
                        IdType.ROOM with name,
                        dungeon.format,
                        Vector3i(0),
                        box,
                        RoomFormat.KeyDropMode.MARKED_ROOM_MOB_KILL,
                        Material.STONE
                    )
                )
                dungeon.rooms[room.identifier] = room
                audience.sendMessage("Added room '$name' to dungeon, with some default values".fromMini)
            }
        ),
        ComponentQuestion("Remove a room".fromMini,
            dungeon.rooms.map { room->
                ComponentOption(room.value.identifier.key.fromMini){
                    dungeon.rooms.remove(room.key)
                    it.sendMessage("Removed room ${room.key.key}".fromMini)
                }
            }
        )
    ).component

    private fun handleSchematicCallback(dungeon: Instance, schem: String): Result<File> {
        var schematicFile = FormatLoader.REGISTERED_SCHEMATICS[schem]
        if (schematicFile == null) // trying again, this time appending .schem
            schematicFile = FormatLoader.REGISTERED_SCHEMATICS["$schem.schem"]
        return if (schematicFile==null){
            Result.failure(IllegalStateException("No schematic named '$schem' found"))
        } else if (dungeon !is StaticInstance) {
            Result.failure(IllegalStateException("This dungeon is not a static dungeon"))
        } else Result.success(schematicFile)
    }

    data class ComponentConfiguration(
        val elements: List<ComponentConfigurationElement>
    ){
        constructor(
            vararg questions: ComponentConfigurationElement
        ): this(questions.toList())

        val component: Component get() {
            var component: Component = Component.newline()
            component = component.append(CONFIGURATION_QUESTION_HEADER)
            elements.forEach {
                component = component.appendNewline().append(it.component)
            }
            return component
        }
    }

    interface ComponentConfigurationElement{
        val component: Component
    }

    data class ComponentLabel(
        override val component: Component
    ): ComponentConfigurationElement

    data class ComponentQuestion(
        val label: Component,
        val options: List<ComponentOption>
    ): ComponentConfigurationElement{

        constructor(
            label: Component,
            vararg options: ComponentOption
        ): this(label, options.toList())

        override val component: Component get() {
            var component = label
            options.forEach {
                component = component.append(it.component)
            }
            return component
        }
    }

    /**
     * The [callback] is always in an asynchronous state, the thread executor specifically is the
     * `Instantiated Chat Question Processing Thread`
     */
    data class ComponentOption(
        val label: Component,
        val hover: Component,
        val callback: (Audience) -> Unit
    ){

        constructor(
            label: Component,
            callback: (Audience) -> Unit
        ): this(label, "Click to set".fromMini,callback)

        val component: Component get() = Component
            .newline()
            .append(
                Component
                    .text("-> ")
                    .color(NamedTextColor.BLUE)
                    .append(label)
                    .hoverEvent(hover)
                    .clickEvent(ClickEvent.callback(
                        { audience ->
                            val scheduler = Schedulers.CHAT_QUESTION
                            plugin.logger.debug("Submitted callback to async pool")
                            scheduler.submit {
                                plugin.logger.debug("Asynchronously calling back option click")
                                try{
                                    callback(audience)
                                }catch (_: TimeoutException){
                                    audience.sendMessage("<red>Prompt timed out".fromMini)
                                    plugin.logger.debug("Prompt timed out")
                                }
                            }
                        },
                        QUESTION_CLICKABLE_OPTIONS
                    ))
            )
    }
}

private val CONFIGURATION_QUESTION_HEADER =
    Component.text("This message will expire after 25 seconds")
        .color(NamedTextColor.RED).decorate(TextDecoration.BOLD)

private val QUESTION_CLICKABLE_OPTIONS: ClickCallback.Options =
    ClickCallback.Options.builder().lifetime(25L.seconds.toJavaDuration()).uses(1).build()

private val DEFAULT_HOVER_MESSAGE = Component.text("Click to set")

/**
 * in-chat example below
 * ```text
 * WILL EXPIRE IN 25 SECONDS {
 *   label-1
 *   list-1 [
 *     option-1
 *   ]
 *   label-2
 *   list-2 [
 *     list-3 [
 *       option-2
 *     ]
 *   ]
 * }
 * ```
 */
sealed interface QuestionElement{
    fun withScope(level: Int): Component
    fun build(): Component = withScope(0)

    data class Header(
        val elements: Collection<QuestionElement>
    ): QuestionElement{
        constructor(
            vararg components: QuestionElement
        ): this(components.toList())

        override fun withScope(level: Int): Component {
            var component = Component.empty() as Component
            component = component.append(CONFIGURATION_QUESTION_HEADER).append(Component.text(" {"))
            for (e in elements){
                component = component.appendNewline().withScope(1).append(e.withScope(1))
            }
            component = component.appendNewline().withScope(1).append(Component.text("}"))
            return component
        }
    }

    open class ListOf (
        val label: Component,
        val elements: Collection<QuestionElement>
    ): QuestionElement {
        constructor(label: Component, vararg options: QuestionElement): this(label, options.toList())

        constructor(label: String, vararg options: QuestionElement): this(label.fromMini, options.toList())
        constructor(label: String, options: Collection<QuestionElement>): this(label.fromMini, options)

        override fun withScope(level: Int): Component {
            var component = Component.empty() as Component
            component = component.withScope(level).append(label).append(Component.text(" ["))
            val newLevel = level + 1
            for (e in elements){
                component = component.appendNewline().withScope(newLevel).append(e.withScope(newLevel))
            }
            component = component.appendNewline().withScope(newLevel).append(Component.text("]"))
            return component
        }
    }

    class ForTrait(
        val trait: Trait<*>,
        vararg options: QuestionElement
    ): ListOf(Component.text(trait.identifier.key), *options)

    companion object{
        private val scopeCache = HashMap<Int, Component>()
        fun Component.withScope(level: Int): Component {
            val c = scopeCache.getOrPut(level) { Component.text(" ".repeat(level)) }
            return this.append(c)
        }
    }

    data class Label(
        val component: Component
    ): QuestionElement {
        constructor(label: String): this(label.fromMini)
        override fun withScope(level: Int): Component = Component.empty().withScope(level).append(component)
    }

    data class Clickable(
        val label: Component,
        val hover: Component,
        val callback: (Audience) -> Unit
    ): QuestionElement {
        constructor(
            label: Component,
            callback: (Audience) -> Unit
        ): this(label, DEFAULT_HOVER_MESSAGE,callback)

        constructor(
            label: String,
            callback: (Audience) -> Unit
        ): this(label.fromMini, DEFAULT_HOVER_MESSAGE,callback)

        override fun withScope(level: Int): Component {
            val scoped = Component.empty().withScope(level)
            val c = Component.text("-> ").color(NamedTextColor.BLUE)
                .append(label).hoverEvent(hover).clickEvent(ClickEvent.callback(
                    { audience ->
                        val scheduler = Schedulers.CHAT_QUESTION
                        plugin.logger.debug("Submitted callback to async pool")
                        scheduler.submit {
                            plugin.logger.debug("Asynchronously calling back option click")
                            try{
                                callback(audience)
                            }catch (_: TimeoutException){
                                audience.sendMessage("<red>Prompt timed out".fromMini)
                                plugin.logger.debug("Prompt timed out")
                            }
                        }
                    },
                    QUESTION_CLICKABLE_OPTIONS
                ))
            return scoped.append(c)
        }
    }
}

object PromptFactory{
    private val YOU_CANT_SEE_ME = Component.text("(players can not see this message)").color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)
    private fun <T: Any?> internal(prompt: Component, audience: Audience, timeout: Duration, f: (String) -> T): CompletableFuture<T>{
        val response = CompletableFuture<T>()
        val start = System.currentTimeMillis().milliseconds
        audience.sendMessage(prompt)
        ListenerFactory.registerEvent(AsyncChatEvent::class.java)
        { e: AsyncChatEvent, l: Listener ->
            if (e.player !== audience) return@registerEvent // watch this referential equality check
            HandlerList.unregisterAll(l)
            if (start.minus(System.currentTimeMillis().milliseconds) > timeout) return@registerEvent
            e.isCancelled = true
            val str = e.message().asString
            val c = Component.text("-> $str").append(YOU_CANT_SEE_ME).color(NamedTextColor.DARK_GREEN)
            audience.sendMessage(Component.newline().append(c))
            try{
                response.complete(f(str))
            }catch (t: Throwable){
                response.completeExceptionally(t)
            }
        }
        return response
    }

    /**
     * the timeout is needed here to make sure that no chat events are handled if the given timeout has been exceeded since the method was invoked.
     * This means you need a timeout when the prompt is created, and a timeout of preferably the same length when you go to get the completable future.
     */
    fun <T: Any?> prompt(prompt: Component, audience: Audience, timeout: Duration, f: (String) -> T): T = internal(prompt, audience, timeout, f).get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

    fun promptFloats(count: Int, audience: Audience, timeout: Duration = 10L.seconds): FloatArray? =
        prompt("<green>Enter $count floats seperated by a space".fromMini, audience, timeout) { response ->
            val splitResponse = response.split(" ")
            if (splitResponse.size != count) {
                audience.sendMessage("Not enough arguments! Needed $count, received ${splitResponse.size}".fromMini)
                return@prompt null
            }
            try{
                FloatArray(count) {
                    splitResponse[it].toFloat()
                }
            }catch (t: Throwable){
                audience.sendMessage("<red>Could not parse response -> ${t.message}".fromMini)
                if (t !is NumberFormatException) t.log("parse error")
                null
            }
        }

    fun promptIntegers(count: Int, audience: Audience, timeout: Duration = 10L.seconds): IntArray? =
        prompt("<green>Enter $count integers seperated by a space".fromMini, audience, timeout) { response ->
            val splitResponse = response.split(" ")
            if (splitResponse.size != count) {
                audience.sendMessage("Not enough arguments! Needed $count, received ${splitResponse.size}".fromMini)
                return@prompt null
            }
            try{
                IntArray(count) {
                    splitResponse[it].toInt()
                }
            }catch (t: Throwable){
                audience.sendMessage("<red>Could not parse response -> ${t.message}".fromMini)
                if (t !is NumberFormatException) t.log("parse error")
                null
            }
        }

    fun <T : Keyed> promptRegistry(reg: RegistryKey<T>, audience: Audience, timeout: Duration = 10L.seconds): T? =
        prompt(caption("edit.question.prompt.registry", reg.key().value()), audience, timeout){
            val ret = RegistryAccess.registryAccess().getRegistry(reg).get(NamespacedKey.minecraft(it))
            ret ?: caption("edit.question.prompt.registry.unknown").send(audience)
            ret
        }

    fun promptString(name: String, audience: Audience, timeout: Duration = 10L.seconds): String =
        prompt(caption("edit.question.prompt.string", name), audience, timeout){ it.trim() }

    fun promptStrings(name: String, delimiter: String, audience: Audience, timeout: Duration = 10L.seconds): Collection<String> =
        prompt(caption("edit.question.prompt.strings", name), audience, timeout){ it.split(delimiter) }
}