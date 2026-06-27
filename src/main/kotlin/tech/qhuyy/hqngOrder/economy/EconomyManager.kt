package tech.qhuyy.hqngOrder.economy

import org.bukkit.OfflinePlayer
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.model.Software

class EconomyManager(
    private val plugin: HqngOrder,
    private val platform: Software
) {
    private var provider: EconomyProvider? = null

    fun init() {
        provider = EconomyRegistry(plugin, platform).resolve()
        if(provider == null) {
            plugin.logger.severe("No economy provider available. Disabling plugin...")
            plugin.server.pluginManager.disablePlugin(plugin)
        }
    }

    fun getBalance(player: OfflinePlayer): Double {
        return provider?.getBalance(player) ?: 0.0
    }

    fun hasBalance(player: OfflinePlayer, amount: Double): Boolean {
        return provider?.hasBalance(player, amount) ?: false
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        return provider?.withdraw(player, amount) ?: false
    }

    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        return provider?.deposit(player, amount) ?: false
    }

    fun formatAmount(amount: Double): String = provider?.formatAmount(amount) ?: "0.00"
}