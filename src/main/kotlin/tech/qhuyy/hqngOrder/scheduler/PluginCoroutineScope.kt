package tech.qhuyy.hqngOrder.scheduler

import com.tcoded.folialib.FoliaLib
import com.tcoded.folialib.wrapper.task.WrappedTask
import kotlinx.coroutines.*
import tech.qhuyy.hqngOrder.HqngOrder
import java.util.logging.Level

class PluginCoroutineScope(
    private val plugin: HqngOrder
) {
    private val foliaLib: FoliaLib = plugin.foliaLib

    val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.Default +
                CoroutineExceptionHandler { _, e ->
                    plugin.logger.log(Level.SEVERE, "Unhandled coroutine exception", e)
                }
    )

    fun runTimer(delay: Long, period: Long, f: () -> Unit): WrappedTask {
        return foliaLib.scheduler.runTimer(f, delay, period)
    }

    fun launchAsync(f: suspend CoroutineScope.() -> Unit) {
        scope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            plugin.logger.log(Level.SEVERE, "Unhandled async exception", e)
        }) {
            f()
        }
    }

    fun cancel() {
        scope.cancel("Plugin disabling")
    }
}