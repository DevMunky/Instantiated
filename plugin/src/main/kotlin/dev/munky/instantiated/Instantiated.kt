package dev.munky.instantiated

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import dev.munky.instantiated.command.DungeonCommand
import dev.munky.instantiated.common.util.UtilHolder
import dev.munky.instantiated.common.util.formattedSeconds
import dev.munky.instantiated.data.IntraDataStores
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.data.loader.*
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.DungeonManagerImpl
import dev.munky.instantiated.dungeon.EventManager
import dev.munky.instantiated.dungeon.TaskManager
import dev.munky.instantiated.edit.BlockDisplayRenderer
import dev.munky.instantiated.edit.EditModeHandler
import dev.munky.instantiated.edit.ParticleRenderer
import dev.munky.instantiated.edit.TextRenderer
import dev.munky.instantiated.event.InstantiatedStateEvent
import dev.munky.instantiated.event.testing.TestingMobs
import dev.munky.instantiated.lang.LangFileLoader
import dev.munky.instantiated.lang.LangStorage
import dev.munky.instantiated.network.ServerPacketRegistration
import dev.munky.instantiated.paperhack.PaperCodecSupport
import dev.munky.instantiated.provider.FAWEProvider
import dev.munky.instantiated.provider.WorldChangeAccess
import dev.munky.instantiated.util.CustomLogger
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.get
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import org.koin.core.module.dsl.*
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent
import java.io.File
import java.time.Duration

inline fun <reified T : Any> Any.easyGet(): T {
    if (this is KoinScopeComponent) {
        throw UnsupportedOperationException("use get() while inside of a koin component")
    }
    return plugin.get()
}

class Instantiated : InstantiatedPlugin(), KoinComponent {
    companion object{
        @JvmStatic val instantiated get() = plugin // for java interop
    }
    private var _loadState: PluginState = PluginState.UNDEFINED
    private var forceStop = true
    val initTime: Long = System.currentTimeMillis()
    override val isMythicSupported: Boolean = false
    private var weOwnCommandAPI = false

    override fun onLoad() {
        _loadState = PluginState.LOADING

        val modules = module {
            single { this@Instantiated } withOptions {
                named("plugin")
                bind<Plugin>()
                createdAtStart()
            }

            singleOf<TheConfig>(::TheConfig)

            singleOf<EditModeHandler>(::EditModeHandler)
            singleOf<EventManager>(::EventManager)
            singleOf<TaskManager>(::TaskManager)
            singleOf<DungeonManager>(::DungeonManagerImpl)
            singleOf<DungeonCommand>(::DungeonCommand)

            singleOf<FormatStorage>(::FormatStorage)
            singleOf<FormatLoader>(::FormatLoader)

            singleOf<MobStorage>(::MobStorage)
            singleOf<MobLoader>(::MobLoader)

            singleOf<ComponentStorage>(::ComponentStorage)
            singleOf<ComponentLoader>(::ComponentLoader)

            singleOf<LangFileLoader>(::LangFileLoader)
            singleOf<LangStorage>(::LangStorage)

            singleOf<ParticleRenderer>(::ParticleRenderer)
            singleOf<BlockDisplayRenderer>(::BlockDisplayRenderer)
            singleOf<TextRenderer>(::TextRenderer)

            single<IntraDataStores> { IntraDataStores }

            singleOf<PaperCodecSupport>(::PaperCodecSupport)
            singleOf<ServerPacketRegistration>(::ServerPacketRegistration)
            singleOf<WorldChangeAccess>(::WorldChangeAccess)
        }

        startKoin {
            modules(modules)
            logger(CustomKoinLogger(plugin.logger))
        }

        UtilHolder.registerProvider(logger to { debug })
        get<TheConfig>().load()
        get<LangFileLoader>().load()

        weOwnCommandAPI = !CommandAPI.isLoaded()
        if (weOwnCommandAPI) CommandAPI.onLoad(CommandAPIBukkitConfig(this)
            .setNamespace("instantiated")
            .skipReloadDatapacks(true) // TODO make sure people dont need data-pack support!
        )

        _loadState = PluginState.UNDEFINED
    }

    override fun onEnable() {
        _loadState = PluginState.ENABLING

        val startTime = System.nanoTime()

        get<WorldChangeAccess>().initialize(FAWEProvider) // just go with world edit for now

        get<EventManager>().initialize()
        get<TaskManager>().initialize()

        get<DungeonManager>().initialize()

        get<TextRenderer>().initialize()

        get<TheConfig>().RENDERER.value.initialize()

        get<FormatLoader>().load()
        get<MobLoader>().load()
        get<ComponentLoader>().load()

        get<EditModeHandler>().initialize()

        get<ServerPacketRegistration>().initialize(get())

        TestingMobs() // TODO remove test


        if (debug) {
            loadKoinModules(module {
                singleOf<UnitTesting>(::UnitTesting)
            })
            get<UnitTesting>().start()
        }

        if (weOwnCommandAPI) CommandAPI.onEnable()
        get<DungeonCommand>().createCommand().register("instantiated")

        val timeToEnable = Duration.ofNanos(System.nanoTime() - startTime)

        logger.info("Instantiated enabled in ${timeToEnable.formattedSeconds}")

        _loadState = PluginState.PROCESSING
        forceStop = false // this way exceptions thrown during loading or enabling, fail fast
    }

    override fun onDisable() {
        if (forceStop) {
            _loadState = PluginState.DISABLED
            logger.warning("FORCE STOPPED! Something bad definitely happened. Dumping stack just in case there wasn't one before")
            Thread.dumpStack()
            return
        }
        _loadState = PluginState.DISABLING
        // Plugin shutdown logic

        get<FormatLoader>().save()
        get<ComponentLoader>().save()
        get<MobLoader>().save()

        // when renderer type becomes an option, add that

        get<DungeonManager>().shutdown()
        get<EditModeHandler>().shutdown()

        CommandAPI.onDisable()
        getKoin().close()

        logger.info("Disabled, goodbye")
        _loadState = PluginState.DISABLED
    }

    override fun onReload(save: Boolean) {
        _loadState = PluginState.RELOADING
        val startTime = System.nanoTime()

        InstantiatedStateEvent(_loadState).callEvent()

        if (save) get<FormatLoader>().save()
        get<DungeonManager>().cleanup()
        get<EditModeHandler>().shutdown()
        get<TextRenderer>().shutdown()
        get<TheConfig>().RENDERER.value.shutdown()

        get<TheConfig>().load()
        get<LangFileLoader>().load()

        get<FormatLoader>().load()
        get<MobLoader>().load()
        get<ComponentLoader>().load()

        get<TextRenderer>().initialize()
        get<TheConfig>().RENDERER.value.initialize()

        get<EditModeHandler>().initialize()

        UtilHolder.registerProvider(logger to { debug })

        val timeToReload = Duration.ofNanos(System.nanoTime() - startTime)

        logger.info("Instantiated reloaded in ${timeToReload.formattedSeconds}")

        _loadState = PluginState.PROCESSING
    }

    override val debug: Boolean get() {
        return try {
            get<TheConfig>().DEBUG.value
        } catch (e: Throwable) {
            true
        }
    }

    override val state: PluginState get() = _loadState
}

val plugin: Instantiated = KoinJavaComponent.get(Instantiated::class.java)

interface InstantiatedAPI : KoinComponent {
    companion object{
        @JvmStatic
        val API: InstantiatedAPI get() = plugin
    }

    val state: PluginState
    val debug: Boolean
    fun onReload(save: Boolean)

    val isMythicSupported: Boolean
}

abstract class InstantiatedPlugin : JavaPlugin(), InstantiatedAPI {
    private val iLogger = CustomLogger({ debug }, "Instantiated")
    override fun getLogger(): CustomLogger = iLogger
    init{
        logger.info("Loading from this file -> " +
                File(this.javaClass.protectionDomain.codeSource.location.path).name
        )
    }
}

private class CustomKoinLogger(
    private val logger: CustomLogger
): Logger(convertLogger(logger.level)) {
    override fun display(level: Level, msg: MESSAGE) {
        when (level) {
            Level.DEBUG -> logger.debug(msg)
            Level.INFO -> logger.info(msg)
            Level.ERROR -> logger.severe(msg)
            Level.NONE -> logger.info(msg)
            Level.WARNING -> logger.warning(msg)
        }
    }
    companion object {
        fun convertLogger(level: java.util.logging.Level?): Level {
            return when (level) {
                java.util.logging.Level.FINEST -> Level.DEBUG
                java.util.logging.Level.FINER -> Level.DEBUG
                java.util.logging.Level.FINE -> Level.DEBUG
                java.util.logging.Level.CONFIG -> Level.DEBUG
                java.util.logging.Level.INFO -> Level.INFO
                java.util.logging.Level.WARNING -> Level.WARNING
                java.util.logging.Level.SEVERE -> Level.ERROR
                else -> Level.INFO
            }
        }
    }
}

