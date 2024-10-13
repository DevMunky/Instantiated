package dev.munky.instantiated.edit


import dev.munky.instantiated.common.structs.Box
import dev.munky.instantiated.data.loader.ComponentStorage
import dev.munky.instantiated.dungeon.DungeonManager
import dev.munky.instantiated.dungeon.component.trait.LocatableTrait
import dev.munky.instantiated.dungeon.interfaces.Instance
import dev.munky.instantiated.dungeon.interfaces.RoomInstance
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.ComponentUtil
import dev.munky.instantiated.util.fromMini
import dev.munky.instantiated.util.toVector3f
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.persistence.PersistentDataType
import org.joml.Vector3f
import org.koin.core.component.get
import kotlin.math.pow

data class EditToolInteraction(
    val event: PlayerInteractEvent,
    val instance: Instance,
    val instancedRoom: RoomInstance,
    val interactionPoint: Location?
)

sealed interface EditTool{
    companion object{
        @JvmStatic
        val tools = EditTool::class.sealedSubclasses
            .map { it.objectInstance!! }
            .associateBy { it::class.simpleName!!.lowercase() }

        /**
         * @return true if an edit tool was used
         */
        @JvmStatic
        fun execute(click: EditToolInteraction) : Boolean{
            click.event.item ?: return false
            val editToolString = click.event.item!!.itemMeta.persistentDataContainer.get(
                DungeonManager.EDIT_TOOL,
                PersistentDataType.STRING
            ) ?: return false
            val tool = tools[editToolString] ?: return false
            if (!tool.condition(click)) return false
            click.event.isCancelled = true
            if (click.event.action == Action.RIGHT_CLICK_AIR || click.event.action == Action.RIGHT_CLICK_BLOCK) {
                tool.onRightClick(click)
            } else if (click.event.action == Action.LEFT_CLICK_AIR || click.event.action == Action.LEFT_CLICK_BLOCK) {
                tool.onLeftClick(click)
            }
            return true
        }
    }
    fun condition(click: EditToolInteraction) : Boolean
    fun onRightClick(click: EditToolInteraction)
    fun onLeftClick(click: EditToolInteraction)
    val item: ItemStack
    data object Vertex : EditTool {
        override fun onRightClick(click: EditToolInteraction) = onClick(click)
        override fun onLeftClick(click: EditToolInteraction) = onClick(click)
        override val item: ItemStack
            get() {
                val item = ItemStack(Material.NETHERITE_SHOVEL)
                item.editMeta {
                    it.displayName("<green>Dungeon Room edit tool".fromMini)
                    it.lore(mutableListOf(
                        "<gray>Click either right or left".fromMini,
                        "<gray>To move a corner of the closest room".fromMini,
                        "<gray>If you are not looking at a block,".fromMini,
                        "<gray>your current position is used".fromMini
                    ))
                    it.persistentDataContainer.set(
                        DungeonManager.EDIT_TOOL,
                        PersistentDataType.STRING,
                        this::class.simpleName!!.lowercase()
                    )
                }
                return item
            }
        override fun condition(click: EditToolInteraction): Boolean = true // no condition
        private fun onClick(click: EditToolInteraction){
            val interaction = click.interactionPoint ?: click.event.player.location;
            val interactionVector = interaction.toVector3f.add(click.instancedRoom.realVector.toVector3f.mul(-1f))
            val box = getCuboidFromNewCorner(click.instancedRoom.format.box, interactionVector)
            click.instancedRoom.box = box + click.instancedRoom.realVector.toVector3f
            click.instancedRoom.format.box = box // update the master 'template'
            click.event.player.sendMessage("<green>Added vertex at ${interactionVector.x},${interactionVector.y},${interactionVector.z}".fromMini)
            plugin.logger.debug(
                "Added vertex at " + interaction +
                        " from room '" + click.instancedRoom.identifier +
                        "' in '" + click.instance.identifier + "'"
            )
        }
    }
    data object Door : EditTool {
        override fun condition(click: EditToolInteraction): Boolean = true
        override fun onRightClick(click: EditToolInteraction) {
            TODO("Not yet implemented")
        }
        override fun onLeftClick(click: EditToolInteraction) {
            TODO("Not yet implemented")
        }
        override val item: ItemStack
            get() {
                val doorItem = ItemStack(Material.IRON_INGOT)
                doorItem.editMeta {
                    it.displayName(ComponentUtil.toComponent("<rainbow>Dungeon door creation tool"))
                    it.lore(mutableListOf(
                        "<gray>Right click to move closest door corner".fromMini,
                        "<gray>Left click to remove clicked door".fromMini
                    ))
                    it.persistentDataContainer.set(
                        DungeonManager.EDIT_TOOL,
                        PersistentDataType.STRING,
                        this::class.simpleName!!.lowercase()
                    )
                }
                return doorItem
            }
    }
    data object Config : EditTool{
        override fun condition(click: EditToolInteraction): Boolean = true
        override fun onRightClick(click: EditToolInteraction) = onClick(click)
        override fun onLeftClick(click: EditToolInteraction) = onClick(click)
        private fun onClick(click: EditToolInteraction){
            val interaction = click.interactionPoint ?: click.event.player.location
            // edit entities by standing close to them
            val message = if (click.event.player.isSneaking) {
                    ChatQuestions.getDungeonQuestion(click.instance)
                } else {
                    ChatQuestions.getRoomConfigQuestion(click.instancedRoom)
                }
            click.event.player.sendMessage(message)
        }
        override val item: ItemStack
            get() {
                val configItem = ItemStack(Material.EMERALD)
                configItem.editMeta {
                    it.displayName("<blue>Configuration Editor".fromMini)
                    it.lore(mutableListOf(
                        "<gray>Click on things to change their configs,".fromMini,
                        "<gray>Or click nothing to configure the current room".fromMini
                    ))
                    it.persistentDataContainer.set(
                        DungeonManager.EDIT_TOOL,
                        PersistentDataType.STRING,
                        this::class.simpleName!!.lowercase()
                    )
                }
                return configItem
            }
    }
    data object Component : EditTool {
        override fun condition(click: EditToolInteraction): Boolean = true
        override fun onRightClick(click: EditToolInteraction) = onClick(click)
        override fun onLeftClick(click: EditToolInteraction) = onClick(click)

        /**
         * Clicking edits a component if one is close enough, creating a new one otherwise.
         *
         * When creating a component, a chat question asks the editor which component should be placed.
         *
         * If sneaking, then edit components with no location trait
         */

        fun <K,V> HashMap(vararg pairs: Pair<K, V>): HashMap<K,V> = HashMap(pairs.toMap())

        private fun onClick(click: EditToolInteraction) {
            val interaction = (click.interactionPoint?.clone()?.subtract(click.instancedRoom.realVector))?.toVector3f ?: run {
                ManagedGui(HashMap(
                    0 to ManagedItem(ItemType.STICK, "component", {}) {
                        it.whoClicked.sendMessage("Hello!")
                    }
                ), 27)
                return
            }
            val components = plugin.get<ComponentStorage>()[click.instancedRoom.format] ?: return
            val component = components
                .filter { it.hasTrait<LocatableTrait>() } // has location trait
                .associateBy { it.getTrait<LocatableTrait>().vector.distanceSquared(interaction) } // map of distance to component
                .toSortedMap() // sort by distance
                .filter { it.key < 1.5f.pow(2) } // filter only components closer than a block and a half
                .firstNotNullOfOrNull { it.value } // first not null component, or null if there is no component
                ?: run { // handle no component found
                    click.event.player.sendActionBar("<red>There is no component here".fromMini)
                    return
                }
            val question = component.question.withScope(0)
            click.event.player.sendMessage(question)
        }

        override val item: ItemStack = run {
            val entityItem = ItemStack(Material.BRUSH)
            entityItem.editMeta {
                it.displayName(ComponentUtil.toComponent("<yellow>Component Tool"))
                it.lore(mutableListOf(
                    "<gray>Right click to add mob at your location".fromMini,
                    "<gray>Left click to delete mobs at clicked block".fromMini
                ))
                it.persistentDataContainer.set(
                    DungeonManager.EDIT_TOOL,
                    PersistentDataType.STRING,
                    this::class.simpleName!!.lowercase()
                )
            }
            entityItem
        }
    }
}

fun getCuboidFromNewCorner(region: Box, newCorner: Vector3f): Box {
    val corners = getCuboidOppositeMap(region)
    var closest = corners.keys.first()
    var closestDistance = closest.distance(newCorner)
    for (corner in corners.keys) {
        val distance = corner.distance(newCorner)
        if (distance < closestDistance) {
            closest = corner
            closestDistance = distance
        }
    }
    val mapping = corners[closest]!!
    val finalClosest = closest
    val opposite = corners.entries.first { e->
        e.value == mapping && e.key !== finalClosest
    }.key
    if (newCorner.distance(opposite) < Box.MINIMUM_DIAGONAL) return region
    return Box(newCorner, opposite)
}

fun getCuboidOppositeMap(box: Box): Map<Vector3f, Int> {
    val min = box.minimum
    val max = box.maximum
    val minX = min.x
    val minY = min.y
    val minZ = min.z
    val maxX = max.x
    val maxY = max.y
    val maxZ = max.z
    return mutableMapOf(
        Vector3f(minX, minY, minZ) to 0,
        Vector3f(minX, minY, maxZ) to 1,
        Vector3f(minX, maxY, minZ) to 2,
        Vector3f(minX, maxY, maxZ) to 3,
        Vector3f(maxX, minY, minZ) to 3,
        Vector3f(maxX, minY, maxZ) to 2,
        Vector3f(maxX, maxY, minZ) to 1,
        Vector3f(maxX, maxY, maxZ) to 0
    )
}