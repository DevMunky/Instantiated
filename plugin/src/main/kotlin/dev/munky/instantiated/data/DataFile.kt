package dev.munky.instantiated.data

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.munky.instantiated.common.logging.NotYetInitializedException
import dev.munky.instantiated.data.config.TheConfig
import dev.munky.instantiated.plugin
import org.bukkit.configuration.file.YamlConfiguration
import org.koin.core.component.get
import java.io.File
import java.lang.ref.WeakReference

abstract class DataFile(
    val file: File
) {

    constructor(name: String) : this(getPluginFile(name))

    fun init(){
        tryCreateFile()
        check(file.exists()) { "file ${file.path} does not exist (I tried to make it)" }
        check(file.isFile) { "file ${file.path} is not a file" }
        check(file.canRead()) { "file ${file.path} is not readable" }
    }

    private var _yaml: Result<YamlConfiguration> = Result.failure(NotYetInitializedException())
    private var _json: Result<JsonElement> = Result.failure(NotYetInitializedException())
    private var _data: Result<ByteArray> = Result.failure(NotYetInitializedException())

    private fun tryCreateFile(){
        val resOnly = if (this is TheConfig) false else plugin.get<TheConfig>().resourceDataFiles.value
        if (!file.exists() || file.length() == 0L){
            file.parentFile.mkdirs()
            file.createNewFile()
            plugin.javaClass.classLoader.getResourceAsStream(file.name)?.use { inputStream ->
                file.outputStream().buffered().use { outputStream ->
                    outputStream.write(inputStream.readBytes())
                }
            } ?: if (!resOnly) return else {}
        }
        _data = kotlin.runCatching {
            if (resOnly){
                plugin.javaClass.classLoader.getResourceAsStream(file.name)!!.use {
                    it.readBytes()
                }
            } else file.readBytes()
        }
        _yaml = kotlin.runCatching {
            check(_data.isSuccess) { "file data did not load" }
            val yaml = YamlConfiguration()
            yaml.load(file)
            yaml
        }
        _json = kotlin.runCatching {
            check(_data.isSuccess) { "file data did not load" }
            JsonParser.parseString(String(data.getOrThrow()))
        }
    }

    protected val data get() = _data
    protected val json get() = _json
    protected val yaml get() = _yaml
}

class ConfigurationValue<T : Any>(
    private val path: String,
    private val comment: List<String>,
    private val warningMessage: (Throwable) -> String,
    private val def: T,
    private val getter: (Any) -> T
) {
    private var _value: T? = null

    private var _previous = WeakReference<T?>(null)

    /**
     * Load a value from a configuration
     *
     * @param config the configuration to load from
     */
    fun load(config: YamlConfiguration) {
        config.setComments(path, comment)
        if (this._value == null) loadValue(config)
        else synchronized(this._value!!) { loadValue(config) }
        // to keep asynchronous `gets` during runtime safe
        // probably don't need this but there is no hurt
    }

    fun set(value: T): T {
        val ret = _value
        check(ret != null) { "Tried setting config value before loading it" }
        _value = value
        return ret
    }

    fun push(value: T) {
        check(_previous.refersTo(null)) { "Cannot push consecutively! Please pop before pushing." }
        _previous = WeakReference(_value)
        _value = value
    }

    fun pop() {
        check(!_previous.refersTo(null))
        _value = _previous.get()
    }

    private fun loadValue(config: YamlConfiguration){
        val any = config.get(path)
        val resOnly = if (this == plugin.get<TheConfig>().resourceDataFiles) false else plugin.get<TheConfig>().resourceDataFiles.value
        this._value =
            if (resOnly) def
            else if (any == null) {
                plugin.logger.warning(warningMessage(NullPointerException("Section does not exist and was created")))
                plugin.logger.debug("Failure causing default: Section does not exist")
                config.set(path, "null")
                def
            } else {
                val res: Result<T> = kotlin.runCatching { getter(any) }
                if (res.isFailure) {
                    val exception = res.exceptionOrNull()!!
                    plugin.logger.warning(warningMessage(exception))
                    plugin.logger.debug("Failure causing default: ${exception.message}")
                    def
                } else res.getOrThrow()
            }
        plugin.logger.debug("Loaded config value '${this.path}' -> ${this._value}")
    }

    val value get() = _value ?: throw IllegalStateException("Configuration value '$path' has not been loaded yet")

    val isLoaded: Boolean get() = this._value != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConfigurationValue<*>

        if (path != other.path) return false
        if (def != other.def) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + def.hashCode()
        return result
    }
}

fun getPluginFile(name: String): File = File(plugin.dataFolder.absolutePath + File.separator + name)