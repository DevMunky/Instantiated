package dev.munky.instantiated.data.config

import dev.munky.instantiated.data.ConfigurationValue
import dev.munky.instantiated.data.loader.DataFileLoader
import dev.munky.instantiated.data.loader.DataOperationResult
import dev.munky.instantiated.edit.BlockDisplayRenderer
import dev.munky.instantiated.edit.ParticleRenderer
import dev.munky.instantiated.plugin
import net.kyori.adventure.text.format.NamedTextColor
import org.koin.core.component.get

class TheConfig: DataFileLoader("config.yml"){
    val DEBUG = ConfigurationValue(
        "debug.enabled",
        listOf(
            "Whether to log extra debug info, get extra error info,",
            "and enable debug features in the game."
        ),
        { "Debug is undefined" },
        true,
        { it as Boolean }
    )

    val DUNGEON_WORLD_NAME = ConfigurationValue(
        "dungeon.world",
        listOf("The name of the dungeon (instance) world"),
        { "Dungeon world name is undefined" },
        "instancing",
        { it as String }
    )

    val RENDER_REFRESH_RATE = ConfigurationValue(
        "dungeon.edit-mode.refresh-rate",
        listOf("The polling rate for edit mode rendering"),
        { "Render refresh rate is undefined" },
        5,
        { it as Int }
    )

    val RENDER_RESOLUTION = ConfigurationValue(
        "dungeon.edit-mode.resolution",
        listOf("The resolution for edit mode rendering"),
        { "Render resolution is undefined" },
        4,
        { it as Int }
    )

    val KEYS_GLOW = ConfigurationValue(
        "dungeon.keys.dropped-keys-glow.enabled",
        listOf("Whether or not dropped keys glow on the ground"),
        { "Dropped keys glow is undefined" },
        true,
        { it as Boolean }
    )

    val KEYS_GLOW_COLOR = ConfigurationValue(
        "dungeon.keys.dropped-keys-glow.color",
        listOf("The color of dropped keys, if enabled"),
        { "Dropped keys glow color is undefined" },
        NamedTextColor.BLUE,
        {
            val name = it as String
            NamedTextColor.NAMES.value(name)!!
        }
    )

    val DUNGEON_GRID_SIZE = ConfigurationValue(
        "dungeon.grid-size",
        listOf("The size of the grid formed when creating instances of formats (dungeons)"),
        { "Dungeon grid size is undefined or out of bounds" },
        1000,
        { it as Int }
    )

    val DUNGEON_CACHE_SIZE = ConfigurationValue(
        "dungeon.cache-size-per-dungeon",
        listOf("The amount of cached instances created per format"),
        { "Dungeon cache size is undefined or out of bounds" },
        1,
        {
            val i = it as Int
            check(i < 10) { "Cache size too large (>10)" }
            i
        }
    )

    val RESOURCE_DATA_FILES = ConfigurationValue(
        "debug.use-exclusively-resource-files",
        listOf(
            "Whether data file loaders pull exclusively from the jar resources",
            "resulting in only the packaged files being used.",
            "Existing files are left untouched. Imagine this as something akin to safe-mode.",
            "When enabled, every value in this file will be its default value, except for this option."
        ),
        { "Use exclusively resource files is undefined" },
        false,
        { it as Boolean }
    )

    val RENDERER = ConfigurationValue(
        "dungeon.edit-mode.renderer",
        listOf(
            "Which edit mode renderer, either ParticleRenderer or BlockDisplayRenderer"
        ),
        { "Edit mode renderer is undefined" },
        plugin.get<ParticleRenderer>(),
        { name ->
            name as String
            when (name.lowercase()){
                "particlerenderer", "particle" -> plugin.get<ParticleRenderer>()
                "blockdisplayrenderer", "block", "blockdisplay" -> plugin.get<BlockDisplayRenderer>()
                else -> throw IllegalStateException("Renderer $name does not exist")
            }
        }
    )

    override fun load0(data: ByteArray): DataOperationResult {
        val yaml = this.yaml.getOrThrow()
        RESOURCE_DATA_FILES.load(yaml)
        DEBUG.load(yaml)
        DUNGEON_WORLD_NAME.load(yaml)
        DUNGEON_CACHE_SIZE.load(yaml)
        DUNGEON_GRID_SIZE.load(yaml)
        KEYS_GLOW.load(yaml)
        KEYS_GLOW_COLOR.load(yaml)
        RENDER_RESOLUTION.load(yaml)
        RENDER_REFRESH_RATE.load(yaml)
        RENDERER.load(yaml)
        yaml.save(file)
        return DataOperationResult.SUCCESS
    }

    override fun save0(force: Boolean): DataOperationResult = DataOperationResult.SUCCESS // no op
}