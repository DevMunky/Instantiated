package dev.munky.instantiated.dungeon.component

import dev.munky.instantiated.dungeon.component.trait.DungeonMobKillTriggerTrait
import dev.munky.instantiated.dungeon.component.trait.RoomEnterTriggerTrait
import dev.munky.instantiated.dungeon.component.trait.RoomLeaveTriggerTrait
import dev.munky.instantiated.edit.QuestionElement
import java.util.*

class TriggerOnRoomEnterComponent(
    roomEnterTrait: RoomEnterTriggerTrait,
    override val uuid: UUID = UUID.randomUUID()
) : DungeonComponent("on-room-enter", listOf(roomEnterTrait)){

    override val question: QuestionElement = QuestionElement.ListOf(
        "room enter",
        question<RoomEnterTriggerTrait> {
            val new = TriggerOnRoomEnterComponent(it, uuid)
            replaceCompInStorage(this, new)
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
            val new = TriggerOnRoomLeaveComponent(it, uuid)
            replaceCompInStorage(this, new)
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
            val new = TriggerOnDungeonMobKillComponent(it, uuid)
            replaceCompInStorage(this, new)
        }
    )
}