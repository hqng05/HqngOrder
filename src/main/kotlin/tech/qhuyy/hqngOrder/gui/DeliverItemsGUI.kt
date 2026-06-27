package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.model.BuyOrder
import tech.qhuyy.hqngOrder.model.DeliverySession

class DeliverItemsGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    fun open(player: Player, order: BuyOrder) {
        val title = plugin.messageManager.getString("delivery-title",
            "<gradient:#FF6B35:#FFA500>⚊ ⚊ ⚊ Deliver Items ⚊ ⚊ ⚊</gradient:>")

        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize(title))

        val infoItem = ItemBuilder.from(Material.BOOK)
            .name(miniMessage.deserialize("<gold>📦 Place matching items here</gold>"))
            .lore(
                miniMessage.deserialize("<gray>Order: <yellow>#${order.id}</yellow></gray>"),
                miniMessage.deserialize("<gray>Needed: <yellow>${order.remainingAmount}x ${order.itemStack.type.name}</yellow></gray>"),
                miniMessage.deserialize("<gray>Close inventory when done</gray>")
            )
            .asGuiItem()
        inventory.setItem(4, infoItem.itemStack)

        val session = DeliverySession(
            order = order,
            inv = inventory,
            isConfirming = false
        )
        plugin.guiHandler.addDeliverySession(player.uniqueId, session)

        player.openInventory(inventory)
    }
}

