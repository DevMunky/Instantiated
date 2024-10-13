package dev.munky.instantiated.dungeon.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import dev.munky.instantiated.common.serialization.CommonJsonCodecs
import dev.munky.instantiated.common.serialization.JsonCodec
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.data.ServerJsonCodecs.BLOCK_TYPE
import dev.munky.instantiated.data.loader.MobStorage
import dev.munky.instantiated.dungeon.component.trait.*
import dev.munky.instantiated.dungeon.component.trait.LocatableTrait.*
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.CodecHolder
import org.koin.core.component.get
import java.util.*

object ComponentCodecs: CodecHolder({"Component '$it' has no registered codec"}) {
    val CUSTOM = JsonCodec.of(
        CustomComponent::class,
        {
            val json = JsonObject()
            for (t in it.`$traits`){
                val codec = TraitCodecs.get(t::class)
                json.add(codec.clazz.simpleName, codec.encode(t))
            }
            json
        },
        {
            check(it is JsonObject) {"Not a json object"}
            val traits = ArrayList<Trait>()
            val uuidE = it.get("uuid")
            check(uuidE is JsonPrimitive) {"Not a json primitive"}
            val uuid = UUID.fromString(uuidE.asString)
            for (t in it.asMap()){
                check(t.value is JsonObject) {"Not a json object"}
                val type = t.key
                val codec = TraitCodecs.get<Trait>(type)
                val trait = codec.decode(t.value)
                traits.add(trait)
            }
            CustomComponent(traits, uuid)
        }
    )
    val SPAWNER = JsonCodec.composite(
        SpawnerComponent::class,
        "location-and-direction-trait", TraitCodecs.LOCATION_AND_DIRECTION, SpawnerComponent::getTrait,
        "spawner-trait", TraitCodecs.SPAWNER, SpawnerComponent::getTrait,
        "uuid", CommonJsonCodecs.UUID, SpawnerComponent::uuid,
        ::SpawnerComponent
    )
    val TRIGGER_ROOM_ENTER = JsonCodec.composite(
        TriggerOnRoomEnterComponent::class,
        "trigger-room-enter", TraitCodecs.ROOM_ENTER_TRIGGER, TriggerOnRoomEnterComponent::getTrait,
        "uuid", CommonJsonCodecs.UUID, TriggerOnRoomEnterComponent::uuid,
        ::TriggerOnRoomEnterComponent
    )
    val TRIGGER_ROOM_LEAVE = JsonCodec.composite(
        TriggerOnRoomLeaveComponent::class,
        "trigger-room-leave", TraitCodecs.ROOM_LEAVE_TRIGGER, TriggerOnRoomLeaveComponent::getTrait,
        "uuid", CommonJsonCodecs.UUID, TriggerOnRoomLeaveComponent::uuid,
        ::TriggerOnRoomLeaveComponent
    )
    val DOOR = JsonCodec.composite(
        DoorComponent::class,
        "set-blocks-trait", TraitCodecs.SET_BLOCKS, DoorComponent::getTrait,
        "uuid", CommonJsonCodecs.UUID, DoorComponent::uuid,
        ::DoorComponent
    )
    val TRIGGER_DUNGEON_MOB_KILL = JsonCodec.composite(
        TriggerOnDungeonMobKillComponent::class,
        "trigger-dungeon-mob-kill", TraitCodecs.DUNGEON_MOB_KILL_TRIGGER, TriggerOnDungeonMobKillComponent::getTrait,
        "uuid", CommonJsonCodecs.UUID, TriggerOnDungeonMobKillComponent::uuid,
        ::TriggerOnDungeonMobKillComponent
    )
}

object TraitCodecs: CodecHolder({"Trait '$it' has no registered codec"}){ // literally just for the custom component
    val LOCATION = JsonCodec.composite(
        LocationTrait::class,
        "vector", CommonJsonCodecs.VECTOR3F, LocationTrait::vector,
        ::LocationTrait
    )
    val LOCATION_AND_DIRECTION = JsonCodec.composite(
        LocationAndDirectionTrait::class,
        "vector", CommonJsonCodecs.VECTOR3F, LocationAndDirectionTrait::vector,
        "yaw", CommonJsonCodecs.FLOAT, LocationAndDirectionTrait::yaw,
        "pitch", CommonJsonCodecs.FLOAT, LocationAndDirectionTrait::pitch,
        ::LocationAndDirectionTrait
    )
    val SPAWNER = JsonCodec.composite(
        SpawnerTrait::class,
        "mob", CommonJsonCodecs.STRING, { it.mob.identifier.key },
        "radius", CommonJsonCodecs.FLOAT, SpawnerTrait::radius,
        "quantity-lower", CommonJsonCodecs.INT, { it.quantity.first },
        "quantity-upper", CommonJsonCodecs.INT, { it.quantity.last },
        { mobId, radius, lower, higher ->
            val id = IdType.MOB with mobId
            val mob = plugin.get<MobStorage>()[id] ?: throw DungeonExceptions.ComponentNotFound.consume(id)
            SpawnerTrait(mob, lower..higher, radius)
        }
    )
    val ROOM_ENTER_TRIGGER = JsonCodec.composite(
        RoomEnterTriggerTrait::class,
        "uses", CommonJsonCodecs.INT, EventTriggerTrait<*>::uses,
        "targets", CommonJsonCodecs.UUID_LIST, EventTriggerTrait<*>::targets,
        ::RoomEnterTriggerTrait
    )
    val ROOM_LEAVE_TRIGGER = JsonCodec.composite(
        RoomLeaveTriggerTrait::class,
        "uses", CommonJsonCodecs.INT, EventTriggerTrait<*>::uses,
        "targets", CommonJsonCodecs.UUID_LIST, EventTriggerTrait<*>::targets,
        ::RoomLeaveTriggerTrait
    )
    val SET_BLOCKS = JsonCodec.composite(
        SetBlocksTrait::class,
        "open", BLOCK_TYPE, SetBlocksTrait::openType,
        "close", BLOCK_TYPE, SetBlocksTrait::closeType,
        "change", CommonJsonCodecs.ENUM<SetBlocksTrait.ChangeFunction>(), SetBlocksTrait::changeFunction,
        "blocks", CommonJsonCodecs.MUTABLE_PACKED_VECTOR3I_COLLECTION, { it.blocks },
        ::SetBlocksTrait
    )
    val DUNGEON_MOB_KILL_TRIGGER = JsonCodec.composite(
        DungeonMobKillTriggerTrait::class,
        "mob", CommonJsonCodecs.STRING, { it.mob.key },
        "uses", CommonJsonCodecs.INT, EventTriggerTrait<*>::uses,
        "targets", CommonJsonCodecs.UUID_LIST, EventTriggerTrait<*>::targets,
        { id, uses, targets ->
            DungeonMobKillTriggerTrait(IdType.MOB with id, uses, targets)
        }
    )
}