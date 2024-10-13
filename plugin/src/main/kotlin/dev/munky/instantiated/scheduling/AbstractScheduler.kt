package dev.munky.instantiated.scheduling

import dev.munky.instantiated.common.util.log
import dev.munky.instantiated.plugin
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import io.papermc.paper.threadedregions.scheduler.ScheduledTask.CancelledState
import io.papermc.paper.threadedregions.scheduler.ScheduledTask.ExecutionState
import io.papermc.paper.util.Tick
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.*
import kotlin.time.Duration
import kotlin.time.toJavaDuration

object Schedulers {
    val ASYNC: AbstractScheduler = AsyncFoliaScheduler()
    val SYNC: AbstractScheduler = SyncFoliaScheduler()
    val CHAT_QUESTION: AbstractScheduler = ChatQuestionScheduler()
    val RENDER: AbstractScheduler = RenderScheduler()
    val COMPONENT_PROCESSING = ComponentProcessor()
}

abstract class AbstractScheduler: Executor{

    override fun execute(command: Runnable) {
        submit { command.run() }
    }

    fun submit(block: (ScheduledTask) -> Unit): ScheduledTask{
        if (!plugin.isEnabled) throw IllegalStateException("Instantiated disabled")
        val task = run0(block)
        return task
    }

    fun submit(later: Duration, block: (ScheduledTask) -> Unit): ScheduledTask{
        if (!plugin.isEnabled) throw IllegalStateException("Instantiated disabled")
        val task = run0(later, block)
        return task
    }

    fun repeat(interval: Duration, block: (ScheduledTask) -> Unit): ScheduledTask{
        if (!plugin.isEnabled) throw IllegalStateException("Instantiated disabled")
        val task = repeat0(interval, block)
        return task
    }

    protected abstract fun run0(block: (ScheduledTask) -> Unit): ScheduledTask
    protected abstract fun run0(later: Duration, block: (ScheduledTask) -> Unit): ScheduledTask
    protected abstract fun repeat0(interval: Duration, block: (ScheduledTask) -> Unit): ScheduledTask
}

class AsyncFoliaScheduler: AbstractScheduler() {
    private val folia get() = Bukkit.getServer().asyncScheduler

    override fun run0(block: (ScheduledTask) -> Unit): ScheduledTask {
        return folia.runNow(plugin){ block(it) }
    }

    override fun run0(later: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        return folia.runDelayed(plugin, { block(it) }, later.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    override fun repeat0(interval: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        val m = interval.inWholeMilliseconds
        return folia.runAtFixedRate(plugin, { block(it) }, m, m, TimeUnit.MILLISECONDS)
    }
}

class SyncFoliaScheduler: AbstractScheduler() {
    private val folia get() = Bukkit.getServer().globalRegionScheduler

    override fun run0(block: (ScheduledTask) -> Unit): ScheduledTask {
        return folia.run(plugin){ block(it) }
    }

    override fun run0(later: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        val delay = Tick.tick().fromDuration(later.toJavaDuration()).toLong()
        return folia.runDelayed(plugin, { block(it) }, delay)
    }

    override fun repeat0(interval: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        val t = Tick.tick().fromDuration(interval.toJavaDuration()).toLong()
        return folia.runAtFixedRate(plugin, { block(it) }, t, t)
    }
}

class ChatQuestionScheduler: AbstractScheduler() {
    private val thread: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        val thread = Thread(it)
        thread.name = "Instantiated Chat Question Processing Thread"
        thread.priority = Thread.NORM_PRIORITY + 1
        thread.setUncaughtExceptionHandler{ th, t ->
            t.log("Error in instantiated chat question processing thread (id=${th.threadId()})")
        }
        thread
    }

    override fun run0(block: (ScheduledTask) -> Unit): ScheduledTask {
        val task = ChatProcessingTask(block, false)
        task.future = thread.schedule(task, 0, TimeUnit.MILLISECONDS)
        return task
    }

    override fun run0(later: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        val task = ChatProcessingTask(block, false)
        task.future = thread.schedule(task, later.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        return task
    }

    override fun repeat0(interval: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        val task = ChatProcessingTask(block, true)
        val millis = interval.inWholeMilliseconds
        task.future = thread.scheduleAtFixedRate(task, millis, millis, TimeUnit.MILLISECONDS)
        return task
    }

    private inner class ChatProcessingTask(
        private val block: (ScheduledTask) -> Unit,
        private val repeat: Boolean
    ): ScheduledTask, () -> Unit {
        var future: ScheduledFuture<*>? = null

        override fun getOwningPlugin(): Plugin = plugin

        override fun isRepeatingTask(): Boolean = repeat

        override fun cancel(): CancelledState {
            val cancelled = future?.cancel(false)
            return if (cancelled != null) {
                CancelledState.NEXT_RUNS_CANCELLED
            }else {
                CancelledState.CANCELLED_ALREADY
            }
        }

        override fun getExecutionState(): ExecutionState {
            val state = future?.state()
            return when (state){
                Future.State.RUNNING -> ExecutionState.RUNNING
                Future.State.SUCCESS -> ExecutionState.FINISHED
                Future.State.FAILED -> ExecutionState.CANCELLED_RUNNING
                Future.State.CANCELLED -> ExecutionState.CANCELLED
                null -> ExecutionState.IDLE
            }
        }

        override fun invoke() {
            try{
                block(this)
            }catch (t: Throwable){
                throw Throwable("Throwable caught during task invocation", t)
            }
        }
    }
}

class RenderScheduler: AbstractScheduler() {
    private val thread: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        val thread = Thread(it)
        thread.name = "Instantiated Edit Mode Render Thread"
        thread.priority = Thread.NORM_PRIORITY + 1
        thread.setUncaughtExceptionHandler{ th, t ->
            t.log("Error in instantiated render thread (id=${th.threadId()})")
        }
        thread
    }

    override fun run0(block: (ScheduledTask) -> Unit): ScheduledTask {
        val task = RenderTask(block, false)
        task.future = thread.schedule(task, 0, TimeUnit.MILLISECONDS)
        return task
    }

    override fun run0(later: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        val task = RenderTask(block, false)
        task.future = thread.schedule(task, later.inWholeNanoseconds, TimeUnit.NANOSECONDS)
        return task
    }

    override fun repeat0(interval: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        val task = RenderTask(block, true)
        val nanos = interval.inWholeNanoseconds
        task.future = thread.scheduleAtFixedRate(task, nanos, nanos, TimeUnit.NANOSECONDS)
        return task
    }

    private inner class RenderTask(
        private val block: (ScheduledTask) -> Unit,
        private val repeat: Boolean
    ): ScheduledTask, () -> Unit {
        var future: ScheduledFuture<*>? = null

        override fun getOwningPlugin(): Plugin = plugin

        override fun isRepeatingTask(): Boolean = repeat

        override fun cancel(): CancelledState {
            val cancelled = future?.cancel(false)
            return if (cancelled != null) {
                CancelledState.NEXT_RUNS_CANCELLED
            }else {
                CancelledState.CANCELLED_ALREADY
            }
        }

        override fun getExecutionState(): ExecutionState {
            val state = future?.state()
            return when (state){
                Future.State.RUNNING -> ExecutionState.RUNNING
                Future.State.SUCCESS -> ExecutionState.FINISHED
                Future.State.FAILED -> ExecutionState.CANCELLED_RUNNING
                Future.State.CANCELLED -> ExecutionState.CANCELLED
                null -> ExecutionState.IDLE
            }
        }

        override fun invoke() {
            try{
                block(this)
            }catch (t: Throwable){
                throw Throwable("Throwable caught during task invocation", t)
            }
        }
    }
}

class ComponentProcessor: AbstractScheduler() {
    private var _thread: Thread? = null

    private val service: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        val thread = Thread(it)
        thread.name = "Instantiated Component Processing Thread"
        thread.priority = Thread.NORM_PRIORITY + 1
        thread.setUncaughtExceptionHandler{ th, t ->
            t.log("Error in instantiated component processing thread (id=${th.threadId()})")
        }
        _thread = thread
        thread
    }

    internal val thread get() = _thread

    override fun run0(block: (ScheduledTask) -> Unit): ScheduledTask {
        val task = ProcessTask(block, false)
        task.future = service.schedule(task, 0, TimeUnit.MILLISECONDS)
        return task
    }

    override fun run0(later: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        val task = ProcessTask(block, false)
        task.future = service.schedule(task, later.inWholeNanoseconds, TimeUnit.NANOSECONDS)
        return task
    }

    override fun repeat0(interval: Duration, block: (ScheduledTask) -> Unit): ScheduledTask {
        val task = ProcessTask(block, true)
        val nanos = interval.inWholeNanoseconds
        task.future = service.scheduleAtFixedRate(task, nanos, nanos, TimeUnit.NANOSECONDS)
        return task
    }

    private inner class ProcessTask(
        private val block: (ScheduledTask) -> Unit,
        private val repeat: Boolean
    ): ScheduledTask, () -> Unit {
        var future: ScheduledFuture<*>? = null

        override fun getOwningPlugin(): Plugin = plugin

        override fun isRepeatingTask(): Boolean = repeat

        override fun cancel(): CancelledState {
            val cancelled = future?.cancel(false)
            return if (cancelled != null) {
                CancelledState.NEXT_RUNS_CANCELLED
            }else {
                CancelledState.CANCELLED_ALREADY
            }
        }

        override fun getExecutionState(): ExecutionState {
            val state = future?.state()
            return when (state){
                Future.State.RUNNING -> ExecutionState.RUNNING
                Future.State.SUCCESS -> ExecutionState.FINISHED
                Future.State.FAILED -> ExecutionState.CANCELLED_RUNNING
                Future.State.CANCELLED -> ExecutionState.CANCELLED
                null -> ExecutionState.IDLE
            }
        }

        override fun invoke() {
            try{
                block(this)
            }catch (t: Throwable){
                throw Throwable("Throwable caught during process invocation", t)
            }
        }
    }
}