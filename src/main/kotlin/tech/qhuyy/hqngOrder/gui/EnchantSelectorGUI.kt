package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import tech.qhuyy.hqngOrder.HqngOrder

class EnchantSelectorGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    private val allEnchants: List<Enchantment> = listOf(*Enchantment.values()).sortedBy { it.key.key }

    private fun getApplicableEnchants(item: ItemStack): List<Enchantment> {
        return allEnchants.filter { it.canEnchantItem(item) || item.type.name.contains("BOOK") }
    }

    fun open(player: Player, itemStack: ItemStack) {
        val cfg = plugin.guiConfigManager.getScreen("enchant_selector")
        val gui: PaginatedGui = Gui.paginated()
            .title(miniMessage.deserialize(cfg.title))
            .rows(6).pageSize(36).create()

        gui.setDefaultClickAction { event -> event.isCancelled = true }

        makeItem("enchant_selector", "border")?.let { gui.filler.fillBorder(it) }

        val preview = itemStack.clone().apply { amount = 1 }
        gui.setItem(4, ItemBuilder.from(preview)
            .name(miniMessage.deserialize("<gold>👁️ ${itemStack.type.name}</gold>"))
            .asGuiItem { event -> event.isCancelled = true })

        makeClick("enchant_selector", "save") {
            plugin.guiHandler.openItemEditGUI(player, itemStack)
        }?.let { gui.setItem(45, it) }

        val applicable = getApplicableEnchants(itemStack)
        val currentEnchants = itemStack.enchantments
        for (enchant in applicable) {
            val currentLevel = currentEnchants[enchant] ?: 0
            val maxLevel = enchant.maxLevel
            val name = enchant.key.key.replace("_", " ").replaceFirstChar { it.uppercase() }

            val lore = mutableListOf<Component>()
            lore.add(miniMessage.deserialize("<gray>Current: <yellow>$currentLevel</yellow> / <yellow>$maxLevel</yellow></gray>"))
            lore.add(miniMessage.deserialize("<gray>Max: <yellow>$maxLevel</yellow></gray>"))
            lore.add(Component.empty())
            lore.add(miniMessage.deserialize("<gray><italic>Left ↑ | Right ↓</italic></gray>"))

            val display = if (currentLevel > 0) {
                val book = ItemStack(Material.ENCHANTED_BOOK)
                (book.itemMeta as? EnchantmentStorageMeta)?.let { it.addStoredEnchant(enchant, currentLevel, true); book.itemMeta = it }
                book
            } else ItemStack(Material.BOOK)

            gui.addItem(ItemBuilder.from(display)
                .name(miniMessage.deserialize("<light_purple>$name</light_purple>"))
                .lore(lore.toList())
                .asGuiItem { event ->
                    event.isCancelled = true
                    when (event.click) {
                        org.bukkit.event.inventory.ClickType.LEFT -> {
                            val nl = (itemStack.enchantments[enchant] ?: 0) + 1
                            if (nl <= maxLevel) { itemStack.addUnsafeEnchantment(enchant, nl); open(player, itemStack) }
                        }
                        org.bukkit.event.inventory.ClickType.RIGHT -> {
                            val c = itemStack.enchantments[enchant] ?: 0
                            if (c > 0) {
                                if (c <= 1) itemStack.removeEnchantment(enchant)
                                else itemStack.addUnsafeEnchantment(enchant, c - 1)
                                open(player, itemStack)
                            }
                        }
                        else -> {}
                    }
                })
        }

        gui.open(player)
    }

    private fun makeItem(screen: String, key: String): GuiItem? {
        val c = plugin.guiConfigManager.getItem(screen, key) ?: return null
        val m = try { Material.valueOf(c.material.uppercase()) } catch (e: Exception) { Material.STONE }
        val b = ItemBuilder.from(m).amount(c.amount.coerceIn(1, 64))
        if (c.name != null) b.name(miniMessage.deserialize(c.name))
        if (c.lore != null) b.lore(c.lore.map { miniMessage.deserialize(it) })
        return b.asGuiItem { event -> event.isCancelled = true }
    }

    private fun makeClick(screen: String, key: String, action: () -> Unit): GuiItem? {
        val c = plugin.guiConfigManager.getItem(screen, key) ?: return null
        val m = try { Material.valueOf(c.material.uppercase()) } catch (e: Exception) { Material.STONE }
        val b = ItemBuilder.from(m).amount(c.amount.coerceIn(1, 64))
        if (c.name != null) b.name(miniMessage.deserialize(c.name))
        if (c.lore != null) b.lore(c.lore.map { miniMessage.deserialize(it) })
        return b.asGuiItem { event -> event.isCancelled = true; action() }
    }
}

