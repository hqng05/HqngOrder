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
import tech.qhuyy.hqngOrder.manager.OrdersManager
import tech.qhuyy.hqngOrder.model.DeliverySession

class ConfirmDeliveryGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    fun open(player: Player, session: DeliverySession) {
        session.isConfirming = true
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

        // Build confirm button with resolved placeholder
        val confirmCfg = plugin.guiConfigManager.getItem("confirm_delivery", "confirm")
        if (confirmCfg != null) {
            val m = try {
                Material.valueOf(confirmCfg.material.uppercase(Locale.ROOT))
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Invalid material '${confirmCfg.material}' in confirm_delivery.confirm, falling back to STONE")
                Material.STONE
            }
            val b = ItemBuilder.from(m).amount(confirmCfg.amount.coerceIn(1, 64))
            if (confirmCfg.name != null) b.name(miniMessage.deserialize(confirmCfg.name))
            if (confirmCfg.lore != null) {
                val resolvedLore = confirmCfg.lore.map { line ->
                    line.replace("<total_payment>", paymentFormatted)
                }
                b.lore(resolvedLore.map { miniMessage.deserialize(it) })
            }
            gui.setItem(11, b.asGuiItem { event ->
                event.isCancelled = true
                val result = plugin.ordersManager.completeDelivery(player, session)
                player.closeInventory()
                val formatter = plugin.miniMessageFormatter
                when (result) {
                    is OrdersManager.DeliveryResult.SUCCESS -> {
                        formatter.sendMessage(
                            player, "delivery-success",
                            TagResolver.resolver(
                                "payment",
                                Tag.inserting(net.kyori.adventure.text.Component.text(paymentFormatted))
                            )
                        )
                    }

                    is OrdersManager.DeliveryResult.FAILURE -> {
                        formatter.sendMessage(
                            player, "delivery.failure",
                            TagResolver.resolver(
                                "reason",
                                Tag.inserting(net.kyori.adventure.text.Component.text(result.reason))
                            )
                        )
                    }

                    is OrdersManager.DeliveryResult.stash_full -> {
                        formatter.sendMessage(player, "delivery.stash-full")
                        returnItems(player, itemsToDeliver)
                    }
                }
                plugin.guiHandler.removeDeliverySession(player.uniqueId)
            })
        }

        val formatter = plugin.miniMessageFormatter
        val sumLore = mutableListOf<net.kyori.adventure.text.Component>()
        sumLore.add(
            formatter.deserializeKey(
                "gui.confirm-delivery.to-line",
                TagResolver.resolver(
                    "buyer_name",
                    Tag.inserting(net.kyori.adventure.text.Component.text(order.buyerName))
                )
            )
        )
        sumLore.add(
            formatter.deserializeKey(
                "gui.confirm-delivery.item-line",
                TagResolver.resolver(
                    "item_name",
                    Tag.inserting(net.kyori.adventure.text.Component.text(order.itemStack.type.name))
                )
            )
        )
        sumLore.add(
            formatter.deserializeKey(
                "gui.confirm-delivery.qty-line",
                TagResolver.resolver(
                    "delivered",
                    Tag.inserting(net.kyori.adventure.text.Component.text(actualDeliverable))
                ),
                TagResolver.resolver(
                    "remaining",
                    Tag.inserting(net.kyori.adventure.text.Component.text(order.remainingAmount))
                )
            )
        )
        sumLore.add(
            formatter.deserializeKey(
                "gui.confirm-delivery.payment-line",
                TagResolver.resolver(
                    "payment",
                    Tag.inserting(net.kyori.adventure.text.Component.text(paymentFormatted))
                )
            )
        )
        sumLore.add(net.kyori.adventure.text.Component.empty())
        sumLore.add(formatter.deserializeKey("gui.confirm-delivery.stash-note"))
        gui.setItem(
            13, ItemBuilder.from(Material.PAPER)
                .name(formatter.deserializeKey("gui.confirm-delivery.summary-name"))
                .lore(sumLore.toList())
                .asGuiItem { event -> event.isCancelled = true })

        makeClick("confirm_delivery", "cancel") {
            returnItems(player, itemsToDeliver)
            plugin.ordersManager.unlockOrder(order)
            plugin.guiHandler.removeDeliverySession(player.uniqueId)
            player.closeInventory()
            plugin.miniMessageFormatter.sendMessage(player, "delivery.cancelled")
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
