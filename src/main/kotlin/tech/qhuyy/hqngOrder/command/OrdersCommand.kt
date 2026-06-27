package tech.qhuyy.hqngOrder.command

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.utils.MiniMessageFormatter

class OrdersCommand(
    private val plugin: HqngOrder,
    private val formatter: MiniMessageFormatter
) : CommandExecutor, TabCompleter {

    private val miniMessage = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {

        if (sender !is Player) {
            sender.sendMessage(miniMessage.deserialize("<red>Only players can use this command.</red>"))
            return true
        }

        val player: Player = sender

        if (!player.hasPermission("hqngorder.use")) {
            formatter.sendMessage(player, "no-permission")
            return true
        }

        when {
            args.isEmpty() -> {

                plugin.guiHandler.openMarketGUI(player)
            }
            args[0].equals("admin", ignoreCase = true) -> {

                if (!player.hasPermission("hqngorder.admin")) {
                    formatter.sendMessage(player, "no-permission")
                    return true
                }
                plugin.guiHandler.openAdminPanelGUI(player)
            }
            args[0].equals("reload", ignoreCase = true) -> {

                if (!player.hasPermission("hqngorder.admin")) {
                    formatter.sendMessage(player, "no-permission")
                    return true
                }
                reloadPlugin(player)
            }
            else -> {

                plugin.guiHandler.openMarketGUI(player)
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String> {
        if (args.size == 1 && sender.hasPermission("hqngorder.admin")) {
            return listOf("admin", "reload").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }

    private fun reloadPlugin(player: Player) {
        plugin.logger.info("Reloading configuration requested by ${player.name}")

        try {
            plugin.configManager.reload()
            plugin.messageManager.reloadMessagesConfig()
            plugin.guiConfigManager.reload()
            plugin.logger.info("Configuration reloaded successfully")
            formatter.sendMessage(player, "plugin-reloaded")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload configuration: ${e.message}")
            player.sendMessage(miniMessage.deserialize("<red>❌ Failed to reload configuration. Check console for details.</red>"))
        }
    }
}

