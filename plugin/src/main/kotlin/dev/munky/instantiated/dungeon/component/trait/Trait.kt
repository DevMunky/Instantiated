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
    fun question(eth: Any): QuestionElement = question(eth as? EditingTraitHolder<T> ?: throw IllegalArgumentException("Incorrect cast, sadly i cant do a compile time check because of the absence of union type parameters in kotlin.")) // i have to do this because i cant use INTERSECTION TYPES
    fun question(eth: EditingTraitHolder<T>): QuestionElement
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
        override fun question(eth: EditingTraitHolder<LocationTrait>): QuestionElement = QuestionElement.ForTrait(
            this,
            QuestionElement.Clickable("Click a block"){
                val vec = PromptFactory.promptLocation("Vector3i", it)
                eth.trait = LocationTrait(Vector3f(vec))
            },
            QuestionElement.Clickable("X", eth.trait.vector.x){
                val x = PromptFactory.promptFloats(1, it) ?: return@Clickable
                eth.trait = LocationTrait(Vector3f(x[0], eth.trait.vector.y, eth.trait.vector.z))
            },
            QuestionElement.Clickable("Y", eth.trait.vector.y){
                val y = PromptFactory.promptFloats(1, it) ?: return@Clickable
                eth.trait = LocationTrait(Vector3f(eth.trait.vector.x, y[0], eth.trait.vector.z))
            },
            QuestionElement.Clickable("Z", eth.trait.vector.z){
                val z = PromptFactory.promptFloats(1, it) ?: return@Clickable
                eth.trait = LocationTrait(Vector3f(eth.trait.vector.x, eth.trait.vector.y, z[0]))
            }
        )
    }

    class LocationAndDirectionTrait(
        override val vector: Vector3f,
        override val yaw: Float = 0f,
        override val pitch: Float = 0f,
    ): LocatableTrait<LocationAndDirectionTrait>("location-and-direction"){
        override fun question(eth: EditingTraitHolder<LocationAndDirectionTrait>): QuestionElement = QuestionElement.ForTrait(
            this,
            QuestionElement.Clickable("Click a block"){
                val vec = PromptFactory.promptLocation("Vector3i", it)
                eth.trait = LocationAndDirectionTrait(Vector3f(vec), eth.trait.yaw, eth.trait.pitch)
            },
            QuestionElement.Clickable("X", eth.trait.vector.x){
                val x = PromptFactory.promptFloats(1, it) ?: return@Clickable
                eth.trait = LocationAndDirectionTrait(Vector3f(x[0], eth.trait.vector.y, eth.trait.vector.z), yaw, pitch)
            },
            QuestionElement.Clickable("Y", eth.trait.vector.y){
                val y = PromptFactory.promptFloats(1, it) ?: return@Clickable
                eth.trait = LocationAndDirectionTrait(Vector3f(eth.trait.vector.x, y[0], eth.trait.vector.z), yaw, pitch)
            },
            QuestionElement.Clickable("Z", eth.trait.vector.z){
                val z = PromptFactory.promptFloats(1, it) ?: return@Clickable
                eth.trait = LocationAndDirectionTrait(Vector3f(eth.trait.vector.x, eth.trait.vector.y, z[0]), yaw, pitch)
            },
            QuestionElement.Clickable("Yaw", eth.trait.yaw){
                val yaw = PromptFactory.promptFloats(1, it) ?: return@Clickable
                eth.trait = LocationAndDirectionTrait(eth.trait.vector, yaw[0], pitch)
            },
            QuestionElement.Clickable("Pitch", eth.trait.pitch){
                val pitch = PromptFactory.promptFloats(1, it) ?: return@Clickable
                eth.trait = LocationAndDirectionTrait(eth.trait.vector, yaw, pitch[0])
            }
        )
    }
}