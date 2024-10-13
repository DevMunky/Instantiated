package dev.munky.instantiated.lang

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.data.Storage
import dev.munky.instantiated.data.loader.DataFileLoader
import dev.munky.instantiated.data.loader.DataOperationResult
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.asString
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class LangFileLoader: DataFileLoader("lang.json"), KoinComponent{
    private val mini = MiniMessage.builder().build()

    override fun load0(data: ByteArray): DataOperationResult {
        val root = JsonParser.parseString(String(data))
        //check(rroot.isSuccess) { "Json data failure: ${rroot.exceptionOrNull()!!.message}" }
        //val root = rroot.getOrThrow()
        check(root is JsonObject) { "Root data is not json object" }
        var ret = DataOperationResult.SUCCESS
        val i = root.asMap().iterator()
        val errs = ArrayList<Throwable>()
        val entries = mutableMapOf<IdKey, Component>()
        while (i.hasNext()) {
            val entry = i.next()
            try{
                val key = entry.key
                val id = IdType.TRANSLATABLE with key
                val value = entry.value as? JsonPrimitive
                    ?: throw IllegalStateException("Language key $key does not have a valid value")
                val component = mini.deserialize(value.asString)
                plugin.logger.debug("decoded lang entry '$key' -> $component")
                entries[id] = component
            }catch (t: Throwable){
                ret = DataOperationResult.PARTIAL_SUCCESS
                errs.add(t)
            }
        }
        errs.forEach {
            plugin.logger.warning("Failed to parse lang entry: ${it.message}")
        }
        get<LangStorage>().load(entries)
        return ret
    }

    override fun save0(force: Boolean): DataOperationResult { return DataOperationResult.SUCCESS } // never saves, no runtime changes
}

private fun langEntryNotFound(key: String, vararg objects: Any?): Component  {
    plugin.logger.debug("lang entry '$key' not found")
    var message =
        Component.text("'")
            .append(Component.text(key).color(NamedTextColor.WHITE))
            .append(Component.text("' not found"))
            .color(NamedTextColor.RED)
    if (objects.isNotEmpty()) message = message.append(Component.text(" << ${objects.contentToString()}"))
    return message
}

class LangStorage: Storage<IdKey, Component>(), KoinComponent {

    fun caption(key: String, vararg objects: Any?): Component {
        val id = IdType.TRANSLATABLE with key
        var component = this[id] ?: return langEntryNotFound(key, *objects)
        for ((index, any) in objects.withIndex()) {
            val replacement = any.toString()
            plugin.logger.debug("replacing {$index} with $replacement")
            component = component.replaceText {
                it
                    .matchLiteral("{$index}")
                    .replacement(replacement)
                    .once()
            }
        }
        plugin.logger.debug("Produced '${component.asString}'")
        return component
    }

}

fun caption(key: String, vararg o: Any?): Component = plugin.get<LangStorage>().caption(key, *o)
