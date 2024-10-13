@file:Suppress("UnstableApiUsage")

package dev.munky.instantiated.dungeon.procedural

import com.google.common.graph.GraphBuilder
import com.google.common.graph.MutableGraph
import com.sk89q.worldedit.math.BlockVector3
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.common.structs.IdType
import dev.munky.instantiated.dungeon.procedural.CardinalDirection.Companion.toCardinalDirection
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.plugin
import dev.munky.instantiated.util.toVector3f
import org.joml.Vector3f
import org.joml.Vector3i
import java.util.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.random.Random

interface ProceduralGenerator {

}

/**
 * Every instanced dungeon will have its own generator instance
 */
class SimpleProceduralGenerator(
    val masterFormat: ProceduralFormat,
    val finalRoomCount : Int
) : ProceduralGenerator {
    // door vectors are relative to BlockVector3.ZERO
    // at any given room Int, i can have a list of doors
    private val currentRooms : MutableGraph<ProceduralDoor> = GraphBuilder.directed().expectedNodeCount(finalRoomCount).allowsSelfLoops(false).build()
    private val currentRoomCount get() = currentRooms.nodes().size
    init {
        // entrance door where players spawn?
        val startingRoom = masterFormat.startingRoom
        val firstDoor = startingRoom.doors.first()
        val startingDoor = ProceduralDoor(
            startingRoom,
            firstDoor,
            startingRoom.box.center.sub(firstDoor.toVector3f).toCardinalDirection(),
            BlockVector3.ZERO
        )
        currentRooms.addNode(startingDoor)
        println("_ starting door = $startingDoor")
    }
    fun generate() {
        // start at one because we count the starting room
        var iterations = 0
        do {
            if (iterations>2000) {
                plugin.logger.warning("Procedural iteration limit reached!")
                break
            }
            val roomToGenerate = masterFormat.rooms.values.random()
            val doorToUseForNewRoom = roomToGenerate.doors.random()
            val dir = roomToGenerate.box.center.sub(doorToUseForNewRoom.toVector3f).toCardinalDirection()
            val doorToExtend = currentRooms.nodes().random()
            val doorDif = doorToExtend.door.subtract(doorToUseForNewRoom)
            val proceduralDoor = ProceduralDoor(roomToGenerate, doorToUseForNewRoom, dir, doorDif)
            iterations++
            try {
                val put = currentRooms.putEdge(doorToExtend, proceduralDoor)
                if (put) println("Added door '$proceduralDoor'")
            } catch (_: Throwable) { } // if self loop, try again!
        } while (currentRooms.edges().size < finalRoomCount - 1) // we start at one because of the starting room
    }
    fun getRoomMap(instancedDungeon: ProceduralInstance) : LinkedHashMap<IdKey,ProceduralRoomInstance> {
        val map = currentRooms.nodes()
            .map {
                it.format.instance(instancedDungeon) as ProceduralRoomInstance
            }.associateBy {
                val newidString = "${it.identifier.key}#${Random.nextInt(Short.MAX_VALUE.toInt())}" // somewhat low change these dont overlap...
                println("adding $newidString to the map...")
                IdKey(IdType.ROOM, newidString)
            }.toMutableMap()
        println("MAp is ${map.keys}")
        return LinkedHashMap(map)
    }
}
// each format can have 1-4 of these
data class ProceduralDoor(
    val format : ProceduralRoomFormat,
    val door : BlockVector3,
    val doorFacingDirection: CardinalDirection,
    val origin : BlockVector3
){
    @Suppress("unused") // need this to keep hash codes unique across procedural doors
    val uuidForUniqueness = UUID.randomUUID()!!
    override fun toString(): String {
        return "ProceduralDoor[${format.identifier}@$origin->$door@$doorFacingDirection]"
    }
}

enum class CardinalDirection {
    NORTH,
    SOUTH,
    EAST,
    WEST;
    companion object{
        @JvmStatic
        fun fromVector(vector: Vector3f) : CardinalDirection {
            val normal = vector.normalize()
            val angle = atan2(normal.z * -1, normal.x) // weird minecraft stuff WOW
            val quadrant = (Math.round( 4 * angle / (2 * PI) + 4 ) % 4).toInt()
            val dir = CardinalDirection.entries[quadrant]
            return dir
        }

        fun Vector3f.toCardinalDirection() : CardinalDirection {
            return fromVector(this)
        }
    }
    val toVector3i : Vector3i = when (this.ordinal) {
        0 -> Vector3i(0,0,-1)
        1 -> Vector3i(0,0,1)
        2 -> Vector3i(1,0,0)
        3 -> Vector3i(-1,0,0)
        else -> throw DungeonExceptions.Generic.consume("Unhandled Cardinal Direction")
    }
}
