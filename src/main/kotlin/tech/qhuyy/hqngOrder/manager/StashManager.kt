package tech.qhuyy.hqngOrder.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.database.DatabaseManager
import java.util.concurrent.ConcurrentHashMap

class StashManager(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    private val openStashes = ConcurrentHashMap.newKeySet<Inventory>()

    private val databaseManager: DatabaseManager get() = plugin.databaseManager

    fun openStash(player: Player) {
        plugin.scope.launchAsync {
            val stash = databaseManager.loadStash(player.uniqueId)

            plugin.foliaLib.scheduler.runNextTick {
                val title = plugin.messageManager.getMessage("stash-title")

                val inventory = Bukkit.createInventory(null, 54, miniMessage.deserialize(title))

                for (i in stash.indices) {
                    if (stash[i] != null) {
                        inventory.setItem(i, stash[i])
                    }
                }

                openStashes.add(inventory)
                player.openInventory(inventory)
            }
        }
    }

    fun isOpenStash(inventory: Inventory): Boolean {
        return openStashes.contains(inventory)
    }

    fun handleStashClose(player: Player, inventory: Inventory) {
        openStashes.remove(inventory)

        val contents = inventory.contents

        plugin.scope.launchAsync {
            databaseManager.saveStash(player.uniqueId, contents)
            plugin.logger.fine("Saved stash for ${player.name} (${contents.count { it != null && !it.type.isAir }} items)")
        }
    }
}