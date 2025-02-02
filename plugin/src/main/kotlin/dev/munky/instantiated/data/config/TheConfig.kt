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
    val debug = ConfigurationValue(
        "debug.enabled",
        listOf(
            "Whether to log extra debug info, get extra error info,",
            "and enable debug features in the game."
        ),
        { "Debug is undefined" },
        true,
        { it as Boolean }
    )

    val dungeonWorldName = ConfigurationValue(
        "dungeon.world",
        listOf("The name of the dungeon (instance) world"),
        { "Dungeon world name is undefined" },
        "instancing",
        { it as String }
    )

    val renderRefreshRate = ConfigurationValue(
        "dungeon.edit-mode.refresh-rate",
        listOf("The polling rate for edit mode rendering"),
        { "Render refresh rate is undefined" },
        5,
        { it as Int }
    )

    val renderResolution = ConfigurationValue(
        "dungeon.edit-mode.resolution",
        listOf("The resolution for edit mode rendering"),
        { "Render resolution is undefined" },
        4,
        { it as Int }
    )

    val keysGlow = ConfigurationValue(
        "dungeon.keys.dropped-keys-glow.enabled",
        listOf("Whether or not dropped keys glow on the ground"),
        { "Dropped keys glow is undefined" },
        true,
        { it as Boolean }
    )

    val keysGlowColor = ConfigurationValue(
        "dungeon.keys.dropped-keys-glow.color",
        listOf("The color of dropped keys, if enabled"),
        { "Dropped keys glow color is undefined" },
        NamedTextColor.BLUE,
        {
            val name = it as String
            NamedTextColor.NAMES.value(name)!!
        }
    )

    val dungeonGridSize = ConfigurationValue(
        "dungeon.grid-size",
        listOf("The size of the grid formed when creating instances of formats (dungeons)"),
        { "Dungeon grid size is undefined or out of bounds" },
        1000,
        { it as Int }
    )

    val dungeonCacheSize = ConfigurationValue(
        "dungeon.cache-size-per-dungeon",
        listOf("The amount of cached instances created per format"),
        { "Dungeon cache size is undefined: ${it.message})" },
        1,
        {
            val i = it as Int
            check(i < 10) { "Cache size too large (>10)" }
            i
        }
    )

    val resourceDataFiles = ConfigurationValue(
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

    // Koin is not fully initialized when this is constructed
    val renderer by lazy {
        ConfigurationValue(
            "dungeon.edit-mode.renderer",
            listOf(
                "Which edit mode renderer, either 'block' or 'particle'",
                "particle rendering will probably become deprecated lul"
            ),
            { "Edit mode renderer is undefined" },
            plugin.get<BlockDisplayRenderer>(),
            { name ->
                name as String
                when (name.lowercase()) {
                    "particle" -> plugin.get<ParticleRenderer>()
                    "block" -> plugin.get<BlockDisplayRenderer>()
                    else -> throw IllegalStateException("Renderer $name does not exist")
                }
            }
        )
    }

    val componentLogging = ConfigurationValue(
        "debug.components",
        listOf(
            "Whether components log debug information"
        ),
        { "Component debug logging is undefined" },
        false,
        { it as? Boolean ?: (it as String).toBoolean() }
    )

    override fun load0(data: ByteArray): DataOperationResult {
        val yaml = this.yaml.getOrThrow()
        resourceDataFiles.load(yaml)
        debug.load(yaml)
        dungeonWorldName.load(yaml)
        dungeonCacheSize.load(yaml)
        dungeonGridSize.load(yaml)
        keysGlow.load(yaml)
        keysGlowColor.load(yaml)
        renderResolution.load(yaml)
        renderRefreshRate.load(yaml)
        renderer.load(yaml)
        componentLogging.load(yaml)
        yaml.save(file)
        return DataOperationResult.SUCCESS
    }

    override fun save0(force: Boolean): DataOperationResult = DataOperationResult.SUCCESS // no op
}