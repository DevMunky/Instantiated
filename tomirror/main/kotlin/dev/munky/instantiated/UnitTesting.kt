package dev.munky.instantiated

import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.util.formatException
import dev.munky.instantiated.data.loader.FormatStorage
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.component.DoorComponent
import dev.munky.instantiated.dungeon.component.trait.SetBlocksTrait
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.sstatic.StaticFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.block.BlockType
import org.joml.Vector3f
import org.joml.Vector3i
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.*

private inline fun test(es: MutableMap<String, Exception>, name: String, block: () -> Unit){
    try {
        block()
        plugin.logger.test("Test $name passed")
    }catch (t: Throwable){
        plugin.logger.severe("Test $name failed")
        es[name] = (IllegalStateException("Test $name failed", t))
    }
}

@Suppress("UnstableApiUsage")
class UnitTesting: KoinComponent {
    private inline fun <reified T> get(): T = plugin.get()
    var testInst: Instance? = null

    fun start(){
        val results = mutableMapOf<String, Exception>()
        test(results,"format"){
            val formats = get<FormatStorage>().values.toMutableList()
            formats.add(
                StaticFormat(
                    IdType.DUNGEON with "unit-test",
                    null,
                    Vector3f(5f)
                )
            )
            get<FormatStorage>().load(formats.associateBy { it.identifier })
        }
        test(results,"instance"){
            val i = get<DungeonManager>()
                .startInstance("unit-test", Format.InstanceOption.NEW_NON_CACHED, listOf())
            testInst = i.getOrThrow()
        }
        test(results, "questions"){
            val trait = SetBlocksTrait(BlockType.ICE, BlockType.AIR, SetBlocksTrait.ChangeFunction.BOTTOM_UP, mutableSetOf(Vector3i(1)))
            val comp = DoorComponent(
                trait,
                UUID.nameUUIDFromBytes(ByteArray(0))
            )
            val question = comp.question.build()
            val ser = PlainTextComponentSerializer.plainText()
            // could include more in this test
            check(question.contains(Component.text("""
                Set change-function [
                   -> TOP_DOWN
                   -> BOTTOM_UP
                   -> NEGATIVE_2_POSITIVE
                   -> POSITIVE_2_NEGATIVE
                ]
            """.trimIndent()) ){ c1, c2 ->
                val str1 = ser.serialize(c1)
                val str2 = ser.serialize(c2)
                str1.contains(str2) || str2.contains(str1)
            }) {"question does not contain trait"}
        }
        test(results, "cleanup"){
            val formats = get<FormatStorage>().values.toMutableList()
            testInst!!.remove(Instance.RemovalReason.FORMAT_CHANGE, false)
            formats.removeIf { it.identifier.key == "unit-test" }
            get<FormatStorage>().load(formats.associateBy { it.identifier })
        }
        results.forEach {
            plugin.logger.test("Test ${it.key} failed: ${it.value.formatException(true, 5)}")
        }
    }
}