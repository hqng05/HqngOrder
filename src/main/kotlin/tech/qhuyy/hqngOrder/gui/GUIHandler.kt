package tech.qhuyy.hqngOrder.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.model.BuyOrder
import tech.qhuyy.hqngOrder.model.DeliverySession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GUIHandler(private val plugin: HqngOrder) {

    private val activeDeliverySessions = ConcurrentHashMap<UUID, DeliverySession>()

    fun addDeliverySession(playerUuid: UUID, session: DeliverySession) {
        activeDeliverySessions[playerUuid] = session
    }

    fun getDeliverySession(playerUuid: UUID): DeliverySession? {
        return activeDeliverySessions[playerUuid]
    }

    fun removeDeliverySession(playerUuid: UUID) {
        activeDeliverySessions.remove(playerUuid)
    }

    fun openMarketGUI(player: Player) {
        MarketGUI(plugin).open(player)
    }

    fun openYourOrdersGUI(player: Player) {
        YourOrdersGUI(plugin).open(player)
    }

    fun openItemSelectorGUI(player: Player) {
        ItemSelectorGUI(plugin).open(player)
    }

    fun openItemEditGUI(player: Player, itemStack: ItemStack) {
        ItemEditGUI(plugin).open(player, itemStack)
    }

    fun openEnchantSelectorGUI(player: Player, itemStack: ItemStack) {
        EnchantSelectorGUI(plugin).open(player, itemStack)
    }

    fun openConfirmOrderGUI(player: Player, itemStack: ItemStack, amount: Int, price: Double) {
        ConfirmOrderGUI(plugin).open(player, itemStack, amount, price)
    }

    fun openDeliverItemsGUI(player: Player, order: BuyOrder) {
        DeliverItemsGUI(plugin).open(player, order)
    }

    fun openConfirmDeliveryGUI(player: Player, session: DeliverySession) {
        ConfirmDeliveryGUI(plugin).open(player, session)
    }

    fun openAdminPanelGUI(player: Player) {
        AdminPanelGUI(plugin).open(player)
    }
}

