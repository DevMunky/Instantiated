package dev.munky.instantiated.dungeon.component.trait

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.structs.Identifiable
import dev.munky.instantiated.edit.PromptFactory
import dev.munky.instantiated.edit.QuestionElement
import org.joml.Vector3f

/**
 * used to categorize components.
 *
 * the object permanence for traits is simple. Each component that needs a trait get its own instance.
 *
 * `traits are not shared across components`
 *
 * Traits by themselves do **specific and niche things**, like setting a region of blocks or spawning a mob.
 * *Chaining together traits with components* is where the real functionality shines.
 */
abstract class Trait<T: Trait<T>>(
    override val identifier: IdKey,
): Identifiable {
    constructor(
        id: String
    ): this(IdType.TRAIT.with(id))

    abstract fun question(res: EditingTraitHolder<T>): QuestionElement
}

class EditingTraitHolder<T: Trait<T>>(
    initTrait: T,
    val f: (T) -> Unit
){
    var trait: T = initTrait
        set(value) {
            f(value)
            field = value
        }
}

sealed class LocatableTrait(id: String): Trait<LocatableTrait>(id){
    abstract val vector: Vector3f
    abstract val yaw: Float
    abstract val pitch: Float

    class LocationTrait(
        override val vector: Vector3f
    ): LocatableTrait("location"){
        override val yaw: Float = 0f
        override val pitch: Float = 0f
        override fun question(res: EditingTraitHolder<LocatableTrait>): QuestionElement = QuestionElement.ForTrait(
            this,
            QuestionElement.Clickable("X"){
                val x = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationTrait(Vector3f(x[0], res.trait.vector.y, res.trait.vector.z))
            },
            QuestionElement.Clickable("Y"){
                val y = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationTrait(Vector3f(res.trait.vector.x, y[0], res.trait.vector.z))
            },
            QuestionElement.Clickable("Z"){
                val z = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationTrait(Vector3f(res.trait.vector.x, res.trait.vector.y, z[0]))
            }
        )
    }

    class LocationAndDirectionTrait(
        override val vector: Vector3f,
        override val yaw: Float = 0f,
        override val pitch: Float = 0f,
    ): LocatableTrait("location-and-direction"){
        override fun question(res: EditingTraitHolder<LocatableTrait>): QuestionElement = QuestionElement.ForTrait(
            this,
            QuestionElement.Clickable("X"){
                val x = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationTrait(Vector3f(x[0], res.trait.vector.y, res.trait.vector.z))
            },
            QuestionElement.Clickable("Y"){
                val y = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationTrait(Vector3f(res.trait.vector.x, y[0], res.trait.vector.z))
            },
            QuestionElement.Clickable("Z"){
                val z = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationTrait(Vector3f(res.trait.vector.x, res.trait.vector.y, z[0]))
            },
            QuestionElement.Clickable("Yaw"){
                val yaw = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationAndDirectionTrait(res.trait.vector, yaw[0], pitch)
            },
            QuestionElement.Clickable("Pitch"){
                val pitch = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationAndDirectionTrait(res.trait.vector, yaw, pitch[0])
            }
        )
    }
}


