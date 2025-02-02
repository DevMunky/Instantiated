package dev.munky.instantiated

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import dev.munky.instantiated.command.DungeonCommand
import dev.munky.instantiated.common.util.UtilHolder
import dev.munky.instantiated.common.util.formattedSeconds
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.IntraDataStores
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.data.loader.*
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.DungeonManagerImpl
import dev.munky.instantiated.dungeon.EventManager
import dev.munky.instantiated.dungeon.TaskManager
import dev.munky.instantiated.dungeon.component.TraitContext
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.edit.BlockDisplayRenderer
import dev.munky.instantiated.edit.EditModeHandler
import dev.munky.instantiated.edit.ParticleRenderer
import dev.munky.instantiated.edit.TextRenderer
import dev.munky.instantiated.event.InstantiatedStateEvent
import dev.munky.instantiated.event.testing.TestingMobs
import dev.munky.instantiated.network.ServerPacketRegistration
import dev.munky.instantiated.paperhack.PaperCodecSupport
import dev.munky.instantiated.provider.FAWEProvider
import dev.munky.instantiated.provider.WorldChangeAccess
import dev.munky.instantiated.scheduling.Schedulers
import dev.munky.instantiated.util.CustomLogger
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
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
import java.util.*

class Instantiated : InstantiatedPlugin() {
    @Suppress("unused")
    companion object{
        /**
         * Only exists for java interop.
         */
        @JvmStatic val instantiated get() = plugin
        @JvmStatic val api get() = InstantiatedAPI
    }
    private var _loadState: PluginState = PluginState.UNDEFINED
    private var forceStop = true
    val initTime: Long = System.currentTimeMillis()
    val isMythicSupported: Boolean = false
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

        Schedulers.COMPONENT_PROCESSING.submit{
            UtilHolder.registerProvider(logger to { debug })
            get<TheConfig>().load()
            get<LangFileLoader>().load()
        }

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

        Schedulers.COMPONENT_PROCESSING.submit {
            get<WorldChangeAccess>().initialize(FAWEProvider) // just go with world edit for now

            get<EventManager>().initialize()
            get<TaskManager>().initialize()

            get<DungeonManager>().initialize()

            get<TextRenderer>().initialize()

            get<TheConfig>().renderer.value.initialize()

            get<FormatLoader>().load()
            get<MobLoader>().load()
            get<ComponentLoader>().load()

            get<EditModeHandler>().initialize()

            get<ServerPacketRegistration>().initialize(get())

            TestingMobs() // TODO remove test
        }

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

        get<TheConfig>().renderer.value.shutdown()

        get<DungeonManager>().shutdown()
        get<EditModeHandler>().shutdown()

        CommandAPI.onDisable()
        getKoin().close()

        logger.info("Disabled, goodbye")
        _loadState = PluginState.DISABLED
    }

    fun reload(save: Boolean) {
        _loadState = PluginState.RELOADING
        if (!Bukkit.isPrimaryThread()){
            plugin.logger.debug("Moved reload to primary thread")
            Schedulers.SYNC.submit {
                reload(save)
            }
            return
        }
        val startTime = System.nanoTime()

        InstantiatedStateEvent(_loadState).callEvent()

        Schedulers.ASYNC.submit {
            if (save) get<FormatLoader>().save()

            get<DungeonManager>().cleanup()
            get<EditModeHandler>().shutdown()
            get<TextRenderer>().shutdown()
            get<TheConfig>().renderer.value.shutdown()

            get<TheConfig>().load()
            get<LangFileLoader>().load()

            get<FormatLoader>().load()
            get<MobLoader>().load()
            get<ComponentLoader>().load()

            get<TextRenderer>().initialize()
            get<TheConfig>().renderer.value.initialize()

            UtilHolder.registerProvider(logger to { debug })
        }

        get<EditModeHandler>().initialize()

        val timeToReload = Duration.ofNanos(System.nanoTime() - startTime)

        logger.info("Instantiated reloaded in ${timeToReload.formattedSeconds}")

        _loadState = PluginState.PROCESSING
    }

    val state: PluginState get() = _loadState
}

val plugin: Instantiated = KoinJavaComponent.get(Instantiated::class.java)

val theConfig = plugin.get<TheConfig>()

@Suppress("unused")
object InstantiatedAPI: KoinComponent {
    private val _plugin get() = try {
        plugin
    }catch (t: Throwable) {
        t.log("Exception thrown while using Instantiated API. " +
                "Chances are you are accessing the API " +
                "before the plugin is initialized.")
        throw t
    }
    val state: PluginState = _plugin.state
    val debug: Boolean = _plugin.debug
    fun reload(save: Boolean) = _plugin.reload(save)
    val isMythicSupported: Boolean = _plugin.isMythicSupported

    /**
     * Consumes the cache
     */
    fun startInstance(name: String, players: Collection<Player>) {
        val uuids = players.map{ it.uniqueId }
        get<DungeonManager>().startInstance(name, Format.InstanceOption.CONSUME_CACHE, uuids)
    }

    fun startEditModeFor(player: Player) = get<EditModeHandler>().startEditModeFor(player)
    fun stopEditModeFor(player: Player) = get<EditModeHandler>().stopEditModeFor(player)

    /**
     * Invoke a component by uuid.
     * @throws IllegalArgumentException if the uuid is not associated with a component
     * @param roomInstance the room context, if null then all rooms associated with a component instance
     */
    fun invokeComponent(uuid: UUID, roomInstance: RoomInstance?) {
        val component = get<ComponentStorage>().getByUUID(uuid) ?: throw IllegalArgumentException("Unknown component $uuid")
        if (roomInstance == null) {
            val roomFormat = get<ComponentStorage>().getRoomByComponent(component)
            for (instance in roomFormat.parent.instances) {
                val r = instance.rooms[roomFormat.identifier] ?: continue // shouldn't ever continue
                val ctx = TraitContext(r, component)
                component(ctx)
            }
        } else component(TraitContext(roomInstance, component))
    }
}



abstract class InstantiatedPlugin : JavaPlugin(), KoinComponent {
    private val iLogger = CustomLogger({ debug }, "Instantiated")
    override fun getLogger() = iLogger
    init{
        logger.info("Loading from this file -> " +
                File(this.javaClass.protectionDomain.codeSource.location.path).name
        )
    }

    val debug: Boolean get() {
        return try {
            get<TheConfig>().debug.value
        } catch (e: Throwable) {
            true
        }
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