package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import tech.qhuyy.hqngOrder.HqngOrder

class YourOrdersGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    fun open(player: Player) {
        val screenConfig = plugin.guiConfigManager.getScreen("your_orders")
        val rows = screenConfig.rows.coerceIn(1, 6)

        val gui: PaginatedGui = Gui.paginated()
            .title(miniMessage.deserialize(screenConfig.title))
            .rows(rows)
            .pageSize(36)
            .create()

        gui.setDefaultClickAction { event -> event.isCancelled = true }

        val borderItem = ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE)
            .name(Component.text(" "))
            .asGuiItem { event -> event.isCancelled = true }
        gui.filler.fillBorder(borderItem)

        makeItem("your_orders", "info_book")?.let { gui.setItem(4, it) }

        makeClick("your_orders", "back_to_market") { plugin.guiHandler.openMarketGUI(player) }?.let {
            gui.setItem(
                45,
                it
            )
        }

        makeClick(
            "your_orders",
            "create_order"
        ) { plugin.guiHandler.openItemSelectorGUI(player) }?.let { gui.setItem(48, it) }

        makeClick("your_orders", "open_stash") { plugin.stashManager.openStash(player) }?.let { gui.setItem(49, it) }

        val playerOrders = plugin.ordersManager.getActiveOrders(player.uniqueId)
        val formatter = plugin.miniMessageFormatter
        for (order in playerOrders) {
            val price = plugin.economyManager.formatAmount(order.pricePerItem)
            val total = plugin.economyManager.formatAmount(order.pricePerItem * order.remainingAmount)
            val loreLines: MutableList<net.kyori.adventure.text.Component> = mutableListOf()
            loreLines.add(
                formatter.deserializeKey(
                    "gui.your-orders.status-line",
                    TagResolver.resolver("status", Tag.inserting(net.kyori.adventure.text.Component.text(order.status)))
                )
            )
            loreLines.add(
                formatter.deserializeKey(
                    "gui.your-orders.amount-line",
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
            loreLines.add(
                formatter.deserializeKey(
                    "gui.your-orders.price-line",
                    TagResolver.resolver("price", Tag.inserting(net.kyori.adventure.text.Component.text(price)))
                )
            )
            loreLines.add(
                formatter.deserializeKey(
                    "gui.your-orders.total-line",
                    TagResolver.resolver("total", Tag.inserting(net.kyori.adventure.text.Component.text(total)))
                )
            )
            loreLines.add(net.kyori.adventure.text.Component.empty())
            loreLines.add(formatter.deserializeKey("gui.your-orders.click-cancel"))

            gui.addItem(
                ItemBuilder.from(order.itemStack.clone())
                    .amount(order.remainingAmount.coerceIn(1, 64))
                    .lore(loreLines.toList())
                    .asGuiItem { event ->
                        event.isCancelled = true
                        plugin.ordersManager.cancelOrder(player, order)
                        plugin.guiHandler.openYourOrdersGUI(player)
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

