package dev.munky.instantiated.data.loader

import com.google.gson.*
import dev.munky.instantiated.common.serialization.CommonJsonCodecs
import dev.munky.instantiated.common.serialization.JsonCodec
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.Storage
import dev.munky.instantiated.dungeon.mob.BossDungeonMob
import dev.munky.instantiated.dungeon.mob.DungeonMob
import dev.munky.instantiated.dungeon.mob.SimpleDungeonMob
import dev.munky.instantiated.exception.DungeonExceptions.Companion.DataSyntax
import dev.munky.instantiated.util.CodecHolder
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.reflect.KClass

class MobLoader : DataFileLoader("mobs.json"), KoinComponent {

    override fun load0(data: ByteArray): DataOperationResult {
        val rootArray = JsonParser.parseString(String(data, Charsets.UTF_8))
        if (rootArray !is JsonArray) throw DataSyntax.consume("root json is a '${rootArray.javaClass.simpleName}', not a JsonArray")
        val mobs = mutableMapOf<IdKey, DungeonMob>()
        rootArray.forEach { entryElement ->
            var type = "unknown type"
            var id = "unknown id"
            try{
                val entryData = checkType<JsonObject>(entryElement, "a mob's json data")
                val codecName = checkType<JsonPrimitive>(
                    entryData.get("type") ?: throw DataSyntax.consume("required key 'type' does not exist"),
                    "a mob's codec type"
                ).asString
                type = codecName
                val codec = MobCodecs.get<DungeonMob>(codecName)
                val mobDataJson = entryData.get("data") ?: throw DataSyntax.consume("key 'data' does not exist")
                val mobData = checkType<JsonObject>(
                    mobDataJson,
                    "a mob's json data"
                )
                id = checkType<JsonPrimitive>(mobData.get("id"), "a mob's id").asString
                val mob = codec.decode(mobData)
                mobs += mob.identifier to mob
            }catch (t: Throwable){
                t.log("Error loading a '$type' named '$id'")
            }
        }
        get<MobStorage>().load(mobs)
        return DataOperationResult.SUCCESS
    }

    override fun save0(force: Boolean): DataOperationResult {
        val rootArray = JsonArray()
        val mobs = ArrayList(get<MobStorage>().values)
        mobs.forEach { mob ->
            val entryData = JsonObject()
            val codec = MobCodecs.get(mob::class)
            entryData.addProperty("type", codec.clazz.simpleName!!)
            val mobData = codec.encode(mob)
            entryData.add("data", mobData)
            rootArray.add(entryData)
        }
        file.bufferedWriter(Charsets.UTF_8,8192).use {
            GSON.toJson(rootArray, it)
        }
        return DataOperationResult.SUCCESS
    }
}

class MobStorage: Storage<IdKey, DungeonMob>()

object MobCodecs: CodecHolder({"Mob codec '$it' not found. Call MobCodecs.INSTANCE.registerCodec(KClass,(T)->JsonElement,JsonElement->(T)) to register one"}) {

    @Suppress("unused")
    @JvmStatic
    fun <T: DungeonMob> registerCodec(clazz: KClass<T>, encoder: (T) -> JsonElement, decoder: (JsonElement) -> T) {
        this.`&spine`.add(JsonCodec.of(clazz, encoder, decoder))
    }

    val SIMPLE = JsonCodec.composite(
        SimpleDungeonMob::class,
        "id", CommonJsonCodecs.STRING, { it.identifier.key },
        "marked", CommonJsonCodecs.BOOL, DungeonMob::isMarked,
        "data", CommonJsonCodecs.STRING2STRING, DungeonMob::custom,
        {id,marked,data->
            print(data)
            SimpleDungeonMob(
                IdType.MOB.with(id),
                marked,
                data.toMutableMap()
            )
        }
    )

    val BOSS = JsonCodec.composite(
        BossDungeonMob::class,
        "id", CommonJsonCodecs.STRING, { it.identifier.key },
        "data", CommonJsonCodecs.STRING2STRING, DungeonMob::custom,
        {id,data->
            BossDungeonMob(
                IdType.MOB.with(id),
                data.toMutableMap()
            )
        }
    )
}