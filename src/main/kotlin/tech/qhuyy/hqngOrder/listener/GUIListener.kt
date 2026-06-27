package tech.qhuyy.hqngOrder.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.manager.StashManager

class GUIListener(
    private val plugin: HqngOrder,
    private val stashManager: StashManager
) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        val deliverySession = plugin.guiHandler.getDeliverySession(player.uniqueId)
        if (deliverySession != null && !deliverySession.isConfirming) {

            val clickedInv = event.clickedInventory
            val topInv = player.openInventory.topInventory
            val orderItem = deliverySession.order.itemStack

            if (clickedInv != null && clickedInv == topInv) {
                val item = event.currentItem
                if (item != null && !item.type.isAir) {
                    if (item.type != orderItem.type) {
                        event.isCancelled = true
                        player.sendMessage(plugin.miniMessageFormatter.deserializeKey("item-mismatch"))
                    }
                }
            }

            if (event.isShiftClick && clickedInv != null && clickedInv != topInv) {
                val item = event.currentItem
                if (item != null && !item.type.isAir && item.type != orderItem.type) {
                    event.isCancelled = true
                    player.sendMessage(plugin.miniMessageFormatter.deserializeKey("item-mismatch"))
                }
            }
            return
        }

        if (stashManager.isOpenStash(event.inventory)) {
            return
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return

        val deliverySession = plugin.guiHandler.getDeliverySession(player.uniqueId)
        if (deliverySession != null && !deliverySession.isConfirming) {

            for ((_, slot) in event.newItems) {
                val orderItem = deliverySession.order.itemStack
                if (slot.type != orderItem.type) {
                    event.isCancelled = true
                    player.sendMessage(plugin.miniMessageFormatter.deserializeKey("item-mismatch"))
                    return
                }
            }
            return
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        if (stashManager.isOpenStash(event.inventory)) {
            stashManager.handleStashClose(player, event.inventory)
            return
        }

        val deliverySession = plugin.guiHandler.getDeliverySession(player.uniqueId)
        if (deliverySession != null && !deliverySession.isConfirming) {
            val depositedItems = deliverySession.getDepositedItems()
            if (depositedItems.isEmpty()) {

                plugin.ordersManager.unlockOrder(deliverySession.order)
                plugin.guiHandler.removeDeliverySession(player.uniqueId)
                player.sendMessage(plugin.miniMessageFormatter.deserializeKey("delivery-cancelled"))
            } else {

                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.guiHandler.openConfirmDeliveryGUI(player, deliverySession)
                })
            }
            return
        }
    }
}