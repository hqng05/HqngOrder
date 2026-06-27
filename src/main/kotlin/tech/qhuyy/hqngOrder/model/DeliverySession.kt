package tech.qhuyy.hqngOrder.model

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

data class DeliverySession(
    val order: BuyOrder,
    val inv: Inventory,
    var isConfirming: Boolean = false
) {
    private val infoSlot: Int = 4

    fun getDepositedItems(): List<ItemStack> {
        return inv.contents.filterNotNull()
            .filterIndexed { index, item -> index != infoSlot && !item.type.isAir }
            .toList()
    }

    fun getInfoItem(): ItemStack? = inv.contents.getOrNull(infoSlot)?.takeIf { !it.type.isAir }
}
