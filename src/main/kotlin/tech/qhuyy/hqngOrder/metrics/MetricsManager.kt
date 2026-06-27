package tech.qhuyy.hqngOrder.metrics

import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import org.bukkit.Bukkit
import tech.qhuyy.hqngOrder.HqngOrder
import java.util.concurrent.atomic.AtomicBoolean

class MetricsManager(
    private val plugin: HqngOrder,
    private val pluginId: Int
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!plugin.configManager.getMetrics()) {
            plugin.logger.info("bStats metrics disabled via config.")
            return
        }

        if (!started.compareAndSet(false, true)) return

        Metrics(plugin, pluginId)

        plugin.logger.info("bStats metrics enabled.")
    }
}