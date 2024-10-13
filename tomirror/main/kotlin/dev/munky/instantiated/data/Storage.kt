package dev.munky.instantiated.data

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.Identifiable
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.plugin
import kotlin.collections.set

abstract class Storage<K: Identifiable, V>(
    val allowPostRegister: Boolean = false
): Map<K, V>{
    protected var spine = HashMap<IdKey, K>()
    protected var data = HashMap<K, V>()
    private var _initializing = false
    val initializing: Boolean get() = _initializing
    open fun load(entries: Map<K, V>) {
        _initializing = true
        spine = HashMap()
        data = HashMap()
        for (entry in entries) { register0(entry.toPair()) }
        _initializing = false
    }

    private fun register0(entry: Pair<K, V>){
        val key = entry.first.identifier
        plugin.logger.debug("Adding datum '$key' to storage")
        spine[key] = entry.first
        data[entry.first] = entry.second
    }

    fun register(entry: Pair<K, V>){
        if (_initializing) return
        if (!allowPostRegister) throw UnsupportedOperationException("This storage does not allow registering outside of initialization")
        register0(entry)
    }

    fun getById(id: IdKey): V? = data[spine[id]]

    fun getByIdOrThrow(
        id: IdKey,
        f: () -> Throwable = { DungeonExceptions.ComponentNotFound.consume(id) }
    ): V = getById(id) ?: throw f()

    override fun get(key: K): V? = data[key]

    fun getOrThrow(
        key: K,
        f: () -> Throwable = { DungeonExceptions.ComponentNotFound.consume(key.identifier) }
    ): V = get(key) ?: throw f()

    override val entries: Set<Map.Entry<K, V>> get() = data.entries
    override val keys: Set<K> get() = data.keys
    override val size: Int get() = spine.size
    override val values: Collection<V> get() = data.values
    override fun isEmpty(): Boolean = spine.isEmpty()
    override fun containsValue(value: V): Boolean = data.containsValue(value)
    override fun containsKey(key: K): Boolean = data.containsKey(key)
}

