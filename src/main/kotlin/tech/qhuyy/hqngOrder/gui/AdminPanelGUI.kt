package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import tech.qhuyy.hqngOrder.HqngOrder

class AdminPanelGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    fun open(player: Player) {
        val cfg = plugin.guiConfigManager.getScreen("admin_panel")
        val gui: PaginatedGui = Gui.paginated()
            .title(miniMessage.deserialize(cfg.title))
            .rows(6).pageSize(36).create()

        gui.setDefaultClickAction { event -> event.isCancelled = true }

        makeItem("admin_panel", "border")?.let { gui.filler.fillBorder(it) }

        makeItem("admin_panel", "info")?.let { gui.setItem(4, it) }

        makeClick("admin_panel", "back") { plugin.guiHandler.openMarketGUI(player) }?.let { gui.setItem(45, it) }

        makeClick("admin_panel", "previous_page") { gui.previous() }?.let { gui.setItem(51, it) }
        makeClick("admin_panel", "next_page") { gui.next() }?.let { gui.setItem(52, it) }

        val formatter = plugin.miniMessageFormatter
        for (order in plugin.ordersManager.getActiveOrders()) {
            val price = plugin.economyManager.formatAmount(order.pricePerItem)
            val total = plugin.economyManager.formatAmount(order.pricePerItem * order.remainingAmount)
            val lore = mutableListOf<net.kyori.adventure.text.Component>()
            lore.add(
                formatter.deserializeKey(
                    "gui.admin-panel.id-line",
                    TagResolver.resolver(
                        "order_id",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.id.toString()))
                    )
                )
            )
            lore.add(
                formatter.deserializeKey(
                    "gui.admin-panel.buyer-line",
                    TagResolver.resolver(
                        "buyer_name",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.buyerName))
                    )
                )
            )
            lore.add(
                formatter.deserializeKey(
                    "gui.admin-panel.amount-line",
                    TagResolver.resolver(
                        "fulfilled",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.amountFulfilled))
                    ),
                    TagResolver.resolver(
                        "needed",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.amountNeeded))
                    )
                )
            )
            lore.add(
                formatter.deserializeKey(
                    "gui.admin-panel.price-line",
                    TagResolver.resolver("price", Tag.inserting(net.kyori.adventure.text.Component.text(price)))
                )
            )
            lore.add(
                formatter.deserializeKey(
                    "gui.admin-panel.total-line",
                    TagResolver.resolver("total", Tag.inserting(net.kyori.adventure.text.Component.text(total)))
                )
            )
            lore.add(net.kyori.adventure.text.Component.empty())
            lore.add(formatter.deserializeKey("gui.admin-panel.force-cancel"))

            gui.addItem(
                ItemBuilder.from(order.itemStack.clone())
                    .amount(order.remainingAmount.coerceIn(1, 64))
                    .lore(lore.toList())
                    .asGuiItem { event ->
                        event.isCancelled = true
                        plugin.ordersManager.adminCancelOrder(player, order)
                        plugin.guiHandler.openAdminPanelGUI(player)
                    })
        }

        gui.open(player)
    }

    private fun makeItem(screen: String, key: String): GuiItem? {
        val c = plugin.guiConfigManager.getItem(screen, key) ?: return null
        val m = try {
            Material.valueOf(c.material.uppercase())
        } catch (e: Exception) {
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
            Material.valueOf(c.material.uppercase())
        } catch (e: Exception) {
            Material.STONE
        }
        val b = ItemBuilder.from(m).amount(c.amount.coerceIn(1, 64))
        if (c.name != null) b.name(miniMessage.deserialize(c.name))
        if (c.lore != null) b.lore(c.lore.map { miniMessage.deserialize(it) })
        return b.asGuiItem { event -> event.isCancelled = true; action() }
    }
}

