package dev.munky.instantiated.data

import org.bukkit.entity.Entity
import java.util.concurrent.ConcurrentHashMap

abstract class DungeonIntraData<T : Any> {
    private val data = ConcurrentHashMap<String, IntraDataValue<*>>()
    fun <V> setIntraData(subject: T, key: IntraEntry<V>, value: V) {
        val dis = disambiguate(subject, key)
        data[dis] = FixedIntraDataValue(value)
    }
    fun <V> setIntraData(subject: T, key: IntraEntry<V>, value: () -> V) {
        val dis = disambiguate(subject, key)
        data[dis] = LazyIntraDataValue(value)
    }
    fun <V> getIntraData(subject: T, key: IntraEntry<V>): V? {
        val dis = disambiguate(subject, key)
        val value = data[dis]
        @Suppress("UNCHECKED_CAST") // enforced by the add set function
        return if (value == null) null else value() as? V ?: throw IllegalStateException("Duplicate key -> $key = $value")
    }
    fun hasIntraData(subject: T, key: IntraEntry<*>): Boolean {
        val dis = disambiguate(subject, key)
        return data.containsKey(dis)
    }
    fun <V> removeIntraData(subject: T, key: IntraEntry<V>): V? {
        val dis = disambiguate(subject, key)
        val value = data.remove(dis)
        @Suppress("UNCHECKED_CAST") // enforced by the add set function
        return if (value == null) null else value() as? V ?: throw IllegalStateException("Duplicate key -> $key = $value")
    }
    protected abstract fun disambiguate(subject: T, key: IntraEntry<*>): String

    interface IntraDataValue<T>: () -> T

    class FixedIntraDataValue<T>(val value: T): IntraDataValue<T> {
        override fun invoke(): T = value
    }
    class LazyIntraDataValue<T>(val f: () -> T): IntraDataValue<T> {
        var _value: Any? = UNINITIALIZED
        @Suppress("UNCHECKED_CAST")
        override fun invoke(): T {
            if (_value === UNINITIALIZED) _value = f()
            return _value as T
        }
    }
}

private object UNINITIALIZED {}

object IntraDataStores {
    object EntityIntraData: DungeonIntraData<Entity>() {
        override fun disambiguate(subject: Entity, key: IntraEntry<*>): String = "${subject.uniqueId}:$key"
        fun <T> Entity.getIntraData(key: IntraEntry<T>): T? = EntityIntraData.getIntraData(this, key)
        fun Entity.hasIntraData(key: IntraEntry<*>): Boolean = EntityIntraData.hasIntraData(this, key)
        fun <T> Entity.setIntraData(key: IntraEntry<T>, value: T) = EntityIntraData.setIntraData(this, key, value)
        fun <T> Entity.setIntraData(key: IntraEntry<T>, value: () -> T) = EntityIntraData.setIntraData(this, key, value)
        fun <T> Entity.removeIntraData(key: IntraEntry<T>): T? = EntityIntraData.removeIntraData(this, key)
    }
}

class IntraEntry<V>( // this type parameter is only for compile time type checking
    val key: String
){
    override fun toString(): String = "IntraEntry[$key]"
}
