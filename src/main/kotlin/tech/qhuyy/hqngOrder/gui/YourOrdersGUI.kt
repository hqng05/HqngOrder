package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
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

        makeClick("your_orders", "back_to_market") { plugin.guiHandler.openMarketGUI(player) }?.let { gui.setItem(45, it) }

        makeClick("your_orders", "create_order") { plugin.guiHandler.openItemSelectorGUI(player) }?.let { gui.setItem(48, it) }

        makeClick("your_orders", "open_stash") { plugin.stashManager.openStash(player) }?.let { gui.setItem(49, it) }

        val playerOrders = plugin.ordersManager.getActiveOrders(player.uniqueId)
        for (order in playerOrders) {
            val price = plugin.economyManager.formatAmount(order.pricePerItem)
            val total = plugin.economyManager.formatAmount(order.pricePerItem * order.remainingAmount)
            val loreLines: MutableList<Component> = mutableListOf()
            loreLines.add(miniMessage.deserialize("<gray>Status: <yellow>${order.status}</yellow></gray>"))
            loreLines.add(miniMessage.deserialize("<gray>Amount: <yellow>${order.amountFulfilled}/${order.amountNeeded}</yellow></gray>"))
            loreLines.add(miniMessage.deserialize("<gray>Price: <yellow>$price</yellow> each</gray>"))
            loreLines.add(miniMessage.deserialize("<gray>Total: <yellow>$total</yellow></gray>"))
            loreLines.add(Component.empty())
            loreLines.add(miniMessage.deserialize("<red><italic>Click to cancel</italic></red>"))

            gui.addItem(ItemBuilder.from(order.itemStack.clone())
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

