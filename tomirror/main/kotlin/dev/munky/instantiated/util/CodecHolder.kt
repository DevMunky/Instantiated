package dev.munky.instantiated.util

import com.google.gson.JsonElement
import dev.munky.instantiated.common.serialization.JsonCodec
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties

abstract class CodecHolder(
    val errMsg: (String) -> String
) {
    protected val spine by lazy { initHolder(this) }

    fun <T: Any> get(clas: KClass<out T>): JsonCodec<T> {
        for (codec in spine) {
            if (clas == codec.clazz) { // hopefully the same classloader, otherwise this won't work lol
                @Suppress("UNCHECKED_CAST") // UR WRONG
                return codec as JsonCodec<T>
            }
        }
        throw IllegalStateException(errMsg(clas.simpleName!!))
    }

    fun <T: Any> get(str: String): JsonCodec<T> {
        for (codec in spine) {
            if (str == codec.clazz.simpleName) {// probably the same classloader
                @Suppress("UNCHECKED_CAST") // could actually be true its possible
                return codec as? JsonCodec<T> ?: throw IllegalStateException("There are probably multiple codecs named $str, or munky is retarded and the wrong type is being used.")
            }
        }
        throw IllegalStateException(errMsg(str))
    }
}

@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class ModifiableCodecHolder(errMsg: (String) -> String): CodecHolder(errMsg){
    /**
     * Register a new codec to this holder.
     */
    fun <T: Any> register(codec: JsonCodec<T>) {
        this.spine.add(codec)
    }

    /**
     * Registration method targeted to java.
     */
    fun <T: Any> register(clazz: Class<T>, encoder: (T) -> JsonElement, decoder: (JsonElement) -> T) {
        val codec = JsonCodec.of(clazz.kotlin, encoder, decoder)
        register(codec)
    }
}

/**
 * Need the generic type parameter from a method in order for the compiler to not freak out
 */
private fun <T: CodecHolder> initHolder(holder: T): HashSet<JsonCodec<*>> {
    // apparently ::class is very different from .javaClass.kotlin, and the IDE doesnt like the former here
    return holder.javaClass.kotlin.declaredMemberProperties.asSequence()
        .filter { it.returnType.classifier!! == JsonCodec::class }
        .map { it.get(holder) }
        .filterIsInstance<JsonCodec<*>>()
        .toHashSet()
}