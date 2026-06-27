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
import tech.qhuyy.hqngOrder.model.BuyOrder

class MarketGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    fun open(player: Player) {
        val screenConfig = plugin.guiConfigManager.getScreen("market")
        val title = miniMessage.deserialize(screenConfig.title)
        val rows = screenConfig.rows.coerceIn(1, 6)

        val gui: PaginatedGui = Gui.paginated()
            .title(title)
            .rows(rows)
            .pageSize(36)
            .create()

        gui.setDefaultClickAction { event -> event.isCancelled = true }

        val borderItem = ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE)
            .name(Component.text(" "))
            .asGuiItem { event -> event.isCancelled = true }
        gui.filler.fillBorder(borderItem)

        makeItem("market", "info_book")?.let { gui.setItem(4, it) }

        val currentCategory = plugin.guiHandler.getCategory(player.uniqueId)
        val filters = mapOf(
            45 to "filter_all", 46 to "filter_blocks", 47 to "filter_tools",
            48 to "filter_combat", 49 to "filter_misc"
        )
        val categoryMap = mapOf(45 to "all", 46 to "blocks", 47 to "tools", 48 to "combat", 49 to "misc")
        for ((slot, key) in filters) {
            makeClickable("market", key) {
                plugin.guiHandler.setCategory(player.uniqueId, categoryMap[slot] ?: "all")
                plugin.guiHandler.openMarketGUI(player)
            }?.let { gui.setItem(slot, it) }
        }

        makeClickable("market", "your_orders") { plugin.guiHandler.openYourOrdersGUI(player) }?.let {
            gui.setItem(
                50,
                it
            )
        }

        makeClickable("market", "previous_page") { gui.previous() }?.let { gui.setItem(51, it) }
        makeClickable("market", "next_page") { gui.next() }?.let { gui.setItem(52, it) }

        if (player.hasPermission("hqngorder.admin")) {
            makeClickable(
                "market",
                "admin_panel"
            ) { plugin.guiHandler.openAdminPanelGUI(player) }?.let { gui.setItem(53, it) }
        }

        val orders = plugin.ordersManager.getActiveOrders()
        val filtered = if (currentCategory == "all") orders
        else orders.filter { matchesCategory(it, currentCategory) }

        val formatter = plugin.miniMessageFormatter
        for (order in filtered) {
            val item = order.itemStack.clone()
            val loreLines: MutableList<net.kyori.adventure.text.Component> = mutableListOf()
            loreLines.add(
                formatter.deserializeKey(
                    "gui.market.seller-line",
                    TagResolver.resolver(
                        "seller_name",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.buyerName))
                    )
                )
            )
            loreLines.add(
                formatter.deserializeKey(
                    "gui.market.needed-line",
                    TagResolver.resolver(
                        "amount",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.remainingAmount))
                    )
                )
            )
            loreLines.add(
                formatter.deserializeKey(
                    "gui.market.price-line",
                    TagResolver.resolver(
                        "price",
                        Tag.inserting(net.kyori.adventure.text.Component.text(plugin.economyManager.formatAmount(order.pricePerItem)))
                    )
                )
            )
            loreLines.add(
                formatter.deserializeKey(
                    "gui.market.total-line",
                    TagResolver.resolver(
                        "total",
                        Tag.inserting(net.kyori.adventure.text.Component.text(plugin.economyManager.formatAmount(order.pricePerItem * order.remainingAmount)))
                    )
                )
            )
            if (order.isLocked()) {
                loreLines.add(formatter.deserializeKey("gui.market.locked-line"))
            }
            loreLines.add(net.kyori.adventure.text.Component.empty())
            loreLines.add(formatter.deserializeKey("gui.market.click-hint"))

            val guiItem = ItemBuilder.from(item)
                .amount(order.remainingAmount.coerceIn(1, 64))
                .lore(loreLines.toList())
                .asGuiItem { event ->
                    event.isCancelled = true
                    val clicker = event.whoClicked as? Player ?: return@asGuiItem
                    handleOrderClick(clicker, order)
                }
            gui.addItem(guiItem)
        }

        gui.open(player)
    }

    private fun matchesCategory(order: BuyOrder, category: String): Boolean {
        val m = order.itemStack.type
        return when (category) {
            "blocks" -> m.isBlock
            "tools" -> m.name.endsWith("_AXE") || m.name.endsWith("_PICKAXE") ||
                    m.name.endsWith("_SHOVEL") || m.name.endsWith("_HOE") ||
                    m.name.contains("FISHING") || m.name.contains("SHEARS") || m.name.contains("FLINT")

            "combat" -> m.name.contains("SWORD") || m.name.contains("BOW") ||
                    m.name.contains("CROSSBOW") || m.name.contains("TRIDENT") ||
                    m.name.endsWith("_HELMET") || m.name.endsWith("_CHESTPLATE") ||
                    m.name.endsWith("_LEGGINGS") || m.name.endsWith("_BOOTS") || m.name.contains("SHIELD")

            "misc" -> {
                val isBlock = m.isBlock
                val isTool = m.name.endsWith("_AXE") || m.name.endsWith("_PICKAXE") ||
                        m.name.endsWith("_SHOVEL") || m.name.endsWith("_HOE") ||
                        m.name.contains("FISHING") || m.name.contains("SHEARS") || m.name.contains("FLINT")
                val isCombat = m.name.contains("SWORD") || m.name.contains("BOW") ||
                        m.name.contains("CROSSBOW") || m.name.contains("TRIDENT") ||
                        m.name.endsWith("_HELMET") || m.name.endsWith("_CHESTPLATE") ||
                        m.name.endsWith("_LEGGINGS") || m.name.endsWith("_BOOTS") || m.name.contains("SHIELD")
                !isBlock && !isTool && !isCombat
            }

            else -> true
        }
    }

    private fun handleOrderClick(player: Player, order: BuyOrder) {
        if (order.buyerUuid == player.uniqueId) {
            plugin.miniMessageFormatter.sendMessage(player, "gui.market.cannot-deliver-self")
            return
        }
        if (order.isLocked()) {
            plugin.miniMessageFormatter.sendMessage(player, "gui.market.order-being-delivered")
            return
        }
        if (plugin.ordersManager.lockOrder(order, player.uniqueId)) {
            plugin.guiHandler.openDeliverItemsGUI(player, order)
        }
    }

    private fun makeItem(screen: String, key: String): GuiItem? {
        val config = plugin.guiConfigManager.getItem(screen, key) ?: return null
        val mat = try {
            Material.valueOf(config.material.uppercase())
        } catch (e: Exception) {
            Material.STONE
        }
        val builder = ItemBuilder.from(mat).amount(config.amount.coerceIn(1, 64))
        if (config.name != null) builder.name(miniMessage.deserialize(config.name))
        if (config.lore != null) builder.lore(config.lore.map { miniMessage.deserialize(it) })
        return builder.asGuiItem { event -> event.isCancelled = true }
    }

    private fun makeClickable(screen: String, key: String, action: () -> Unit): GuiItem? {
        val config = plugin.guiConfigManager.getItem(screen, key) ?: return null
        val mat = try {
            Material.valueOf(config.material.uppercase())
        } catch (e: Exception) {
            Material.STONE
        }
        val builder = ItemBuilder.from(mat).amount(config.amount.coerceIn(1, 64))
        if (config.name != null) builder.name(miniMessage.deserialize(config.name))
        if (config.lore != null) builder.lore(config.lore.map { miniMessage.deserialize(it) })
        return builder.asGuiItem { event -> event.isCancelled = true; action() }
    }
}

