package dev.munky.instantiated.edit

import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.fromMini
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.persistence.PersistentDataType

// pretty much dont want to make this unless i have to make a gui. hope not
open class ManagedGui(
    val items: HashMap<Int, ManagedItem>,
    val size: Int
){
    val chest by lazy {
        val inv = Bukkit.getServer().createInventory(null, size)
        items.forEach {
            inv.setItem(it.key, it.value)
        }
        inv
    }
    fun open(player: Player) {
        player.openInventory(chest)
    }
}

fun applyManagedItemMeta(item: ItemStack, name: String){
    item.editMeta {
        it.persistentDataContainer.set(
            NamespacedKey(plugin, "managed-item"),
            PersistentDataType.STRING,
            name.replace(" ", "_").lowercase()
        )
        it.displayName(name.fromMini)
    }
}

class ManagedItem(
    mat: ItemType,
    val name: String,
    extra: (ItemStack) -> Unit,
    private val _callback: (InventoryClickEvent) -> Unit
): ItemStack(mat.createItemStack()) {

    init{
        init(name, extra)
    }

    private fun init(name: String, extra: (ItemStack) -> Unit){
        applyManagedItemMeta(this, name)
        extra(this)
    }

    fun callback(event: InventoryClickEvent){
        plugin.logger.debug("managed item callback from ${event.whoClicked}")
        try{
            _callback(event)
        }catch (t: Throwable){
            t.log("Caught throwable in managed item callback for $name")
        }
    }
}