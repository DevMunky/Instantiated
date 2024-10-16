package dev.munky.instantiated.dungeon.component

import dev.munky.instantiated.dungeon.component.trait.*
import dev.munky.instantiated.edit.QuestionElement
import java.util.*

class TriggerOnRoomEnterComponent(
    roomEnterTrait: RoomEnterTriggerTrait,
    override val uuid: UUID = UUID.randomUUID()
) : DungeonComponent("on-room-enter", listOf(roomEnterTrait)){

    override val question: QuestionElement = QuestionElement.ListOf(
        "room enter",
        question<RoomEnterTriggerTrait> {
            TriggerOnRoomEnterComponent(it, uuid)
        }
    )
}

class TriggerOnRoomLeaveComponent(
    roomLeaveTrait: RoomLeaveTriggerTrait,
    override val uuid: UUID = UUID.randomUUID()
) : DungeonComponent("on-room-enter", listOf(roomLeaveTrait)){

    override val question: QuestionElement = QuestionElement.ListOf(
        "room leave",
        question<RoomLeaveTriggerTrait> {
            TriggerOnRoomLeaveComponent(it, uuid)
        }
    )
}

class TriggerOnDungeonMobKillComponent(
    deathTrait: DungeonMobKillTriggerTrait,
    override val uuid: UUID = UUID.randomUUID()
): DungeonComponent("on-dungeon-mob-death", setOf(deathTrait)){

    override val question: QuestionElement = QuestionElement.ListOf(
        "mob kill",
        question<DungeonMobKillTriggerTrait> {
            TriggerOnDungeonMobKillComponent(it, uuid)
        }
    )
}

class TriggerOnBlockInteractComponent(
    interactTrait: InteractWithBlockTriggerTrait,
    location: LocatableTrait.LocationTrait,
    override val uuid: UUID
): DungeonComponent("on-block-interact", listOf(interactTrait)){
    override val question: QuestionElement = QuestionElement.ListOf(
        "interact with block",
        question<InteractWithBlockTriggerTrait> {
            TriggerOnBlockInteractComponent(it, getTrait(), uuid)
        },
        question<LocatableTrait.LocationTrait> {
            TriggerOnBlockInteractComponent(getTrait(), it, uuid)
        }
    )
}