@file:Suppress("UnstableApiUsage")

package dev.munky.instantiated.provider

import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operation
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.session.ClipboardHolder
import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.util.toVector3f
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockType
import java.io.File
import java.io.FileInputStream

class WorldChangeAccess: WorldChangeProvider {
    private var _provider: WorldChangeProvider? = null
    fun initialize(provider: WorldChangeProvider) {
        _provider = provider
    }

    private val provider get() = _provider ?: throw IllegalStateException("Access not initialized")

    override fun setBlocks(box: Box, type: BlockType, world: World) = provider.setBlocks(box, type, world)
    override fun setBlocks(box: Map<Box, BlockType>, world: World) = provider.setBlocks(box, world)
    override fun paste(location: Location, file: File): Result<Box> = provider.paste(location, file)
}

/**
 * Abstracting world edit because it sucks
 */
interface WorldChangeProvider {
    fun setBlocks(box: Box, type: BlockType, world: World)
    fun setBlocks(box: Map<Box,BlockType>, world: World)

    /**
     * Returns a box of the changed area
     */
    fun paste(location: Location, file: File): Result<Box>
}

/**
 * WorldEdit implementation
 */
object FAWEProvider : WorldChangeProvider {
    override fun setBlocks(box: Box, type: BlockType, world: World) {
        WorldEdit.getInstance().newEditSessionBuilder().world(
            BukkitAdapter.adapt(world)
        ).build().use { editSession ->
            setBlocks0(editSession, box, type)
        }
    }

    private fun setBlocks0(edit: EditSession, box: Box, type: BlockType){
        val cuboidRegion = CuboidRegion(
            BlockVector3.at(box.pos1.x.toInt(), box.pos1.y.toInt(), box.pos1.z.toInt()),
            BlockVector3.at(box.pos2.x.toInt(), box.pos2.y.toInt(), box.pos2.z.toInt()),
        )
        edit.setBlocks(cuboidRegion as Region, BukkitAdapter.adapt(type.createBlockData()))
    }

    override fun setBlocks(box: Map<Box,BlockType>, world: World) {
        WorldEdit.getInstance().newEditSessionBuilder().world(
            BukkitAdapter.adapt(world)
        ).build().use { editSession ->
            box.forEach {
                setBlocks0(editSession, it.key, it.value)
            }
        }
    }

    override fun paste(location: Location, file: File): Result<Box> = kotlin.runCatching{
        val clipboardFormat = ClipboardFormats.findByFile(file)
            ?: throw IllegalArgumentException("No schematic found by file name '${file.name}'")
        val pastedRegion: CuboidRegion
        clipboardFormat.getReader(FileInputStream(file)).use { clipboardReader ->
            WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(location.world))
                .fastMode(true)
                .checkMemory(false)
                .build()
                .use { editSession ->
                    val clipboard = clipboardReader.read()

                    pastedRegion = clipboard.region.clone() as CuboidRegion

                    val holder = ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .copyEntities(false)
                        .to(BlockVector3.at(location.x, location.y, location.z))
                        .ignoreAirBlocks(false)

                    val operation: Operation = holder.build()

                    Operations.complete(operation)
                }
        }
        val box = Box(pastedRegion.pos1.toVector3f,pastedRegion.pos2.toVector3f)
        return@runCatching box.plus(location.toVector3f)
    }
}