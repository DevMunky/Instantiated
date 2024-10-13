package dev.munky.instantiated.data.loader

import com.google.gson.Gson
import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.data.DataFile
import dev.munky.instantiated.exception.DataSyntaxException
import dev.munky.instantiated.exception.DungeonExceptions
import dev.munky.instantiated.exception.DungeonExceptions.Companion.DataSyntax
import java.util.concurrent.atomic.AtomicBoolean

abstract class DataFileLoader(fileName: String): DataFile(fileName) {
    private val datumName = fileName.split(".").first()

    protected val GSON: Gson = Gson().newBuilder().setPrettyPrinting().serializeNulls().create()

    var lastLoadResult: DataOperationResult = DataOperationResult.UNDEFINED
    protected var saving = AtomicBoolean(false)

    fun load(): DataOperationResult {
        var cache: ByteArray? = null
        var result: DataOperationResult
        try{
            super.init()
            cache = data.getOrThrow()
            if (cache.isEmpty()) throw IllegalStateException("File is empty, which should not be. If it was empty i should have created the default one...")
            result = load0(cache)
        }catch(t: Throwable) {
            t.log("Error while loading data in '${this::class.simpleName}'")
            if (cache != null){
                file.outputStream().write(cache)
            }
            result = DataOperationResult.FAILURE
        }
        lastLoadResult = result
        return lastLoadResult
    }

    protected abstract fun load0(data: ByteArray): DataOperationResult

    fun save(): DataOperationResult = save(false)

    internal fun save(force: Boolean): DataOperationResult {
        if (saving.get()) {
            return DataOperationResult.UNDEFINED
        }
        saving.set(true)
        var result : DataOperationResult
        try{
            if (!force && lastLoadResult != DataOperationResult.SUCCESS)
                throw DungeonExceptions.Generic.consume("load failed for $datumName, therefore saving is disabled")
            if (!file.exists() || !file.isFile) throw DungeonExceptions.DungeonDataFileNotFound.consume(file)
            result = save0(force)
        }catch(t: Throwable) {
            t.log("Error while saving data: ")
            result = DataOperationResult.FAILURE
        }finally {
            saving.set(false)
        }
        return result
    }

    protected abstract fun save0(force: Boolean): DataOperationResult
}

@Throws(DataSyntaxException::class)
inline fun <reified T: Any> checkType(obj: Any, name: String): T {
    return obj as? T ?: throw DataSyntax.consume("$name is a '${obj::class.simpleName}', not a ${T::class.simpleName}")
}

enum class DataOperationResult{
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE,
    UNDEFINED
}