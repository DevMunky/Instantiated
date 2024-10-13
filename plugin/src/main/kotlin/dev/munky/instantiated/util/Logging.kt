package dev.munky.instantiated.util

import dev.munky.instantiated.common.util.DebugLogger
import dev.munky.instantiated.common.util.log
import java.util.logging.Level
import java.util.logging.Logger

class CustomLogger(private val debug: () -> Boolean, name: String) : DebugLogger("CustomLogger") {

    private val debugLogger: Logger =
        getLogger("${ConsoleColors.FG.GREEN}Debug - $name${ConsoleColors.RESET}")
    private val severeLogger: Logger =
        getLogger("${ConsoleColors.FG.LIGHT_RED}${ConsoleColors.RAPID_BLINK}Error - $name${ConsoleColors.RESET}")
    private val warningLogger: Logger =
        getLogger("${ConsoleColors.FG.YELLOW}Warning - $name${ConsoleColors.RESET}")
    private val infoLogger: Logger =
        getLogger("${ConsoleColors.FG.BLUE}$name${ConsoleColors.RESET}")
    private val testLogger: Logger =
        getLogger("${ConsoleColors.FG.LIGHT_RED}Tests - $name${ConsoleColors.RESET}")

    private val debugMessage: (String) -> String =
        { "" + ConsoleColors.FG.BEST_BLUE + it + ConsoleColors.RESET }
    private val infoMessage: (String) -> String =
        { "" + ConsoleColors.FG.GREEN + it + ConsoleColors.RESET }
    private val warningMessage: (String) -> String =
        { "" + ConsoleColors.FG.YELLOW + it + ConsoleColors.RESET }
    private val severeMessage: (String) -> String  =
        { "" + ConsoleColors.BG.SEVERE_RED + ConsoleColors.FG.WHITE + it + ConsoleColors.RESET }

    fun test(msg: String?) = if (debug()) testLogger.info(infoMessage(msg ?: "null")) else {}

    override fun debug(msg: String?) = if (debug()) debugLogger.info(debugMessage(msg ?: "null")) else {}

    override fun severe(msg: String?) = severeLogger.severe(severeMessage(msg ?: "null"))

    override fun warning(msg: String?) = warningLogger.warning(warningMessage(msg ?: "null"))

    override fun info(msg: String?) = infoLogger.info(infoMessage(msg ?: "null"))

    override fun log(level: Level?, msg: String?) {
        when (level){
            Level.SEVERE -> severe(msg)
            Level.WARNING -> warning(msg)
            else -> info(msg)
        }
    }

    override fun log(level: Level, msg: String, thrown: Throwable) = thrown.log(msg)

    fun logThrowable(preface: String, thrown: Throwable) = thrown.log(preface)
}

