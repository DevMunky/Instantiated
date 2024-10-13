package dev.munky.instantiated.dungeon

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.IntraEntry
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.data.loader.FormatStorage
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.dungeon.mob.DungeonMob
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.FileUtil
import dev.munky.instantiated.world.VoidGenerator
import io.papermc.paper.util.Tick
import net.kyori.adventure.util.TriState
import org.bukkit.*
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.Duration
import java.util.*
import kotlin.math.pow

/**
 * Hopefully one day replacing the java impl!
 */
class DungeonManagerImpl : DungeonManager {
    override val dungeonWorld: World = createDungeonWorld()

    override val instances get() = get<FormatStorage>().values.flatMap { it.instances }

    override fun initialize() {}

    override fun getCurrentDungeon(
        player: UUID
    ): Instance? = instances.firstOrNull { it.players.contains(player) }

    override fun cleanup() {
        for (instance in instances) {
            for (player in instance.onlinePlayers) {
                instance.removePlayer(player.uniqueId) // probably fine because i delete and recreate world on startup anyhow
            }
        }
        val context =
            if (plugin.state.isDisabled) Instance.RemovalReason.PLUGIN_DISABLE
            else Instance.RemovalReason.PLUGIN_RELOAD

        val currentInstances = ArrayList(instances)
        for (instance in currentInstances){
            try{
                instance.remove(context, false)
            }catch (t: Throwable){
                t.log("Exception while cleaning up instance")
            }
        }
    }

    override fun shutdown() = runCatching {
        cleanup() // TODO remove
        do {
            plugin.logger.debug("Trying to delete dungeon world")
            Bukkit.unloadWorld(dungeonWorld, false)
            FileUtil.deleteWorld(dungeonWorld)
        } while (dungeonWorld.worldFolder.exists())
    }.onFailure { it.log("Error during DungeonManager shutdown") }.getOrDefault(Unit)

    private fun createInstance(
        format: Format,
        location: Location = nextLocation(),
        ops: Format.InstanceOption
    ): Result<Instance> = runCatching {
        format.instance(location,ops)
    }
    
    override fun startInstance(
        id: String,
        ops: Format.InstanceOption,
        players: Collection<UUID>
    ) = runCatching {
        val key = IdType.DUNGEON.with(id)
        startInstance(get<FormatStorage>()[key] ?: throw DungeonExceptions.ComponentNotFound.consume(key), ops, players).getOrThrow()
    }

    private fun checkPlayer(player: UUID) = getCurrentDungeon(player)?.removePlayer(player)

    override fun startInstance(
        format: Format,
        ops: Format.InstanceOption,
        players: Collection<UUID>
    ): Result<Instance> = runCatching {
        players.forEach { checkPlayer(it) }
        val instance = createInstance(format, nextLocation(), ops)
            .onFailure { throw DungeonExceptions.Instantiation.consume(format.identifier,it) }
            .onSuccess { instance ->
                instance.addPlayers(players.mapNotNull { Bukkit.getPlayer(it) })
            }
        return instance
    }

    private fun nextLocation(index: Int = instances.size): Location {
        var x = 0 // center x
        var z = 0 // center z
        var d = "right"
        var n = 1.0
        var finalLocation = dungeonWorld.spawnLocation
        // gets the locationInWorld for this number index, in the spiral.
        // Index 0 would be the first spot at the starting locationInWorld,
        // and index 1 would be the next spot
        for (i in 0 until index) {
            // change the direction
            if (i.toDouble() == n.pow(2.0) - n) {
                d = "right"
            } else if (i.toDouble() == n.pow(2.0)) {
                d = "down"
            } else if (i.toDouble() == n.pow(2.0) + n) {
                d = "left"
            } else if (i.toDouble() == n.pow(2.0) + (n * 2 + 1)) {
                d = "up"
                n += 2
            }
            // get the current x and y.
            val gridSize = get<TheConfig>().dungeonGridSize.value
            when (d) {
                "right" -> x += gridSize
                "left" -> x -= gridSize
                "down" -> z += gridSize
                else -> z -= gridSize
            }
            finalLocation = Location(finalLocation.world, x.toDouble(), finalLocation.y, z.toDouble())
        }
        if (instances.any{ l -> l.locationInWorld.distance(finalLocation) < 3 }
        ) {
            finalLocation = nextLocation(index - 1)
        }
        return finalLocation
    }
}

// for some reason doing Long -> Long throws a compile time error, so do Long -> Int instead
val Long.fromMillisToTicks : Int get() {
    return Tick.tick().fromDuration(Duration.ofMillis(this))
}
// for some reason doing Long -> Long throws a compile time error, so do Int -> Long instead
val Int.fromTicksToMillis : Long get()  {
    return Duration.of(this.toLong(), Tick.tick()).toMillis()
}

interface DungeonManager : KoinComponent {
    fun initialize()
    fun startInstance(
        id: String,
        ops: Format.InstanceOption,
        players: Collection<UUID>
    ): Result<Instance>
    fun startInstance(
        format: Format,
        ops: Format.InstanceOption,
        players: Collection<UUID>
    ): Result<Instance>
    fun getCurrentDungeon(player: UUID): Instance?
    fun cleanup()
    fun shutdown()

    val dungeonWorld: World
    val instances: Collection<Instance>

    // return Result<World> in the future for better error handling
    fun createDungeonWorld(): World {
        val worldName = get<TheConfig>().dungeonWorldName.value
        var dungeonWorld: World? = Bukkit.getWorld(worldName)
        if (dungeonWorld == null && Bukkit.getWorlds().isNotEmpty()) {
            val creator = WorldCreator.name(worldName)
            creator.generator(VoidGenerator())
            creator.keepSpawnLoaded(TriState.FALSE)
            creator.environment(World.Environment.NORMAL)
            creator.generateStructures(false)
            creator.type(WorldType.FLAT)
            plugin.logger.debug("Creating instancing world...")
            val newWorld = creator.createWorld()
            if (newWorld != null) {
                newWorld.viewDistance = 15
                newWorld.simulationDistance = 15
                newWorld.isAutoSave = false
                newWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
                newWorld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
                newWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                newWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                newWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                newWorld.setGameRule(GameRule.DO_TILE_DROPS, false)
                newWorld.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false)
                dungeonWorld = newWorld
                plugin.logger.debug("Successfully created instancing world")
            } else {
                plugin.logger.warning("Could not create a new world based on config value 'dungeon.world'")
            }
        }
        return dungeonWorld ?: throw IllegalStateException("Dungeon world could not be created")
    }

    companion object {
        val KEY_ENTITY = IntraEntry<IdKey>("key-entity")
        val KEY_ENTITY_ITEM = NamespacedKey(plugin, "key-entity-item")
        val DUNGEON_MOB_ENTITY = IntraEntry<Pair<RoomInstance, DungeonMob>>("dungeon.mob")
        val INIT_TIME = NamespacedKey(plugin, "init-time")
        val NO_DESPAWN_ENTITY = IntraEntry<Unit>("no-despawn-entity")
        val EDIT_TOOL = NamespacedKey(plugin, "edit-tool")
        const val DEFAULT_DIFFICULTY = 1.0
    }
}

inline val Player.currentDungeon: Instance? get() = plugin.get<DungeonManager>().getCurrentDungeon(this.uniqueId)
