package dev.munky.instantiated.dungeon.component

import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.util.copy
import dev.munky.instantiated.dungeon.component.trait.LocatableTrait
import dev.munky.instantiated.dungeon.component.trait.SetBlocksTrait
import dev.munky.instantiated.dungeon.component.trait.SpawnerTrait
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.edit.AbstractRenderer
import dev.munky.instantiated.edit.QuestionElement
import dev.munky.instantiated.util.toVector3f
import org.bukkit.entity.Player
import org.joml.Vector2f
import org.joml.Vector3f
import java.util.*

class SpawnerComponent(
    locationTrait: LocatableTrait.LocationAndDirectionTrait,
    spawnerTrait: SpawnerTrait,
    override val uuid: UUID = UUID.randomUUID()
): DungeonComponent("spawner", listOf(locationTrait,spawnerTrait)){

    override fun invoke0(room: RoomInstance){
        getTrait<SpawnerTrait>().invoke(room, this)
    }

    override val question = QuestionElement.ListOf(
        "Spawner Component",
        question<LocatableTrait> {
            val new = SpawnerComponent(it as LocatableTrait.LocationAndDirectionTrait, getTrait(), uuid)
            replaceCompInStorage(this, new)
        },
        question<SpawnerTrait> {
            val new = SpawnerComponent(getTrait(), it, uuid)
            replaceCompInStorage(this, new)
        }
    )

    override fun render0(renderer: AbstractRenderer, room: RoomInstance, editor: Player) {
        val location = getTrait<LocatableTrait>().vector.copy.add(room.realVector.toVector3f)
        renderer.renderEllipse(
            room.realVector.world,
            location,
            Vector2f(getTrait<SpawnerTrait>().radius, getTrait<SpawnerTrait>().radius),
            componentData,
            editor,
            1f
        )
    }
}

class DoorComponent(
    setBlock: SetBlocksTrait,
    override val uuid: UUID
): DungeonComponent("door", listOf(setBlock)), NeedsInitialized{

    override val question = QuestionElement.ListOf(
        "Door Component",
        question<SetBlocksTrait> {
            val new = DoorComponent(it, uuid)
            replaceCompInStorage(this, new)
        }
    )

    val isOpen get() = getTrait<SetBlocksTrait>().isOpen

    fun set(open: Boolean, room: RoomInstance) {
        if (isOpen != open) invoke(room)
    }

    override fun initialize(room: RoomInstance) = set(true, room)

    override fun invoke0(room: RoomInstance) {
        getTrait<SetBlocksTrait>().invoke(room, this)
    }

    override fun render0(renderer: AbstractRenderer, room: RoomInstance, editor: Player) {
        val location = room.realVector.toVector3f
        for (block in getTrait<SetBlocksTrait>().blocks){
            val bL = location.copy.add(Vector3f(block))
            val box = Box(bL, bL.copy.add(Vector3f(1f)))
            renderer.renderBox(
                room.realVector.world,
                box,
                componentData,
                editor
            )
        }
    }
}