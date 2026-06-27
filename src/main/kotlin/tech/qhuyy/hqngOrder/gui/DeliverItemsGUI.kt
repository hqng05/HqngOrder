package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.model.BuyOrder
import tech.qhuyy.hqngOrder.model.DeliverySession

class DeliverItemsGUI(private val plugin: HqngOrder) {

    fun open(player: Player, order: BuyOrder) {
        val title = plugin.messageManager.getMessage("delivery-title")
        val formatter = plugin.miniMessageFormatter

        val inventory = Bukkit.createInventory(null, 27, formatter.deserialize(title))

        val infoItem = ItemBuilder.from(Material.BOOK)
            .name(formatter.deserializeKey("gui.deliver-items.info-name"))
            .lore(
                formatter.deserializeKey(
                    "gui.deliver-items.info-order-line",
                    TagResolver.resolver(
                        "order_id",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.id.toString()))
                    )
                ),
                formatter.deserializeKey(
                    "gui.deliver-items.info-needed-line",
                    TagResolver.resolver(
                        "amount",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.remainingAmount))
                    ),
                    TagResolver.resolver(
                        "item_name",
                        Tag.inserting(net.kyori.adventure.text.Component.text(order.itemStack.type.name))
                    )
                ),
                formatter.deserializeKey("gui.deliver-items.info-close-hint")
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

