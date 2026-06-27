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
        return inv.contents
            .mapIndexedNotNull { index, item ->
                if (index == infoSlot || item == null || item.type.isAir) null else item
            }
            .toList()
    }

    fun getInfoItem(): ItemStack? = inv.contents.getOrNull(infoSlot)?.takeIf { !it.type.isAir }
}
