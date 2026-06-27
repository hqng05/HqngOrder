package tech.qhuyy.hqngOrder.economy

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.model.Software
import java.util.*
import java.util.logging.Level

class EconomyManager(
    private val plugin: HqngOrder,
    private val platform: Software
) {
    lateinit var provider: EconomyProvider

    fun init() {
        runCatching {
            EconomyRegistry(plugin, platform).resolve()
        }.onSuccess { resolved ->
            provider = resolved
            plugin.logger.info("Economy provider initialized successfully")
        }.onFailure { e ->
            plugin.logger.log(Level.SEVERE, "Failed to initialize economy provider: ${e.message}")
            plugin.server.pluginManager.disablePlugin(plugin)
        }
    }

    fun getBalance(player: OfflinePlayer): Double {
        return provider.getBalance(player)
    }

    fun getBalance(uuid: UUID): Double {
        return getBalance(Bukkit.getOfflinePlayer(uuid))
    }

    fun checkAndDeduct(player: OfflinePlayer, amount: Double): Boolean {
        if (!hasEnough(player, amount)) return false
        return withdraw(player, amount)
    }

    fun hasEnough(player: OfflinePlayer, amount: Double): Boolean {
        return provider.hasBalance(player, amount)
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        return provider.withdraw(player, amount)
    }

    fun deposit(uuid: UUID, amount: Double): Boolean {
        return deposit(Bukkit.getOfflinePlayer(uuid), amount)
    }

    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        return provider.deposit(player, amount)
    }

    fun formatAmount(amount: Double): String = provider.formatAmount(amount)

    fun currencyNamePlural(): String = provider.currencyNamePlural()

    fun currencyNameSingular(): String = provider.currencyNameSingular()
}