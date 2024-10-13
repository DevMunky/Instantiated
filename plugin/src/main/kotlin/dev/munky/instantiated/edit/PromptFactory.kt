package dev.munky.instantiated.edit

import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.loader.caption
import dev.munky.instantiated.event.ListenerFactory
import dev.munky.instantiated.util.asString
import dev.munky.instantiated.util.asComponent
import dev.munky.instantiated.util.send
import dev.munky.instantiated.util.toVector3i
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.joml.Vector3i
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object PromptFactory {
    private val YOU_CANT_SEE_ME =
        Component.text("(players can not see this message)").color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)

    private fun <T : Any> internal(
        prompt: Component,
        audience: Audience,
        timeout: Duration,
        f: (String) -> T
    ): CompletableFuture<T> {
        val response = CompletableFuture<T>()
        val start = System.currentTimeMillis().milliseconds
        audience.sendMessage(prompt)
        ListenerFactory.registerEvent(AsyncChatEvent::class.java)
        { e: AsyncChatEvent, l: Listener ->
            if (e.player !== audience) return@registerEvent // watch this referential equality check
            e.isCancelled = true
            val str = e.message().asString
            try {
                if (str == "n" || str == "cancel" || str == "no") throw CancellationException("Prompt cancelled")
                if (start.minus(System.currentTimeMillis().milliseconds) > timeout) throw TimeoutException("Prompt timed out")
                val v = f(str)
                val c = Component.text("-> $str").append(YOU_CANT_SEE_ME).color(NamedTextColor.DARK_GREEN)
                audience.sendMessage(Component.newline().append(c))
                response.complete(v)
                HandlerList.unregisterAll(l)
            } catch (t: RetryPromptException) {
                if (!t.silent && t.message == null) caption("edit.question.prompt.retry").send(audience)
                else if (!t.silent && t.message != null) Component.text(t.message!!).send(audience)
            } catch (t: Throwable) {
                response.completeExceptionally(t)
            }
        }
        return response
    }

    /**
     * the timeout is needed here to make sure that no chat events are handled if the given timeout has been exceeded since the method was invoked.
     * This means you need a timeout when the prompt is created, and a timeout of preferably the same length when you go to get the completable future.
     */
    fun <T : Any> prompt(prompt: Component, audience: Audience, timeout: Duration, f: (String) -> T): T {
        threadCheck()
        return internal(prompt, audience, timeout, f).get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    private fun threadCheck() {
        if (Bukkit.isPrimaryThread()) throw IllegalStateException("Prompts cannot be evaluated on the main thread")
    }

    fun promptFloats(count: Int, audience: Audience, timeout: Duration = 10L.seconds): FloatArray =
        prompt("<green>Enter $count floats seperated by a space".asComponent, audience, timeout) { response ->
            val splitResponse = response.split(" ")
            if (splitResponse.size != count) {
                audience.sendMessage("Not enough arguments! Needed $count, received ${splitResponse.size}".asComponent)
                throw RetryPromptException()
            }
            try {
                FloatArray(count) {
                    splitResponse[it].toFloat()
                }
            } catch (t: Throwable) {
                audience.sendMessage("<red>Could not parse response -> ${t.message}".asComponent)
                if (t !is NumberFormatException) t.log("parse error")
                throw RetryPromptException()
            }
        }

    fun promptIntegers(count: Int, audience: Audience, timeout: Duration = 10L.seconds): IntArray =
        prompt("<green>Enter $count integers seperated by a space".asComponent, audience, timeout) { response ->
            val splitResponse = response.split(" ")
            if (splitResponse.size != count) {
                audience.sendMessage("Not enough arguments! Needed $count, received ${splitResponse.size}".asComponent)
                throw RetryPromptException()
            }
            try {
                IntArray(count) {
                    splitResponse[it].toInt()
                }
            } catch (t: Throwable) {
                audience.sendMessage("<red>Could not parse response -> ${t.message}".asComponent)
                if (t !is NumberFormatException) t.log("parse error")
                throw RetryPromptException()
            }
        }

    fun <T : Keyed> promptRegistry(reg: RegistryKey<T>, audience: Audience, timeout: Duration = 10L.seconds): T =
        prompt(caption("edit.question.prompt.registry", reg.key().value()), audience, timeout) {
            val ret = RegistryAccess.registryAccess().getRegistry(reg).get(NamespacedKey.minecraft(it))
            if (ret == null) {
                caption("edit.question.prompt.registry.unknown").send(audience)
                throw RetryPromptException(false)
            } else ret
        }

    fun promptString(label: String, audience: Audience, timeout: Duration = 10L.seconds): String =
        prompt(caption("edit.question.prompt.string", label), audience, timeout) { it.trim() }

    fun promptStrings(
        label: String,
        delimiter: String,
        audience: Audience,
        timeout: Duration = 10L.seconds
    ): Collection<String> =
        prompt(caption("edit.question.prompt.strings", label), audience, timeout) { it.split(delimiter) }

    private fun <T : Event, V : Any> internalEvent(
        event: KClass<T>,
        prompt: Component,
        audience: Audience,
        timeout: Duration,
        f: (T) -> V
    ): CompletableFuture<V> {
        val response = CompletableFuture<V>()
        val start = System.currentTimeMillis().milliseconds
        audience.sendMessage(prompt)
        ListenerFactory.registerEvent(event.java) { e, l: Listener ->
            try {
                if (start.minus(System.currentTimeMillis().milliseconds) <= timeout) {
                    val v = f(e)
                    audience.sendMessage(Component.text("Value received"))
                    response.complete(v)
                } else response.completeExceptionally(TimeoutException("Prompt timed out!"))
                HandlerList.unregisterAll(l)
            } catch (t: RetryPromptException) {
                if (!t.silent && t.message == null) caption("edit.question.prompt.retry").send(audience)
                else if (!t.silent && t.message != null) Component.text(t.message!!).send(audience)
            } catch (t: Throwable) {
                response.completeExceptionally(t)
                HandlerList.unregisterAll(l)
            }
        }
        return response
    }

    /**
     * This prompt is very similar to the others, except that the function you pass in is quite different.
     *
     * Instead of the function consuming the returned chat input and supplying the desired type,
     * the function must now take the entire event and return the desired type.
     *
     * This means it must take into account the correct sender to ensure the prompt actually resolves with the correct context.
     *
     * Although, in every situation the timeout will always work, so there wont be any accidental soft-locks.
     *
     * A null value can be returned, as well as any exception. The only exception to the rule is the [RetryPromptException].
     *
     * Throwing that exception will actually not unregister the listener, and force the listener to continue listening to events.
     */
    fun <T : Event, V : Any> promptEvent(
        event: KClass<T>,
        prompt: Component,
        audience: Audience,
        timeout: Duration,
        f: (T) -> V
    ): V {
        threadCheck()
        return internalEvent(event, prompt, audience, timeout, f).get(timeout.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )
    }

    class RetryPromptException(val silent: Boolean = false, msg: String? = null) : RuntimeException(msg)

    /**
     * Returns a real clicked location
     */
    fun promptLocation(label: String, audience: Audience, timeout: Duration = 10L.seconds): Vector3i =
        promptEvent(PlayerInteractEvent::class, caption("edit.question.prompt.location", label), audience, timeout) {
            if (it.player !== audience) throw RetryPromptException(true) // silently retry listening
            val location = it.interactionPoint ?: it.clickedBlock?.location ?: throw RetryPromptException(true)
            location.toVector3i
        }

    fun promptEntity(label: String, audience: Audience, timeout: Duration = 10L.seconds): LivingEntity =
        promptEvent(PlayerInteractEntityEvent::class, caption("edit.question.prompt.entity", label), audience, timeout) {
            if (it.player !== audience) throw RetryPromptException(true)
            it.rightClicked as LivingEntity
        }
}