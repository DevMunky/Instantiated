package dev.munky.instantiated.dungeon.sstatic

import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.dungeon.interfaces.Format
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.exception.InstantiationException
import dev.munky.instantiated.plugin
import org.bukkit.Location
import org.joml.Vector3f
import java.io.File

class StaticFormat(
    override val identifier: IdKey,
    var schematic: File?,
    override var spawnVector: Vector3f
) : Format {

    override val instances : MutableSet<StaticInstance> = mutableSetOf()
    override val rooms : MutableMap<IdKey, StaticRoomFormat> = LinkedHashMap()
    @Throws(InstantiationException::class)
    override fun instance(location: Location, option: Format.InstanceOption): StaticInstance {
        try{
            val instance : StaticInstance = when (option) {
                Format.InstanceOption.CACHE -> {
                    plugin.logger.debug("Creating a new cached instance")
                    StaticInstance(this, location, true)
                }
                Format.InstanceOption.NEW_NON_CACHED -> {
                    plugin.logger.debug("Creating a new instance")
                    StaticInstance(this, location, false)
                }
                Format.InstanceOption.CONSUME_CACHE -> {
                    if (cached.isEmpty()) {
                        plugin.logger.debug("Creating a new instance")
                        StaticInstance(this, location, false)
                    } else {
                        plugin.logger.debug("Using a cached instance")
                        cached.first() as StaticInstance
                    }
                }
            }
            instances.add(instance)
            return instance
        }catch (e: Exception){
            throw DungeonExceptions.Instantiation.consume(identifier,e)
        }
    }
}