package dev.munky.instantiated.exception


import dev.munky.instantiated.common.logging.InstantiatedException
import dev.munky.instantiated.common.structs.IdKey
import dev.munky.instantiated.dungeon.sstatic.StaticInstance
import dev.munky.instantiated.util.stackMessage
import java.io.File
import kotlin.reflect.KClass

/**
 * Convenience class for grabbing different kinds of exceptions
 *
 * upon further thinking this class is pretty stupid, not sure why i made it but that is the way of learning and experimenting
 */
class DungeonExceptions {
    companion object{
        @JvmStatic
        val Instantiation = ExceptionFactory<IdKey, InstantiationException>{ id ->
            "could not instance '$id'"
        }
        @JvmStatic
        val DataSyntax = ExceptionFactory<String, DataSyntaxException>{ string ->
            string
        }
        @JvmStatic
        val PhysicalRemoval = ExceptionFactory<StaticInstance, PhysicalRemovalException>{ dungeon->
            "could not remove physical component of dungeon '${dungeon.identifier}'"
        }
        @JvmStatic
        val DungeonDataFileNotFound = ExceptionFactory<File, DungeonDataFileNotFoundException>{
            "dungeon data file '${it.name}' not found"
        }
        @JvmStatic
        val Generic = ExceptionFactory<String, GenericException>{
            it
        }
        @JvmStatic
        val ComponentNotFound = ExceptionFactory<IdKey, DungeonStructureNotFound>{
            "component '$it' not found"
        }
    }
}

inline fun <T: Any,reified E: DungeonException> ExceptionFactory(
    noinline f: (T) -> String
): ExceptionFactory<T,E> = ExceptionFactory(E::class, f)

class ExceptionFactory<T : Any,E : DungeonException>(
    private val exceptionClass : KClass<E>,
    private val msgFunction: (T) -> String
) {
    companion object{
        val exceptions : MutableMap<DungeonException,Long> = HashMap()
    }

    fun consume(datum : T, cause: Throwable? = null) : E {
        val constructor = exceptionClass.java.getConstructor(String::class.java,Throwable::class.java)
        val exception : E = constructor.newInstance(msgFunction(datum),cause)
        exceptions[exception] = System.currentTimeMillis()
        return exception
    }

    fun consume(datum : T) : E = consume(datum,null)
}

abstract class DungeonException(message:String?, cause:Throwable?) : InstantiatedException(
    message ?: cause?.getLastMessage(),
    cause
){
    // for java
    fun getStackMessage() : String {
        return this.stackMessage()
    }
}

class InstantiationException(msg:String, cause:Throwable?) : DungeonException(msg,cause)
class GenericException(msg:String, cause:Throwable?) : DungeonException(msg,cause)
class DataSyntaxException(msg:String,cause:Throwable?) : DungeonException(msg,cause)
class PhysicalRemovalException(msg:String,cause:Throwable?) : DungeonException(msg,cause)
class DungeonDataFileNotFoundException(msg:String,cause:Throwable?) : DungeonException(msg,cause)
class DungeonStructureNotFound(msg:String, cause: Throwable?) : DungeonException(msg,cause)

fun Throwable.getLastMessage() : String {
    return if (this.cause!=null){
        cause!!.getLastMessage()
    }else if (this.message!=null){
        this.message!!
    }else{
        ""
    }
}