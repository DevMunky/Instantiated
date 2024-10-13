package dev.munky.instantiated.edit

import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.data.loader.ComponentStorage
import dev.munky.instantiated.data.loader.FormatLoader
import dev.munky.instantiated.dungeon.component.trait.LocatableTrait
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomFormat
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.dungeon.sstatic.StaticInstance
import dev.munky.instantiated.dungeon.sstatic.StaticRoomFormat
import dev.munky.instantiated.dungeon.sstatic.StaticRoomInstance
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.ComponentUtil
import dev.munky.instantiated.util.asComponent
import dev.munky.instantiated.util.send
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.joml.Vector3f
import org.joml.Vector3i
import org.koin.core.component.get
import java.io.File

object BuiltInQuestions{

    fun room(room: RoomInstance): Component = QuestionElement.Header(
        QuestionElement.Label("Configuring '${room.identifier.key}'"),
        QuestionElement.ListOf("Key Drop Mode",
            QuestionElement.Clickable(RoomFormat.KeyDropMode.ROOM_MOBS_CLEAR.name){
                room.format.keyDropMode = RoomFormat.KeyDropMode.ROOM_MOBS_CLEAR
                it.sendMessage("<green>Set key drop mode to ROOM_MOBS_CLEAR".asComponent)
            },
            QuestionElement.Clickable(RoomFormat.KeyDropMode.MARKED_ROOM_MOB_KILL.name){
                room.format.keyDropMode = RoomFormat.KeyDropMode.MARKED_ROOM_MOB_KILL
                it.sendMessage("<green>Set key drop mode to MARKED_ROOM_MOB_KILL".asComponent)
            }
        ),
        QuestionElement.Clickable("Key-item-type"){
            val type = PromptFactory.promptRegistry(RegistryKey.ITEM, it) ?: return@Clickable
            room.format.keyMaterial = type.asMaterial()!!
            it.sendMessage(ComponentUtil.toComponent("<green>Set key material of room to ${type.key.key}"))
        }
    ).build()

    fun instance(instance: Instance): Component = QuestionElement.Header(
        QuestionElement.Label("Configuring '${instance.identifier.key}'"),
        QuestionElement.Clickable("Schematic"){
            it.sendMessage(ComponentUtil.toComponent("<blue>Enter the schematic file name"))
            val schem = PromptFactory.promptString("schematic name", it)
            if (instance !is StaticInstance) return@Clickable
            val file = handleSchematicCallback(instance, schem)
            file.onFailure {t->
                it.sendMessage("Error: ${t.message}".asComponent)
                return@Clickable
            }
            instance.format.schematic = file.getOrThrow()
        },
        QuestionElement.ListOf("Rooms",
            QuestionElement.Clickable("Add room"){
                val name = PromptFactory.promptString("room name", it)
                // maybe make a single block select an edit state
                val pos1arr = PromptFactory.promptFloats(3, it) ?: return@Clickable
                val pos2arr = PromptFactory.promptFloats(3, it) ?: return@Clickable
                val box = Box(Vector3f(pos1arr[0],pos1arr[1],pos1arr[2]), Vector3f(pos2arr[0],pos2arr[1],pos2arr[2]))
                val room = StaticRoomInstance(
                    instance as StaticInstance,
                    StaticRoomFormat(
                        IdType.ROOM with name,
                        instance.format,
                        Vector3i(0),
                        box,
                        RoomFormat.KeyDropMode.MARKED_ROOM_MOB_KILL,
                        Material.STONE
                    )
                )
                instance.rooms[room.identifier] = room
                it.sendMessage("Added room '$name' to dungeon, with some default values".asComponent)
            },
            QuestionElement.ListOf("Remove room",
                instance.rooms.map { room ->
                    QuestionElement.Clickable(room.key.key){
                        instance.rooms.remove(room.key)
                        it.sendMessage("Removed room ${room.key.key}".asComponent)
                    }
                }.toList()
            )
        )
    ).build()

    private fun handleSchematicCallback(dungeon: Instance, schem: String): Result<File> {
        var schematicFile = FormatLoader.REGISTERED_SCHEMATICS[schem]
        if (schematicFile == null) // trying again, this time appending .schem
            schematicFile = FormatLoader.REGISTERED_SCHEMATICS["$schem.schem"]
        return if (schematicFile==null){
            Result.failure(IllegalStateException("No schematic named '$schem' found"))
        } else if (dungeon !is StaticInstance) {
            Result.failure(IllegalStateException("This dungeon is not a static dungeon"))
        } else Result.success(schematicFile)
    }

    fun roomComponents(room: RoomInstance): QuestionElement = QuestionElement.Header(
        run {
            val list = mutableListOf<QuestionElement>(QuestionElement.Label("<green>All components without a location in room '${room.identifier.key}'"))
            val comps = plugin.get<ComponentStorage>()[room.format] ?: return QuestionElement.Label("No components in room '${room.identifier.key}'")
            for (c in comps){
                if (!c.hasTrait<LocatableTrait<*>>()) list.add(QuestionElement.Clickable("Component ${c::class.simpleName}"){
                    c.question.build().send(it)
                })
            }
            list
        }
    )
}