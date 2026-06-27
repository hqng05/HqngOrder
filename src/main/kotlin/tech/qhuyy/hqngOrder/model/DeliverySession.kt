package tech.qhuyy.hqngOrder.model

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

data class DeliverySession(
    val order: BuyOrder,
    val inv: Inventory,
    val isConfirming: Boolean = false
) {
    fun getDepositedItems(): List<ItemStack> {
        return inv.contents.filterNotNull()
            .filter { !it.type.isAir }
            .toList()
    }
}
