package tech.qhuyy.hqngOrder.manager

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.database.DatabaseManager
import tech.qhuyy.hqngOrder.enums.OrderStatus
import tech.qhuyy.hqngOrder.model.BuyOrder
import tech.qhuyy.hqngOrder.model.DeliverySession
import tech.qhuyy.hqngOrder.utils.ItemSerializer
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class OrdersManager(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    private val activeOrders = CopyOnWriteArrayList<BuyOrder>()

    private var expiryTask: com.tcoded.folialib.wrapper.task.WrappedTask? = null

    private val databaseManager: DatabaseManager get() = plugin.databaseManager

    fun init() {
        plugin.logger.info("Loading active orders from database...")
        plugin.scope.launchAsync {
            val orders = databaseManager.loadActiveOrders()
            activeOrders.addAll(orders)
            plugin.logger.info("Loaded ${orders.size} active orders")

            plugin.logger.info("Orders manager initialized with ${activeOrders.size} active orders")
        }

        expiryTask = plugin.scope.runTimer(1200L, 1200L) {
            checkExpirations()
        }
    }

    fun shutdown() {
        expiryTask?.cancel()
    }

    fun getActiveOrders(): List<BuyOrder> = activeOrders.toList()

    fun getActiveOrders(buyerUuid: UUID): List<BuyOrder> {
        return activeOrders.filter { it.buyerUuid == buyerUuid }
    }

    fun getOrder(orderId: Int): BuyOrder? {
        return activeOrders.find { it.id == orderId }
            ?: databaseManager.loadOrder(orderId)
    }

    fun createOrder(
        player: Player,
        itemStack: ItemStack,
        amount: Int,
        pricePerItem: Double
    ): BuyOrder? {
        val totalCost = amount * pricePerItem

        if (!plugin.economyManager.checkAndDeduct(player, totalCost)) {
            plugin.miniMessageFormatter.sendMessage(player, "not-enough-money")
            return null
        }

        val now = System.currentTimeMillis()
        val order = BuyOrder(
            id = 0,
            buyerUuid = player.uniqueId,
            buyerName = player.name,
            itemStack = itemStack.clone(),
            amountNeeded = amount,
            amountFulfilled = 0,
            pricePerItem = pricePerItem,
            currency = "MONEY",
            createdAt = now,
            expiryTime = now + parseExpiryString("7d"),
            status = OrderStatus.ACTIVE.name
        )

        val generatedId = databaseManager.createOrder(order)
        if (generatedId <= 0) {

            plugin.economyManager.deposit(player, totalCost)
            return null
        }

        val savedOrder = order.copy(id = generatedId)
        activeOrders.add(savedOrder)

        databaseManager.logTransaction(
            orderId = generatedId,
            playerUuid = player.uniqueId,
            action = "CREATE",
            amount = amount,
            details = "Created order for ${itemStack.type.name} x$amount at $pricePerItem each"
        )

        plugin.logger.info("Order #$generatedId created by ${player.name} for ${itemStack.type.name} x$amount")
        return savedOrder
    }

    fun cancelOrder(player: Player, order: BuyOrder) {

        if (order.buyerUuid != player.uniqueId && !player.hasPermission("hqngorder.admin")) {
            plugin.miniMessageFormatter.sendMessage(player, "no-permission")
            return
        }

        doCancelOrder(order, "CANCELLED", "Cancelled by ${player.name}")
    }

    fun adminCancelOrder(admin: Player, order: BuyOrder) {
        doCancelOrder(order, "CANCELLED", "Admin-cancelled by ${admin.name}")

        val resolver = TagResolver.resolver(
            "order_id", Tag.inserting(net.kyori.adventure.text.Component.text(order.id.toString()))
        )
        plugin.miniMessageFormatter.sendMessage(admin, "admin-cancel-success", resolver)
    }

    private fun doCancelOrder(order: BuyOrder, status: String, reason: String) {
        val remainingAmount = order.remainingAmount
        val refund = remainingAmount * order.pricePerItem

        order.status = status
        activeOrders.remove(order)

        databaseManager.updateOrder(order)

        if (refund > 0) {
            plugin.economyManager.deposit(order.buyerUuid, refund)
        }

        databaseManager.logTransaction(
            orderId = order.id,
            playerUuid = order.buyerUuid,
            action = status,
            amount = remainingAmount,
            details = reason
        )

        plugin.logger.info("Order #${order.id} $status, refunded $refund")

        val buyer = plugin.server.getPlayer(order.buyerUuid)
        if (buyer != null && buyer.isOnline) {
            val resolver = TagResolver.resolver(
                "order_id", Tag.inserting(net.kyori.adventure.text.Component.text(order.id.toString()))
            )
            plugin.miniMessageFormatter.sendMessage(buyer, "order-cancelled", resolver)
        }
    }

    fun lockOrder(order: BuyOrder, sellerUuid: UUID): Boolean {
        if (order.isLocked()) {
            return false
        }

        order.lockedBy = sellerUuid
        order.lockTime = System.currentTimeMillis()

        databaseManager.updateOrder(order)
        return true
    }

    fun unlockOrder(order: BuyOrder) {
        order.lockedBy = null
        order.lockTime = 0L
        databaseManager.updateOrder(order)
    }

    fun completeDelivery(seller: Player, deliverySession: DeliverySession): DeliveryResult {
        val order = deliverySession.order
        val itemsToDeliver = deliverySession.getDepositedItems()

        if (itemsToDeliver.isEmpty()) {
            return DeliveryResult.FAILURE("No items to deliver")
        }

        val totalDelivered = itemsToDeliver.sumOf { it.amount }
        val remainingNeeded = order.remainingAmount
        val actualDelivered = minOf(totalDelivered, remainingNeeded)
        val payment = actualDelivered * order.pricePerItem

        val stash = databaseManager.loadStash(order.buyerUuid).toMutableList()
        val itemsForStash = mutableListOf<ItemStack>()
        var remaining = actualDelivered
        for (item in itemsToDeliver) {
            if (remaining <= 0) break
            val count = minOf(item.amount, remaining)
            val clone = item.clone()
            clone.amount = count
            itemsForStash.add(clone)
            remaining -= count
        }

        var stashFull = false
        val totalStacked = addItemsToStash(stash, itemsForStash)
        if (totalStacked < actualDelivered) {
            stashFull = true
        }

        if (stashFull) {

            unlockOrder(order)
            return DeliveryResult.stash_full
        }

        order.amountFulfilled += actualDelivered

        databaseManager.saveStash(order.buyerUuid, stash.toTypedArray())

        val isComplete = order.amountFulfilled >= order.amountNeeded
        if (isComplete) {
            order.status = OrderStatus.COMPLETED.name
            activeOrders.remove(order)
        }

        databaseManager.updateOrder(order)

        plugin.economyManager.deposit(seller, payment)

        if (!isComplete) {
            unlockOrder(order)
        }

        databaseManager.logTransaction(
            orderId = order.id,
            playerUuid = seller.uniqueId,
            action = "FULFILL",
            amount = actualDelivered,
            details = "Delivered by ${seller.name}, paid $payment"
        )

        plugin.logger.info("Delivery completed for order #${order.id}: $actualDelivered items, paid $payment to ${seller.name}")

        val buyer = plugin.server.getPlayer(order.buyerUuid)
        if (buyer != null && buyer.isOnline) {
            val resolver = TagResolver.resolver(
                TagResolver.resolver(
                    "order_id",
                    Tag.inserting(net.kyori.adventure.text.Component.text(order.id.toString()))
                ),
                TagResolver.resolver(
                    "amount",
                    Tag.inserting(net.kyori.adventure.text.Component.text(actualDelivered.toString()))
                ),
                TagResolver.resolver("seller", Tag.inserting(net.kyori.adventure.text.Component.text(seller.name)))
            )
            buyer.sendMessage(
                miniMessage.deserialize(
                    plugin.messageManager.getMessage("delivery-received"),
                    resolver
                )
            )
        }

        return DeliveryResult.SUCCESS(payment, actualDelivered)
    }

    fun checkExpirations() {
        val expired = activeOrders.filter { it.isExpired() }

        for (order in expired) {
            expireOrder(order)
        }

        if (expired.isNotEmpty()) {
            plugin.logger.info("Expired ${expired.size} orders")
        }
    }

    private fun expireOrder(order: BuyOrder) {
        order.status = OrderStatus.EXPIRED.name
        activeOrders.remove(order)

        val refund = order.remainingAmount * order.pricePerItem
        databaseManager.updateOrder(order)

        if (refund > 0) {
            plugin.economyManager.deposit(order.buyerUuid, refund)
        }

        databaseManager.logTransaction(
            orderId = order.id,
            playerUuid = order.buyerUuid,
            action = "EXPIRE",
            amount = order.remainingAmount,
            details = "Order expired, refunded $refund"
        )

        plugin.logger.info("Order #${order.id} expired, refunded $refund")

        val buyer = plugin.server.getPlayer(order.buyerUuid)
        if (buyer != null && buyer.isOnline) {
            val resolver = TagResolver.resolver(
                "order_id", Tag.inserting(Component.text(order.id.toString()))
            )
            buyer.sendMessage(
                miniMessage.deserialize(
                    plugin.messageManager.getMessage("order-expired"),
                    resolver
                )
            )
        }
    }

    private fun parseExpiryString(input: String): Long {
        val trimmed = input.trim().lowercase()
        return when {
            trimmed.endsWith("d") -> (trimmed.dropLast(1).toLongOrNull() ?: 7) * 86_400_000L
            trimmed.endsWith("h") -> (trimmed.dropLast(1).toLongOrNull() ?: 12) * 3_600_000L
            trimmed.endsWith("m") -> (trimmed.dropLast(1).toLongOrNull() ?: 30) * 60_000L
            trimmed.endsWith("s") -> (trimmed.dropLast(1).toLongOrNull() ?: 60) * 1_000L
            else -> 7 * 86_400_000L
        }
    }

    private fun addItemsToStash(stash: MutableList<ItemStack?>, items: List<ItemStack>): Int {
        var added = 0

        for (itemToAdd in items) {
            var remaining = itemToAdd.amount

            for (i in stash.indices) {
                if (remaining <= 0) break
                val existing = stash[i] ?: continue
                if (ItemSerializer.itemsMatch(existing, itemToAdd) && existing.amount < existing.maxStackSize) {
                    val space = existing.maxStackSize - existing.amount
                    val toAdd = minOf(space, remaining)
                    existing.amount += toAdd
                    remaining -= toAdd
                    added += toAdd
                }
            }

            for (i in stash.indices) {
                if (remaining <= 0) break
                if (stash[i] == null || stash[i]!!.type.isAir) {
                    val clone = itemToAdd.clone()
                    clone.amount = minOf(remaining, itemToAdd.maxStackSize)
                    stash[i] = clone
                    val toAdd = clone.amount
                    remaining -= toAdd
                    added += toAdd
                }
            }

            if (remaining > 0) {

                plugin.logger.warning("$remaining items couldn't fit in stash for order")
            }
        }

        return added
    }

    sealed class DeliveryResult {
        data class SUCCESS(val payment: Double, val itemsDelivered: Int) : DeliveryResult()
        data class FAILURE(val reason: String) : DeliveryResult()
        data object stash_full : DeliveryResult()
    }
}
