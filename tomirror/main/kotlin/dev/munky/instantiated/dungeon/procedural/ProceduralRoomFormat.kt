package dev.munky.instantiated.dungeon.procedural

import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.math.BlockVector3
import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.util.asOptional
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomFormat
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.exception.DungeonExceptions
import org.bukkit.Material
import org.joml.Vector3ic
import java.io.File


class ProceduralRoomFormat(
    override val parent: Format,
    schematicFile: File,
    override var origin: Vector3ic,
    override val identifier: IdKey,
    override var box: Box,
    override var keyDropMode: RoomFormat.KeyDropMode,
    override var keyMaterial: Material,
    // these are the points to be lined up against other rooms to form dungeons
    val doors : List<BlockVector3>,
    val type : ProceduralRoomType
) : RoomFormat {

    val schematic: Clipboard =
        try {
            ClipboardFormats
                .findByFile(schematicFile).asOptional
                .orElseThrow { DungeonExceptions.Generic.consume("Schematic for room '$identifier' does not exist (${schematicFile.name})") }
                .getReader(schematicFile.inputStream())
                .read()
        } catch (e: Exception) {
            throw DungeonExceptions.DungeonDataFileNotFound.consume(schematicFile, e)
        }

    override fun instance(instance: Instance): RoomInstance {
        return ProceduralRoomInstance(instance as ProceduralInstance,this)
    }
}
enum class ProceduralRoomType {
    ONE_EXIT,
    TWO_EXIT,
    THREE_EXIT,
    FOUR_EXIT;
    companion object{
        fun fromCount(i:Int) : ProceduralRoomType? =
            if (!(1..ProceduralRoomType.entries.size).contains(i)) null else ProceduralRoomType.entries[i + 1]
    }
}