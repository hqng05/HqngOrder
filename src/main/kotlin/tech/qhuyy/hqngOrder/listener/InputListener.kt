package tech.qhuyy.hqngOrder.listener

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.gui.GUIHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InputListener(
    private val plugin: HqngOrder,
    private val guiHandler: GUIHandler
) : Listener {

    private val activeSessions = ConcurrentHashMap<UUID, PlayerInputSession>()
    private val miniMessage = MiniMessage.miniMessage()

    fun startSession(player: Player, itemStack: ItemStack) {
        val session = PlayerInputSession(player = player, itemStack = itemStack, state = InputState.AWAITING_AMOUNT)
        activeSessions[player.uniqueId] = session
        val prompt = plugin.messageManager.getString("prompt-enter-amount", "<yellow>Enter quantity (positive integer):")
        player.sendMessage(miniMessage.deserialize(prompt))
        player.closeInventory()
    }

    fun hasSession(player: Player): Boolean = activeSessions.containsKey(player.uniqueId)
    fun removeSession(player: Player) { removeSession(player.uniqueId) }
    fun removeSession(uuid: UUID) { activeSessions.remove(uuid) }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (hasSession(event.player)) {
            val from = event.from; val to = event.to
            if (from.blockX != to.blockX || from.blockZ != to.blockZ) event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerDropItem(event: PlayerDropItemEvent) { if (hasSession(event.player)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerInteract(event: PlayerInteractEvent) { if (hasSession(event.player)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryOpen(event: org.bukkit.event.inventory.InventoryOpenEvent) {
        if (event.player is Player) {
            if (hasSession(event.player as Player)) event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        if (hasSession(event.player)) {
            event.isCancelled = true
            event.player.sendMessage(miniMessage.deserialize("<red>⛔ Complete your current input first!</red>"))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) { removeSession(event.player) }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val session = activeSessions[player.uniqueId] ?: return
        event.isCancelled = true
        val msg = event.message.trim()

        if (msg.equals("cancel", ignoreCase = true) || msg.equals("exit", ignoreCase = true)) {
            removeSession(player)
            player.sendMessage(miniMessage.deserialize("<red>❌ Input cancelled.</red>"))
            return
        }

        when (session.state) {
            InputState.AWAITING_AMOUNT -> {
                val amount = msg.toIntOrNull()
                if (amount == null || amount <= 0) {
                    player.sendMessage(miniMessage.deserialize("<red>❌ Invalid amount! Enter a positive integer.</red>"))
                    return
                }
                session.amount = amount
                session.state = InputState.AWAITING_PRICE
                player.sendMessage(miniMessage.deserialize("<yellow>Enter price per item (e.g., 10.5):</yellow>"))
            }
            InputState.AWAITING_PRICE -> {
                val price = msg.toDoubleOrNull()
                if (price == null || price <= 0) {
                    player.sendMessage(miniMessage.deserialize("<red>❌ Invalid price! Enter a positive number.</red>"))
                    return
                }
                session.price = price
                val totalCost = session.amount * session.price
                if (!plugin.economyManager.hasEnough(player, totalCost)) {
                    player.sendMessage(miniMessage.deserialize("<red>❌ Not enough money!</red>"))
                    removeSession(player)
                    return
                }
                removeSession(player)
                plugin.foliaLib.scheduler.runNextTick {
                    guiHandler.openConfirmOrderGUI(player, session.itemStack, session.amount, session.price)
                }
            }
        }
    }

    enum class InputState { AWAITING_AMOUNT, AWAITING_PRICE }
    data class PlayerInputSession(
        val player: Player, val itemStack: ItemStack,
        var amount: Int = 0, var price: Double = 0.0, var state: InputState
    )
}