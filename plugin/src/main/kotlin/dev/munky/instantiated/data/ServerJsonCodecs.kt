package dev.munky.instantiated.data

import com.google.gson.JsonPrimitive
import dev.munky.instantiated.common.serialization.JsonCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.block.BlockType
import org.bukkit.inventory.ItemType

data class HolderOfNullable<T: Any?>(
    val value: T?,
)

@Suppress("UnstableApiUsage")
object ServerJsonCodecs{
    val ITEM_TYPE = JsonCodec.of(
        ItemType::class,
        {
            JsonPrimitive(it.key.key)
        },
        {
            check(it is JsonPrimitive) { "Item type is not string, it is ${it::class.simpleName}" }
            val string = it.asString
            val key = NamespacedKey.minecraft(string)
            Registry.ITEM.get(key) ?: throw IllegalStateException("Item $string does not exist")
        }
    )
    val NULLABLE_ITEM_TYPE = JsonCodec.of(
        HolderOfNullable::class,
        {
            val t = it.value as ItemType?
            JsonPrimitive(t?.key?.key ?: "null")
        },
        {
            check(it is JsonPrimitive) { "Item type is not string, it is ${it::class.simpleName}" }
            val string = it.asString
            val key = NamespacedKey.minecraft(string)
            HolderOfNullable(Registry.ITEM.get(key))
        }
    ) as JsonCodec<HolderOfNullable<ItemType>>
    val BLOCK_TYPE = JsonCodec.of(
        BlockType::class,
        {
            JsonPrimitive(it.key.key)
        },
        {
            check(it is JsonPrimitive) { "Block type is not string, it is ${it::class.simpleName}" }
            val string = it.asString
            val key = NamespacedKey.minecraft(string)
            Registry.BLOCK.get(key) ?: throw IllegalStateException("Block $string does not exist")
        }
    )
    val COMPONENT = JsonCodec.of(
        Component::class,
        { GsonComponentSerializer.gson().serializeToTree(it) },
        { GsonComponentSerializer.gson().deserializeFromTree(it) }
    )
}