package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import java.util.Locale
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tech.qhuyy.hqngOrder.HqngOrder

class ConfirmOrderGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    fun open(player: Player, itemStack: ItemStack, amount: Int, price: Double) {
        val totalCost = amount * price
        val economy = plugin.economyManager
        val totalFormatted = economy.formatAmount(totalCost)
        val priceFormatted = economy.formatAmount(price)
        val cfg = plugin.guiConfigManager.getScreen("confirm_order")

        val gui = Gui.gui().title(miniMessage.deserialize(cfg.title)).rows(3).create()
        gui.setDefaultClickAction { event -> event.isCancelled = true }

        makeItem("confirm_order", "border")?.let { gui.filler.fillBorder(it) }

        makeClick("confirm_order", "confirm") {
            if (!economy.hasEnough(player, totalCost)) {
                plugin.miniMessageFormatter.sendMessage(player, "orders.confirm.not-enough-money")
                return@makeClick
            }
            val order = plugin.ordersManager.createOrder(player, itemStack, amount, price)
            if (order != null) {
                plugin.miniMessageFormatter.sendMessage(
                    player, "order-created",
                    TagResolver.resolver(
                        "order_id",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.id.toString()))
                    )
                )
                player.closeInventory()
                plugin.foliaLib.scheduler.runNextTick { plugin.guiHandler.openMarketGUI(player) }
            } else {
                plugin.miniMessageFormatter.sendMessage(player, "orders.confirm.failed-to-create")
            }
        }?.let { gui.setItem(11, it) }

        val previewItem = itemStack.clone().apply { this.amount = amount.coerceIn(1, maxStackSize) }
        val balance = economy.formatAmount(economy.getBalance(player))
        val formatter = plugin.miniMessageFormatter
        val preLore = mutableListOf<net.kyori.adventure.text.Component>()
        preLore.add(
            formatter.deserializeKey(
                "gui.confirm-order.item-line",
                TagResolver.resolver(
                    "item_name",
                    Tag.inserting(net.kyori.adventure.text.Component.text(itemStack.type.name))
                )
            )
        )
        preLore.add(
            formatter.deserializeKey(
                "gui.confirm-order.quantity-line",
                TagResolver.resolver("amount", Tag.inserting(net.kyori.adventure.text.Component.text(amount)))
            )
        )
        preLore.add(
            formatter.deserializeKey(
                "gui.confirm-order.price-line",
                TagResolver.resolver("price", Tag.inserting(net.kyori.adventure.text.Component.text(priceFormatted)))
            )
        )
        preLore.add(
            formatter.deserializeKey(
                "gui.confirm-order.total-line",
                TagResolver.resolver("total", Tag.inserting(net.kyori.adventure.text.Component.text(totalFormatted)))
            )
        )
        preLore.add(net.kyori.adventure.text.Component.empty())
        preLore.add(
            formatter.deserializeKey(
                "gui.confirm-order.balance-line",
                TagResolver.resolver("balance", Tag.inserting(net.kyori.adventure.text.Component.text(balance)))
            )
        )
        gui.setItem(
            13, ItemBuilder.from(previewItem)
                .name(formatter.deserializeKey("gui.confirm-order.summary-name"))
                .lore(preLore.toList())
                .asGuiItem { event -> event.isCancelled = true })

        makeClick("confirm_order", "cancel") {
            player.closeInventory()
            plugin.foliaLib.scheduler.runNextTick { plugin.guiHandler.openMarketGUI(player) }
        }?.let { gui.setItem(15, it) }

        gui.open(player)
    }

    private fun makeItem(screen: String, key: String): GuiItem? {
        val c = plugin.guiConfigManager.getItem(screen, key) ?: return null
        val m = try {
            Material.valueOf(c.material.uppercase(Locale.ROOT))
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Invalid material '${c.material}' in $screen.$key, falling back to STONE")
            Material.STONE
        }
        val b = ItemBuilder.from(m).amount(c.amount.coerceIn(1, 64))
        if (c.name != null) b.name(miniMessage.deserialize(c.name))
        if (c.lore != null) b.lore(c.lore.map { miniMessage.deserialize(it) })
        return b.asGuiItem { event -> event.isCancelled = true }
    }

    private fun makeClick(screen: String, key: String, action: () -> Unit): GuiItem? {
        val c = plugin.guiConfigManager.getItem(screen, key) ?: return null
        val m = try {
            Material.valueOf(c.material.uppercase(Locale.ROOT))
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Invalid material '${c.material}' in $screen.$key, falling back to STONE")
            Material.STONE
        }
        val b = ItemBuilder.from(m).amount(c.amount.coerceIn(1, 64))
        if (c.name != null) b.name(miniMessage.deserialize(c.name))
        if (c.lore != null) b.lore(c.lore.map { miniMessage.deserialize(it) })
        return b.asGuiItem { event -> event.isCancelled = true; action() }
    }
}

