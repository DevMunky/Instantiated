package dev.munky.instantiated.dungeon.mob

import dev.munky.instantiated.common.structs.IdKey
import org.bukkit.entity.LivingEntity
import java.util.*
import kotlin.collections.set

/**
 * Map for organizing dungeon mobs. The root map has keys of identifiers for each room, and the value is an inner weak map with keys of Living Entities and values of the corresponding Dungeon mob.
 * This map is weak, meaning that the Garbage Collector's actions take precedence over all. If a living entity is marked removed and the only references left are weak references,
 * that living entity is cleaned up, and the memory reclaimed. Any entries affected by this in the inner weak map are removed.
 */
class Id2WeakDungeonMobMap : MutableMap<IdKey, WeakHashMap<LivingEntity, DungeonMob>> {
    private val _spine : MutableMap<IdKey, WeakHashMap<LivingEntity, DungeonMob>> = mutableMapOf()
    override val keys: MutableSet<IdKey> get() = _spine.keys
    override val values: MutableCollection<WeakHashMap<LivingEntity, DungeonMob>> get() = _spine.values
    override val size: Int get() = _spine.size
    override val entries: MutableSet<MutableMap.MutableEntry<IdKey, WeakHashMap<LivingEntity, DungeonMob>>> get() = _spine.entries
    override fun clear() {
        _spine.clear()
    }
    override fun put(
        key: IdKey,
        value: WeakHashMap<LivingEntity, DungeonMob>
    ): WeakHashMap<LivingEntity, DungeonMob>? {
        val previous = _spine[key]
        _spine[key] = value
        return previous
    }
    fun put(
        key: IdKey,
        value: Pair<LivingEntity, DungeonMob>
    ): WeakHashMap<LivingEntity, DungeonMob> {
        val previous = _spine[key] ?: WeakHashMap()
        previous[value.first] = value.second
        _spine[key] = previous
        return previous
    }
    override fun putAll(from: Map<out IdKey, WeakHashMap<LivingEntity, DungeonMob>>) {
        _spine.putAll(from)
    }
    override fun remove(key: IdKey): WeakHashMap<LivingEntity, DungeonMob>? {
        val previous = _spine[key]
        _spine.remove(key)
        return previous
    }
    override fun containsKey(key: IdKey): Boolean {
        return _spine.contains(key)
    }
    override fun containsValue(value: WeakHashMap<LivingEntity, DungeonMob>): Boolean {
        return _spine.containsValue(value)
    }
    override fun get(key: IdKey): WeakHashMap<LivingEntity, DungeonMob> {
        val map = _spine[key] ?: WeakHashMap()
        _spine[key] = map
        return map
    }
    override fun isEmpty(): Boolean {
        return _spine.isEmpty()
    }
    override fun toString(): String {
        return _spine.toString()
    }
}