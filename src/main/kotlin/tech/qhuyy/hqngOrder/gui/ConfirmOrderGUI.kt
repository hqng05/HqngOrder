package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
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
                player.sendMessage(miniMessage.deserialize("<red>❌ Not enough money!</red>"))
                return@makeClick
            }
            val order = plugin.ordersManager.createOrder(player, itemStack, amount, price)
            if (order != null) {
                plugin.miniMessageFormatter.sendMessage(player, "order-created",
                    TagResolver.resolver("order_id", Tag.inserting(Component.text(order.id.toString()))))
            }
            player.closeInventory()
            plugin.foliaLib.scheduler.runNextTick { plugin.guiHandler.openMarketGUI(player) }
        }?.let { gui.setItem(11, it) }

        val previewItem = itemStack.clone().apply { this.amount = amount.coerceIn(1, maxStackSize) }
        val balance = economy.formatAmount(economy.getBalance(player))
        val preLore = mutableListOf<Component>()
        preLore.add(miniMessage.deserialize("<gray>Item: <yellow>${itemStack.type.name}</yellow></gray>"))
        preLore.add(miniMessage.deserialize("<gray>Quantity: <yellow>$amount</yellow></gray>"))
        preLore.add(miniMessage.deserialize("<gray>Price per item: <yellow>$priceFormatted</yellow></gray>"))
        preLore.add(miniMessage.deserialize("<gray>Total: <yellow>$totalFormatted</yellow></gray>"))
        preLore.add(Component.empty())
        preLore.add(miniMessage.deserialize("<gray>Balance: <yellow>$balance</yellow></gray>"))
        gui.setItem(13, ItemBuilder.from(previewItem)
            .name(miniMessage.deserialize("<gold>📦 Order Summary</gold>"))
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
        val m = try { Material.valueOf(c.material.uppercase()) } catch (e: Exception) { Material.STONE }
        val b = ItemBuilder.from(m).amount(c.amount.coerceIn(1, 64))
        if (c.name != null) b.name(miniMessage.deserialize(c.name))
        if (c.lore != null) b.lore(c.lore.map { miniMessage.deserialize(it) })
        return b.asGuiItem { event -> event.isCancelled = true }
    }

    private fun makeClick(screen: String, key: String, action: () -> Unit): GuiItem? {
        val c = plugin.guiConfigManager.getItem(screen, key) ?: return null
        val m = try { Material.valueOf(c.material.uppercase()) } catch (e: Exception) { Material.STONE }
        val b = ItemBuilder.from(m).amount(c.amount.coerceIn(1, 64))
        if (c.name != null) b.name(miniMessage.deserialize(c.name))
        if (c.lore != null) b.lore(c.lore.map { miniMessage.deserialize(it) })
        return b.asGuiItem { event -> event.isCancelled = true; action() }
    }
}

