package tech.qhuyy.hqngOrder.model

import org.bukkit.inventory.ItemStack
import java.util.*

data class BuyOrder(
    val id: Int,
    val buyerUuid: UUID,
    val buyerName: String,
    val itemStack: ItemStack,
    var amountNeeded: Int,
    var amountFulfilled: Int,
    val pricePerItem: Double,
    val currency: String = "MONEY",
    val createdAt: Long,
    val expiryTime: Long,
    var status: String,
    var lockedBy: UUID? = null,
    var lockTime: Long = 0L
) {
    val remainingAmount: Int
        get() = (amountNeeded - amountFulfilled).coerceAtLeast(0)

    fun isExpired(): Boolean = System.currentTimeMillis() > expiryTime

    fun isLocked(): Boolean {
        if (lockedBy == null) return false
        return System.currentTimeMillis() - lockTime < 300_000L
    }
}
