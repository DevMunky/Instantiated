package dev.munky.instantiated.data.loader

import com.google.common.collect.ImmutableMap
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dev.munky.instantiated.common.serialization.CommonJsonCodecs
import dev.munky.instantiated.common.serialization.JsonCodec
import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.ServerJsonCodecs
import dev.munky.instantiated.data.Storage
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.dungeon.interfaces.RoomFormat
import dev.munky.instantiated.dungeon.sstatic.StaticFormat
import dev.munky.instantiated.dungeon.sstatic.StaticRoomFormat
import dev.munky.instantiated.edit.EditModeHandler
import dev.munky.instantiated.event.DungeonFormatLoadEvent
import dev.munky.instantiated.event.DungeonTotalCacheEvent
import dev.munky.instantiated.exception.DungeonExceptions.Companion.DataSyntax
import dev.munky.instantiated.plugin
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemType
import org.joml.Vector3i
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.nio.file.Files
import kotlin.math.min
import kotlin.streams.asSequence

class FormatLoader : DataFileLoader("dungeons.json"), KoinComponent {
    companion object{

        private val SCHEMATICS_FOLDER = File(
            Bukkit.getPluginsFolder().toString() +
                    File.separator + "FastAsyncWorldEdit"
                    + File.separator + "schematics"
        )

        var REGISTERED_SCHEMATICS : ImmutableMap<String,File> = ImmutableMap.of()
            set(value) {
                val stackWalker: StackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                val clazz = stackWalker.walk { frames ->
                    frames.toList().firstOrNull { frame ->
                        val trimmedName = frame.className.split(".").last()
                        trimmedName != "ExceptionFactory"
                                && trimmedName != "DirectConstructorHandleAccessor"
                                && trimmedName != "Constructor"
                    }?.declaringClass ?: throw IllegalStateException("Not enough frames, dont set schematics too early")
                }
                if (clazz != FormatLoader::class.java && clazz != Companion::class.java) throw IllegalAccessException("Illegal registry edit")
                field = value
            }

        private fun rawGetSchematicsOnFile(): Map<String, File> = Files.walk(SCHEMATICS_FOLDER.toPath()).parallel()
            .use { stream ->
                stream
                    .filter { it.toString().endsWith(".schem") || it.toString().endsWith(".schematic") }
                    .filter { it != null }
                    .filter { Files.isRegularFile(it) }
                    .asSequence()
                    .associateBy { it.toString().drop(SCHEMATICS_FOLDER.path.length + 1) } // trim the directory plus the extra slash at the end
                    .mapValues { File(it.value.toString()) }
                    .filter { plugin.logger.debug("Schematic '" + it.key + "' registered"); true }
            }
    }

    override fun load0(data: ByteArray): DataOperationResult {
        // this loads all schematics in folder to a map to be retrieved later
        REGISTERED_SCHEMATICS = ImmutableMap.copyOf(rawGetSchematicsOnFile())
        // this is a list to temporarily store formats until registering them at the end
        val loadedFormats = mutableMapOf<IdKey,Format>()
        // parse the json, now if there is an error we can use the cache and rewrite whatever data was in there
        val jsonData = JsonParser.parseString(String(data, Charsets.UTF_8))
        val rootObject = checkType<JsonObject>(jsonData.asJsonObject, "root object")
        val dungeonArray = checkType<JsonArray>(rootObject.get("dungeons"),"dungeon array")
        var result = DataOperationResult.SUCCESS
        for (dungeonElement in dungeonArray) {
            try{
                if (!dungeonElement.isJsonObject) throw DataSyntax.consume("dungeon json is not an object")
                val dungeonObject = dungeonElement.asJsonObject
                val format = getDungeonFromJson(dungeonObject)
                val event = DungeonFormatLoadEvent(format, dungeonObject)
                event.callEvent()

                if (event.isCancelled) {
                    plugin.logger.warning("A load event was cancelled, therefore '${format.identifier}' will try to not load.")
                    continue
                }

                plugin.logger.debug("fully loaded '${format.identifier}'")
                loadedFormats += format.identifier to format
            }catch (e: Exception){
                e.log("Syntax error")
                result = DataOperationResult.PARTIAL_SUCCESS
            }
        }
        get<FormatStorage>().load(loadedFormats)
        plugin.logger.info("Registered dungeons: ${loadedFormats.map{ it.key.key }.toString().replace("[","").replace("]","")}")
        DungeonTotalCacheEvent().callEvent()
        return result
    }

    override fun save0(force: Boolean): DataOperationResult {
        val rootObject = JsonObject()
        val dungeonArray = JsonArray()
        var result = DataOperationResult.SUCCESS
        for (dungeon in get<FormatStorage>().values){
            try {
                val format = when (dungeon){
                    is StaticFormat -> FormatCodecs.STATIC_FORMAT.encode(dungeon).asJsonObject
                    else -> throw DataSyntax.consume("unhandled dungeon format class '${this::class.simpleName}'")
                }
                dungeonArray.add(format)
            } catch (e: Throwable) {
                e.log("Saving dungeon error")
                result = DataOperationResult.PARTIAL_SUCCESS
            }
        }
        rootObject.add("dungeons",dungeonArray)
        file.bufferedWriter(Charsets.UTF_8,8192).use {
            GSON.toJson(rootObject,it)
        }
        plugin.logger.info("Saved all dungeons")
        get<EditModeHandler>().unsavedChanges = false
        return result
    }

    private fun getDungeonFromJson(json: JsonObject): Format{
        var identifier = "Not yet loaded"
        var type = "unknown"
        try{
            val idJson = json.get("id") ?: throw DataSyntax.consume("key 'id' is null")
            identifier = CommonJsonCodecs.STRING.decode(idJson)
            val typeElement = json.get("type")
            check(typeElement is JsonPrimitive) { "type is not primitive" }
            type = typeElement.asString
            return when (type){
                "static" -> FormatCodecs.STATIC_FORMAT.decode(json)
                else -> throw DataSyntax.consume("unhandled dungeon type '$type'")
            }
        }catch (e:Throwable){
            throw DataSyntax.consume("$type dungeon '$identifier'",e)
        }
    }
}

class FormatStorage: Storage<IdKey, Format>(), KoinComponent

object FormatCodecs {
    val STATIC_ROOM: (StaticFormat) -> JsonCodec<StaticRoomFormat> = { parent->
        JsonCodec.composite(
            StaticRoomFormat::class,
            "id",            CommonJsonCodecs.STRING,                         { it.identifier.key },
            "origin",        CommonJsonCodecs.VECTOR3I,                       { Vector3i(it.origin) },
            "box",           CommonJsonCodecs.BOX,                            StaticRoomFormat::box,
            "key-drop-mode", CommonJsonCodecs.ENUM<RoomFormat.KeyDropMode>(), StaticRoomFormat::keyDropMode,
            "key-material",  ServerJsonCodecs.ITEM_TYPE,                      { it.keyMaterial.asItemType()!! },
            { id, origin: Vector3i, box: Box, dropMode, keyMaterial: ItemType ->
                plugin.logger.debug("loading static room '$id'")
                val room = StaticRoomFormat(
                    IdType.ROOM.with(id),
                    parent,
                    origin,
                    box,
                    dropMode,
                    keyMaterial.asMaterial()!!
                )
                room
            }
        )
    }
    val STATIC_FORMAT = JsonCodec.of(
        StaticFormat::class,
        {
            val json = JsonObject()
            json.addProperty("type", "static")
            json.add("id", CommonJsonCodecs.STRING.encode(it.identifier.key))
            json.addProperty("schematic", it.schematic?.name)
            json.add("spawn", CommonJsonCodecs.VECTOR3F.encode(it.spawnVector))
            val roomArray = JsonArray()
            for (room in it.rooms.values){
                roomArray.add(STATIC_ROOM(it).encode(room))
            }
            json.add("rooms",roomArray)
            plugin.logger.debug("Saved dungeon '${it.identifier}'")
            json
        },
        {
            check(it is JsonObject) {"Static format is not a Json Object"}
            val jsonObject = it
            val identifier = CommonJsonCodecs.STRING.decode(jsonObject.get("id"))
            val schematic = CommonJsonCodecs.STRING.decode(jsonObject.get("schematic"))
            val schemFile = FormatLoader.REGISTERED_SCHEMATICS[schematic]
                ?: throw DataSyntax.consume("schematic '$schematic' does not exist")
            val spawnVector = CommonJsonCodecs.VECTOR3F.decode(jsonObject.get("spawn"))
            val format = StaticFormat(IdType.DUNGEON.with(identifier), schemFile, spawnVector)
            val roomArray = jsonObject.get("rooms") as? JsonArray ?: throw DataSyntax.consume("Room element is not an array")
            val rooms = mutableMapOf<IdKey, StaticRoomFormat>()
            val roomCodec = STATIC_ROOM(format)
            for (roomEntry in roomArray) {
                if (!roomEntry.isJsonObject) throw DataSyntax.consume("a room is not a json object (map of values)")
                val dungeonRoom = try {
                    roomCodec.decode(roomEntry.asJsonObject)
                } catch (e: Throwable) {
                    throw DataSyntax.consume("static room", e)
                }
                rooms[dungeonRoom.identifier] = dungeonRoom
            }
            rooms.toSortedMap(alphabeticIdComp)
            format.rooms.putAll(rooms)
            plugin.logger.debug("Loaded dungeon '$identifier'")
            format
        }
    )
    private val alphabeticIdComp: Comparator<IdKey> = Comparator { o1, o2 -> alphabeticStringComparator.compare(o1.key, o2.key)}
}

val alphabeticStringComparator: Comparator<String> = Comparator { o1, o2 ->
    // I did make this myself I'll have you know
    // just googled the offset to be sure
    val minLength = min(o1.length, o2.length)
    var pos = 0
    val array1 = o1.toCharArray()
    val array2 = o2.toCharArray()
    var char1 = array1[pos]
    var char2 = array2[pos]
    while (pos < minLength && char1 == char2) {
        char1 = array1[pos]
        char2 = array2[pos]
        pos++
    }
    if (pos - 1 == minLength) 0
    else (char1.code - char2.code) - LOWERCASE_CHAR_OFFSET
}

private const val LOWERCASE_CHAR_OFFSET = 'a'.code - 1