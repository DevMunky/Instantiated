package dev.munky.instantiated.command

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandPermission
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.*
import dev.jorel.commandapi.arguments.CustomArgument.CustomArgumentInfoParser
import dev.jorel.commandapi.arguments.EntitySelectorArgument.ManyPlayers
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.common.util.asOptional
import dev.munky.instantiated.common.util.emptyOptional
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.data.loader.*
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.component.TraitContext
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.dungeon.lobby.LobbyFormat
import dev.munky.instantiated.dungeon.procedural.ProceduralFormat
import dev.munky.instantiated.dungeon.sstatic.StaticFormat
import dev.munky.instantiated.edit.EditModeHandler
import dev.munky.instantiated.edit.isInEditMode
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.commandFail
import dev.munky.instantiated.util.send
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.joml.Vector3f
import org.koin.core.component.get
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST") // its just command api argument casting
class DungeonCommand {
    fun createCommand(): CommandTree {
        val manager = plugin.get<DungeonManager>()
        val editModeHandler = plugin.get<EditModeHandler>()
        val formatLoader = plugin.get<FormatLoader>()
        val componentStorage = plugin.get<ComponentStorage>()
        return CommandTree("dungeon")
            .withPermission("instantiated.command.dungeon")
            .withAliases("inst", "instantiated")
            .saveCommand(formatLoader)
            .reloadCommand(editModeHandler)
            .startCommand(manager)
            .invokeComponentCommand(componentStorage)
            .leaveCommand(manager)
            .editCommand(editModeHandler)
            .createDungeonCommand()
            .setDebugCommand()
    }

    private fun CommandTree.createDungeonCommand(): CommandTree {
        return this.then(LiteralArgument("create")
            .then(LiteralArgument("dungeon")
                .then(TextArgument("name")
                    .then(TextArgument("schematic")
                        .executes(CommandExecutor { sender, args ->
                            val name = args[0] as String
                            val schemName = args[1] as String
                            val schem = FormatLoader.REGISTERED_SCHEMATICS[schemName]
                                ?: FormatLoader.REGISTERED_SCHEMATICS["$schemName.schem"]
                                ?: caption("command.create.dungeon.schematic_not_found", schemName).commandFail()
                            val storage = plugin.get<FormatStorage>()
                            val formats = HashMap(storage)
                            formats[IdType.DUNGEON with name] = StaticFormat(IdType.DUNGEON with name, schem, Vector3f(0f))
                            storage.load(formats)
                            caption("command.create.dungeon.success").send(sender)
                        })
                    )
                )
            )
        )
    }

    private fun CommandTree.setDebugCommand(): CommandTree{
        return this.then(LiteralArgument("debug")
            .then(BooleanArgument("state").setOptional(true)
                .executes(CommandExecutor { sender, args ->
                    val new = args.get("state") as? Boolean ?: !plugin.get<TheConfig>().debug.value
                    plugin.get<TheConfig>().debug.set(new)
                    caption("command.set_debug.success", new).send(sender)
                })
            )
        )
    }

    private fun CommandTree.invokeComponentCommand(componentStorage: ComponentStorage): CommandTree {
        return this.then(LiteralArgument("invokeC")
            .then(UUIDArgument("uuid")
                .executes(CommandExecutor { sender, args ->
                    val uuid = args.get("uuid") as UUID
                    val component = componentStorage.getByUUID(uuid)
                        ?: caption("command.invoke_component.not_found", uuid).commandFail()
                    val room = componentStorage.getRoomByComponent(component)
                    room.parent.instances.forEach {
                        val r = it.rooms[room.identifier] ?: return@forEach
                        component(TraitContext(r, component))
                    }
                    caption("command.invoke_component.success", component.identifier.key).send(sender)
                })
            )
        )
    }

    private fun CommandTree.saveCommand(formatLoader: FormatLoader): CommandTree {
        return this.then(LiteralArgument("save")
            .executes(CommandExecutor { sender, _ ->
                if (formatLoader.save() != DataOperationResult.SUCCESS){
                    caption("command.save.exception").send(sender)
                }
                else if (plugin.get<ComponentLoader>().save() != DataOperationResult.SUCCESS){
                    caption("command.save.exception").send(sender)
                }
                else if (plugin.get<MobLoader>().save() != DataOperationResult.SUCCESS){
                    caption("command.save.exception").send(sender)
                }
                else caption("command.save.success").send(sender)
            })
            .then(TextArgument("").replaceSuggestions(ArgumentSuggestions.strings(""))
                .then(BooleanArgument("force")
                    .executes(CommandExecutor { sender, args ->
                        val force = args["force"] as Boolean
                        if (formatLoader.save(force) != DataOperationResult.SUCCESS){
                            caption("command.save.exception", formatLoader.lastLoadResult.name).send(sender)
                        }
                        else if (plugin.get<ComponentLoader>().save(force) != DataOperationResult.SUCCESS){
                            caption("command.save.exception", plugin.get<ComponentLoader>().lastLoadResult.name).send(sender)
                        }
                        else if (plugin.get<MobLoader>().save(force) != DataOperationResult.SUCCESS){
                            caption("command.save.exception", plugin.get<MobLoader>().lastLoadResult.name).send(sender)
                        }
                        else caption("command.save.success").send(sender)
                    })
                )
            )
        )
    }

    private fun CommandTree.reloadCommand(handler: EditModeHandler): CommandTree {
        return this.then(LiteralArgument("reload")
            .executes(CommandExecutor { sender, _ ->
                if (handler.unsavedChanges) {
                    handler.unsavedChanges = false
                    caption("command.reload.unsaved_changes").commandFail()
                }
                try {
                    plugin.reload(false)
                    caption("command.reload.success").send(sender)
                } catch (e: Exception) {
                    e.log("Error while reloading")
                    caption("command.reload.error", e.message!!).commandFail()
                }
            })
        )
    }

    private fun CommandTree.startCommand(manager: DungeonManager): CommandTree {
        return this.then(LiteralArgument("start")
            .then(ManyPlayers("players")
                .then(DungeonArgument("dungeon")
                    .then(BooleanArgument("force-create").setOptional(true)
                        .executes(CommandExecutor { sender, args ->
                            val dungeon = args["dungeon"] as Optional<Format>
                            if (dungeon.isEmpty) caption("command.start.dungeon_not_found", args.rawArgsMap()["dungeon"]).commandFail()
                            val force = args["force-create"] as? Boolean
                            val instanceOption = if (force == null || force == false) {
                                Format.InstanceOption.CONSUME_CACHE
                            }else{
                                Format.InstanceOption.NEW_NON_CACHED
                            }

                            // command api has me covered
                            val players = args["players"] as? Collection<Player> ?: run {
                                caption("command.start.no_players").send(sender)
                                emptyList()
                            }
                            val result = manager.startInstance(dungeon.get(), instanceOption, players.map{ it.uniqueId })
                            if (result.isFailure) {
                                result.exceptionOrNull()?.log("Error trying to instance dungeon")
                                caption("command.start.failure", result.exceptionOrNull()!!.message).commandFail()
                            }
                            caption("command.start.success", players.map{ it.name }).send(sender)
                        })
                    )
                )
            )
        )
    }

    private fun CommandTree.leaveCommand(manager: DungeonManager): CommandTree {
        return this.then(LiteralArgument("leave")
            .executes(CommandExecutor { sender: CommandSender, _: CommandArguments? ->
                if (sender !is Player) {
                    throw CommandAPI.failWithString("You must be a player to leave a dungeon")
                }
                val dungeon = manager.getCurrentDungeon(sender.uniqueId)
                    ?: caption("command.leave.single.not_in_dungeon").commandFail()
                dungeon.removePlayer(sender.uniqueId)
                caption("command.leave.single.success").send(sender)
            })
            .then(ManyPlayers("players")
                .withPermission(CommandPermission.OP)
                .executes(CommandExecutor { sender: CommandSender, args: CommandArguments ->
                    // command api has me covered
                    val players = args["players"] as Collection<Player>
                    if (players.isEmpty()) caption("command.leave.many.no_players").commandFail()
                    for (player in players) {
                        manager.getCurrentDungeon(player.uniqueId)?.removePlayer(player.uniqueId)
                    }
                    caption("command.leave.many.success", players.map{ it.name })
                })
            )
        )
    }

    private fun CommandTree.editCommand(editModeHandler: EditModeHandler): CommandTree {
        return this.then(LiteralArgument("edit")
            .executesPlayer(PlayerCommandExecutor { player, _ ->
                if (player.isInEditMode) editModeHandler.stopEditModeFor(player)
                else editModeHandler.startEditModeFor(player)
            })
        )
    }

    /**
     * Returns an optional dungeon, in-case one does not exist with the identifier
     * @param nodeName self-explanatory, read command api
     */
    class DungeonArgument(
        nodeName: String,
        private val type: DungeonType = DungeonType.ALL
    ) : CustomArgument<Optional<Format>, String>(
        TextArgument(nodeName),
        CustomArgumentInfoParser { info ->
            val key = IdType.DUNGEON.with(info.input)
            val dungeon = plugin.get<FormatStorage>()[key].asOptional
            if (dungeon.isPresent && !type.clazz.isInstance(dungeon.get()))// don't want to do type checking in args
                emptyOptional()
            else dungeon
        }
    ) {
        init {
            this.replaceSuggestions(ArgumentSuggestions.stringCollectionAsync {
                CompletableFuture.completedFuture(
                    plugin.get<FormatStorage>().values
                        .filter { type.clazz.isInstance(it) }
                        .map { it.identifier.key }
                )
            })
        }

        enum class DungeonType(kClass: KClass<*>) {
            STATIC(StaticFormat::class),
            PROCEDURAL(ProceduralFormat::class),
            ALL(Any::class),
            LOBBY(LobbyFormat::class);
            val clazz = kClass
        }
    }
}