package dev.munky.instantiated.edit

import dev.munky.instantiated.common.util.formatException
import dev.munky.instantiated.common.util.times
import dev.munky.instantiated.dungeon.component.DungeonComponent
import dev.munky.instantiated.dungeon.component.trait.Trait
import dev.munky.instantiated.plugin
import dev.munky.instantiated.scheduling.Schedulers
import dev.munky.instantiated.util.asComponent
import dev.munky.instantiated.util.send
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

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
 *   list-4 []
 * }
 * ```
 */
sealed interface QuestionElement{
    fun withScope(level: Int): Component
    fun build(): Component = withScope(0)

    data class Header(
        val elements: Collection<QuestionElement>
    ): QuestionElement {
        constructor(
            vararg components: QuestionElement
        ): this(components.toList())

        override fun withScope(level: Int): Component {
            var component = Component.empty() as Component
            component = component.append(CONFIGURATION_QUESTION_HEADER).append(Component.text(" {"))
            if (elements.isEmpty()) return component.append(Component.text("}"))
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

        constructor(label: String, vararg options: QuestionElement): this(label.asComponent, options.toList())
        constructor(label: String, options: Collection<QuestionElement>): this(label.asComponent, options)

        override fun withScope(level: Int): Component {
            var component = Component.empty() as Component
            component = component.withScope(level).append(label).append(Component.text(" ["))
            if (elements.isEmpty()) return component.append(Component.text("]"))
            val newLevel = level + 1
            for (e in elements){
                component = component.appendNewline().withScope(newLevel).append(e.withScope(newLevel))
            }
            component = component.appendNewline().withScope(newLevel).append(Component.text("]"))
            return component
        }
    }

    class ForTrait(
        val trait: Trait,
        vararg options: QuestionElement
    ): ListOf(Component.text(trait.identifier.key), *options)

    companion object{
        private val scopeCache = HashMap<Int, Component>()
        fun Component.withScope(level: Int): Component {
            val c = scopeCache.getOrPut(level) { Component.text(" ".repeat(level)) }
            return this.append(c)
        }
    }

    class ForComponent(
        val comp: DungeonComponent
    ): ListOf(Component.text(comp.identifier.key), run {
        val traits = ArrayList<QuestionElement>()

        traits
    })

    data class Label(
        val component: Component
    ): QuestionElement {
        constructor(label: String): this(label.asComponent)
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
            current: Any?,
            callback: (Audience) -> Unit
        ): this("$label = $current", callback)

        constructor(
            label: String,
            callback: (Audience) -> Unit
        ): this(label.asComponent, DEFAULT_HOVER_MESSAGE,callback)

        override fun withScope(level: Int): Component {
            val scoped = Component.empty().withScope(level)
            val c = Component.text("-> ").color(NamedTextColor.BLUE)
                .append(label).hoverEvent(hover).clickEvent(
                    ClickEvent.callback(
                        { audience ->
                            plugin.logger.debug("Submitted callback to chat question thread")
                            Schedulers.CHAT_QUESTION.submit {
                                try {
                                    CLEAR_CHAT_COMPONENT.send(audience)
                                    callback(audience)
                                } catch (t: TimeoutException) {
                                    audience.sendMessage("<red>${t.message}".asComponent)
                                    plugin.logger.debug("Timed out: ${t.message}")
                                } catch (t: PromptFactory.RetryPromptException) {
                                    plugin.logger.debug("Not sure how a prompt exception got out")
                                    val format = t.formatException(true, 15)
                                    for (line in format.lines()) {
                                        plugin.logger.debug(line)
                                    }
                                } catch (t: Throwable) {
                                    audience.sendMessage("<red>${t::class.simpleName}: ${t.message}".asComponent)
                                    plugin.logger.debug("Prompt exception: ${t.formatException(true, 3)}")
                                }
                            }
                        },
                        QUESTION_CLICKABLE_OPTIONS
                    )
                )
            return scoped.append(c)
        }
    }
}
private val CONFIGURATION_QUESTION_HEADER =
    Component.text("This message will expire after 25 seconds")
        .color(NamedTextColor.RED).decorate(TextDecoration.BOLD)

private val QUESTION_CLICKABLE_OPTIONS: ClickCallback.Options =
    ClickCallback.Options.builder().lifetime(25L.seconds.toJavaDuration()).uses(1).build()

private val DEFAULT_HOVER_MESSAGE = Component.text("Click to set")

val CLEAR_CHAT_COMPONENT: Component = run {
    var c = Component.newline() as Component
    99.times {
        c = c.appendNewline()
    }
    c
}