package dev.munky.instantiated.dungeon.component.trait

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.structs.Identifiable
import dev.munky.instantiated.edit.PromptFactory
import dev.munky.instantiated.edit.QuestionElement
import org.joml.Vector3f

/**
 *
 * the object permanence for traits is simple. Each component that needs a trait get its own instance.
 *
 * `traits are not shared across components. One trait can have effects over multiple [Instances][dev.munky.instantiated.dungeon.interfaces.Instance]
 *
 * Traits by themselves do **specific and niche things**, like setting a region of blocks or spawning a mob.
 * *Chaining together traits with components* is where the real functionality shines.
 */
abstract class Trait(
    override val identifier: IdKey,
): Identifiable {
    constructor(
        id: String
    ): this(IdType.TRAIT.with(id))
}

interface EditableTrait<T: Trait>{
    fun question(res: Any): QuestionElement = question(res as? EditingTraitHolder<T> ?: throw IllegalArgumentException("Incorrect cast, sadly i cant do a compile time check because of the absence of union type parameters in kotlin.")) // i have to do this because i cant use INTERSECTION TYPES
    fun question(res: EditingTraitHolder<T>): QuestionElement
}

class EditingTraitHolder<T: Trait>(
    initTrait: T,
    val f: (T) -> Unit
){
    var trait: T = initTrait
        set(value) {
            f(value)
            field = value
        }
}

sealed class LocatableTrait<T: Trait>(id: String): Trait(id), EditableTrait<T> {
    abstract val vector: Vector3f
    abstract val yaw: Float
    abstract val pitch: Float

    class LocationTrait(
        override val vector: Vector3f
    ): LocatableTrait<LocationTrait>("location"){
        override val yaw: Float = 0f
        override val pitch: Float = 0f
        override fun question(res: EditingTraitHolder<LocationTrait>): QuestionElement = QuestionElement.ForTrait(
            this,
            QuestionElement.Clickable("Click a block"){
                val vec = PromptFactory.promptLocation("Vector3i", it)
                res.trait = LocationTrait(Vector3f(vec))
            },
            QuestionElement.Clickable("X", res.trait.vector.x){
                val x = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationTrait(Vector3f(x[0], res.trait.vector.y, res.trait.vector.z))
            },
            QuestionElement.Clickable("Y", res.trait.vector.y){
                val y = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationTrait(Vector3f(res.trait.vector.x, y[0], res.trait.vector.z))
            },
            QuestionElement.Clickable("Z", res.trait.vector.z){
                val z = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationTrait(Vector3f(res.trait.vector.x, res.trait.vector.y, z[0]))
            }
        )
    }

    class LocationAndDirectionTrait(
        override val vector: Vector3f,
        override val yaw: Float = 0f,
        override val pitch: Float = 0f,
    ): LocatableTrait<LocationAndDirectionTrait>("location-and-direction"){
        override fun question(res: EditingTraitHolder<LocationAndDirectionTrait>): QuestionElement = QuestionElement.ForTrait(
            this,
            QuestionElement.Clickable("Click a block"){
                val vec = PromptFactory.promptLocation("Vector3i", it)
                res.trait = LocationAndDirectionTrait(Vector3f(vec), res.trait.yaw, res.trait.pitch)
            },
            QuestionElement.Clickable("X", res.trait.vector.x){
                val x = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationAndDirectionTrait(Vector3f(x[0], res.trait.vector.y, res.trait.vector.z), yaw, pitch)
            },
            QuestionElement.Clickable("Y", res.trait.vector.y){
                val y = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationAndDirectionTrait(Vector3f(res.trait.vector.x, y[0], res.trait.vector.z), yaw, pitch)
            },
            QuestionElement.Clickable("Z", res.trait.vector.z){
                val z = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationAndDirectionTrait(Vector3f(res.trait.vector.x, res.trait.vector.y, z[0]), yaw, pitch)
            },
            QuestionElement.Clickable("Yaw", res.trait.yaw){
                val yaw = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationAndDirectionTrait(res.trait.vector, yaw[0], pitch)
            },
            QuestionElement.Clickable("Pitch", res.trait.pitch){
                val pitch = PromptFactory.promptFloats(1, it) ?: return@Clickable
                res.trait = LocationAndDirectionTrait(res.trait.vector, yaw, pitch[0])
            }
        )
    }
}


