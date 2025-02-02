package dev.munky.instantiated.data.loader

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.Storage
import dev.munky.instantiated.dungeon.component.ComponentCodecs
import dev.munky.instantiated.dungeon.component.DungeonComponent
import dev.munky.instantiated.dungeon.interfaces.RoomFormat
import dev.munky.instantiated.exception.DungeonExceptions.Companion.DataSyntax
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.*

/**
 * [data-driven help](https://stackoverflow.com/questions/1065584/what-is-data-driven-programming)
 */
class ComponentLoader: DataFileLoader("components.json"), KoinComponent {
    private val components get() = get<ComponentStorage>()
    private val formats get() = get<FormatStorage>()

    override fun load0(data: ByteArray): DataOperationResult {
        val rootObject = checkType<JsonObject>(JsonParser.parseString(String(data, Charsets.UTF_8)), "root json")
        val storageMap = mutableMapOf<RoomFormat, List<DungeonComponent>>()
        var result = DataOperationResult.SUCCESS
        rootObject.asMap().forEach { formatEntry ->
            try{
                val formatObject = checkType<JsonObject>(formatEntry.value, "format json")
                val format = formats[IdType.DUNGEON with formatEntry.key]
                    ?: throw DataSyntax.consume("id '${formatEntry.key}' does not correspond to a loaded dungeon format. Formats: ${formats.keys}")
                formatObject.asMap().forEach { roomEntry ->
                    try {
                        val roomArray = checkType<JsonArray>(roomEntry.value, "room json")
                        val compList = mutableListOf<DungeonComponent>()
                        val roomFormat = format.rooms[IdType.ROOM with roomEntry.key]
                            ?: throw DataSyntax.consume("id '${roomEntry.key}' does not correspond to a loaded room format'")
                        roomArray.forEach { componentEntry ->
                            var label = "not yet loaded"
                            try {
                                val componentObject = checkType<JsonObject>(componentEntry, "component json")
                                val nameE = componentObject.get("type")
                                check(nameE is JsonPrimitive) { "Component type is not a primitive, it is ${nameE::class.simpleName}" }
                                val codecName = nameE.asString
                                label = codecName
                                val codec = ComponentCodecs.get<DungeonComponent>(codecName)
                                val componentData = componentObject.get("data")
                                val component = codec.decode(componentData)
                                compList.add(component)
                            } catch (t: Throwable) {
                                result = DataOperationResult.PARTIAL_SUCCESS
                                t.log("error while loading component '$label'")
                            }
                        }
                        storageMap[roomFormat] = compList
                    } catch (t: Throwable) {
                        result = DataOperationResult.PARTIAL_SUCCESS
                        t.log("error while loading room '${roomEntry.key}'s components")
                    }
                }
            }catch (t: Throwable){
                result = DataOperationResult.PARTIAL_SUCCESS
                t.log("error while loading format '${formatEntry.key}'s components")
            }
        }
        components.load(storageMap)
        return result
    }

    override fun save0(force: Boolean): DataOperationResult {
        val rootObject = JsonObject()
        formats.values.forEach { format ->
            val formatObject = JsonObject()
            format.rooms.values.forEach room@{ room ->
                val roomArray = JsonArray()
                val comps = components[room] ?: return@room
                comps.forEach{ comp ->
                    val compObject = JsonObject()
                    val codec = ComponentCodecs.get(comp::class)
                    compObject.addProperty("type", codec.clazz.simpleName)
                    compObject.add("data", codec.encode(comp))
                    roomArray.add(compObject)
                }
                formatObject.add(room.identifier.key, roomArray)
            }
            rootObject.add(format.identifier.key, formatObject)
        }
        file.bufferedWriter(Charsets.UTF_8,8192).use {
            GSON.toJson(rootObject,it)
        }
        return DataOperationResult.SUCCESS
    }
}

class ComponentStorage: Storage<RoomFormat, List<DungeonComponent>>(true){
    /**
     * Does a linear search to find a [DungeonComponent] with a matching uuid.
     */
    fun getByUUID(uuid: UUID): DungeonComponent? =
        values.flatten().firstOrNull {
            it.uuid == uuid
        }

    fun getRoomByComponent(component: DungeonComponent): RoomFormat =
        entries.firstOrNull { it.value.contains(component) }?.key ?: throw IllegalStateException("How does a component not have an associated room")
}