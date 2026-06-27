package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.manager.OrdersManager
import tech.qhuyy.hqngOrder.model.DeliverySession

class ConfirmDeliveryGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    fun open(player: Player, session: DeliverySession) {
        val order = session.order
        val itemsToDeliver = session.getDepositedItems()
        val totalAmount = itemsToDeliver.sumOf { it.amount }
        val actualDeliverable = minOf(totalAmount, order.remainingAmount)
        val totalPayment = actualDeliverable * order.pricePerItem
        val paymentFormatted = plugin.economyManager.formatAmount(totalPayment)
        val cfg = plugin.guiConfigManager.getScreen("confirm_delivery")

        val gui = Gui.gui().title(miniMessage.deserialize(cfg.title)).rows(3).create()
        gui.setDefaultClickAction { event -> event.isCancelled = true }

        makeItem("confirm_delivery", "border")?.let { gui.filler.fillBorder(it) }

        makeClick("confirm_delivery", "confirm") {
            val result = plugin.ordersManager.completeDelivery(player, session)
            player.closeInventory()
            when (result) {
                is OrdersManager.DeliveryResult.SUCCESS -> {
                    val msg = plugin.messageManager.getString("delivery-success", "<green>✅ Delivery successful!</green>")
                        ?: "<green>✅ Delivery successful!</green>"
                    player.sendMessage(miniMessage.deserialize(msg.replace("<payment>", paymentFormatted)))
                }
                is OrdersManager.DeliveryResult.FAILURE -> player.sendMessage(miniMessage.deserialize("<red>❌ ${result.reason}</red>"))
                is OrdersManager.DeliveryResult.stash_full -> {
                    player.sendMessage(miniMessage.deserialize("<red>❌ Buyer's stash is full!</red>"))
                    returnItems(player, itemsToDeliver)
                }
            }
            plugin.guiHandler.removeDeliverySession(player.uniqueId)
        }?.let { gui.setItem(11, it) }

        val sumLore = mutableListOf<Component>()
        sumLore.add(miniMessage.deserialize("<gray>To: <yellow>${order.buyerName}</yellow></gray>"))
        sumLore.add(miniMessage.deserialize("<gray>Item: <yellow>${order.itemStack.type.name}</yellow></gray>"))
        sumLore.add(miniMessage.deserialize("<gray>Qty: <yellow>$actualDeliverable</yellow> / <yellow>${order.remainingAmount}</yellow></gray>"))
        sumLore.add(miniMessage.deserialize("<gray>Payment: <yellow>$paymentFormatted</yellow></gray>"))
        sumLore.add(Component.empty())
        sumLore.add(miniMessage.deserialize("<gray>Items go to buyer's stash</gray>"))
        gui.setItem(13, ItemBuilder.from(Material.PAPER)
            .name(miniMessage.deserialize("<gold>📦 Delivery Summary</gold>"))
            .lore(sumLore.toList())
            .asGuiItem { event -> event.isCancelled = true })

        makeClick("confirm_delivery", "cancel") {
            returnItems(player, itemsToDeliver)
            plugin.ordersManager.unlockOrder(order)
            plugin.guiHandler.removeDeliverySession(player.uniqueId)
            player.closeInventory()
            player.sendMessage(miniMessage.deserialize("<red>❌ Delivery cancelled, items returned</red>"))
        }?.let { gui.setItem(15, it) }

        gui.open(player)
    }

    private fun returnItems(player: Player, items: List<ItemStack>) {
        for (item in items) {
            val leftover = player.inventory.addItem(item)
            if (leftover.isNotEmpty()) {
                leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
            }
        }
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

