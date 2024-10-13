@file:JvmName("Util")
@file:JvmMultifileClass
package dev.munky.instantiated.util

import com.sk89q.worldedit.math.BlockVector3
import dev.munky.instantiated.common.util.formatException
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.util.Vector
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector3i

fun BlockVector3.toLocation(world : org.bukkit.World) : Location {
    return Location(world, this.x().toDouble(), this.y().toDouble(), this.z().toDouble())
}

fun Location.toBlockVector3() : BlockVector3 {
    return BlockVector3.at(this.x, this.y, this.z)
}

fun Location.toVector3() : com.sk89q.worldedit.math.Vector3 {
    return com.sk89q.worldedit.math.Vector3.at(this.x, this.y, this.z)
}

val Location.toVector3d : Vector3d get() = Vector3d(this.x, this.y, this.z)

val Location.toVector3f : Vector3f get() = Vector3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())

val Location.toVector3i : Vector3i get() = Vector3i(this.x.toInt(), this.y.toInt(), this.z.toInt())

val BlockVector3.toVector3d : Vector3d get() = Vector3d(this.x().toDouble(), this.y().toDouble(), this.z().toDouble())

val Vector.toVector3f: Vector3f get() = Vector3f(this.x.toFloat(),this.y.toFloat(),this.z.toFloat())

fun Vector3i.toLocation(world: World): Location = Location(world, this.x.toDouble(), this.y.toDouble(), this.z.toDouble())

fun Vector3f.toLocation(world: World): Location = Location(world, this.x.toDouble(), this.y.toDouble(), this.z.toDouble())

val BlockVector3.toVector3f: Vector3f get() {
    return Vector3f(this.x().toFloat(),this.y().toFloat(),this.z().toFloat())
}

// TODO remove lang message
val String.asComponent : Component get() = ComponentUtil.toComponent(this)

val Component.asString : String get() = ComponentUtil.toString(this)

fun Component.send(s: Audience) = s.sendMessage(this)

fun Component.commandFail() : Nothing {
    throw dev.jorel.commandapi.CommandAPIBukkit.failWithAdventureComponent(this)
}

fun Throwable.stackMessage() : String = this.formatException()

fun setGlowColorFor(entity: Entity, color: NamedTextColor?){
    if (color == null) {
        entity.isGlowing = false
        val board = Bukkit.getScoreboardManager().mainScoreboard.getEntityTeam(entity)
        board?.removeEntity(entity)
        return
    }
    entity.isGlowing = true
    val board = Bukkit.getScoreboardManager().mainScoreboard
    var team = board.getTeam("instantiated-${color}-team")
    if (team == null) {
        team = board.registerNewTeam("instantiated-${color}-team")
    }
    team.color(color)
}