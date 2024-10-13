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
import org.bukkit.Material
import org.bukkit.World
import org.koin.core.component.KoinComponent
import java.io.File
import java.io.FileInputStream

class WorldChangeAccess: KoinComponent{
    private var _provider: InstantiatedWorldEditProvider? = null
    fun initialize(provider: InstantiatedWorldEditProvider) {
        _provider = provider
    }
    val provider get() = _provider ?: throw IllegalStateException("Access not initialized")
}

/**
 * Abstracting world edit because it sucks
 */
interface InstantiatedWorldEditProvider {
    fun setBlocks(box: Pair<Box,Material>, world: World)
    fun setBlocks(box: Map<Box,Material>, world: World)

    /**
     * Returns a box of the total changed area
     */
    fun paste(location: Location, file: File): Result<Box>
}

/**
 * Worldedit implementation
 */
object FAWEProvider : InstantiatedWorldEditProvider {
    override fun setBlocks(box: Pair<Box,Material>, world: World) {
        WorldEdit.getInstance().newEditSessionBuilder().world(
            BukkitAdapter.adapt(world)
        ).build().use { editSession ->
            _setBlocks(editSession, box)
        }
    }

    private fun _setBlocks(edit: EditSession, box: Pair<Box,Material>){
        val cuboidRegion = CuboidRegion(
            BlockVector3.at(box.first.pos1.x.toInt(), box.first.pos1.y.toInt(), box.first.pos1.z.toInt()),
            BlockVector3.at(box.first.pos2.x.toInt(), box.first.pos2.y.toInt(), box.first.pos2.z.toInt()),
        )
        edit.setBlocks(cuboidRegion as Region, BukkitAdapter.adapt(box.second.createBlockData()))
    }

    override fun setBlocks(box: Map<Box,Material>, world: World) {
        WorldEdit.getInstance().newEditSessionBuilder().world(
            BukkitAdapter.adapt(world)
        ).build().use { editSession ->
            box.forEach {
                _setBlocks(editSession, it.toPair())
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