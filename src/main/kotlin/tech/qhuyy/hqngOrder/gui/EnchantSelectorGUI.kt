package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.Registry
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import tech.qhuyy.hqngOrder.HqngOrder

class EnchantSelectorGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    private val allEnchants: List<Enchantment> = Registry.ENCHANTMENT.toList().sortedBy { it.key.key }

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
        gui.setItem(
            4, ItemBuilder.from(preview)
                .name(
                    plugin.miniMessageFormatter.deserializeKey(
                        "gui.enchant-selector.preview-name",
                        TagResolver.resolver(
                            "item_name",
                            Tag.inserting(net.kyori.adventure.text.Component.text(itemStack.type.name))
                        )
                    )
                )
                .asGuiItem { event -> event.isCancelled = true })

        makeClick("enchant_selector", "save") {
            plugin.guiHandler.openItemEditGUI(player, itemStack)
        }?.let { gui.setItem(45, it) }

        val formatter = plugin.miniMessageFormatter
        val applicable = getApplicableEnchants(itemStack)
        val currentEnchants = itemStack.enchantments
        for (enchant in applicable) {
            val currentLevel = currentEnchants[enchant] ?: 0
            val maxLevel = enchant.maxLevel
            val name = enchant.key.key.replace("_", " ").replaceFirstChar { it.uppercase() }

            val lore = mutableListOf<net.kyori.adventure.text.Component>()
            lore.add(
                formatter.deserializeKey(
                    "gui.enchant-selector.current-line",
                    TagResolver.resolver(
                        "current",
                        Tag.inserting(net.kyori.adventure.text.Component.text(currentLevel))
                    ),
                    TagResolver.resolver("max", Tag.inserting(net.kyori.adventure.text.Component.text(maxLevel)))
                )
            )
            lore.add(
                formatter.deserializeKey(
                    "gui.enchant-selector.max-line",
                    TagResolver.resolver("max", Tag.inserting(net.kyori.adventure.text.Component.text(maxLevel)))
                )
            )
            lore.add(net.kyori.adventure.text.Component.empty())
            lore.add(formatter.deserializeKey("gui.enchant-selector.click-hint"))

            val display = if (currentLevel > 0) {
                val book = ItemStack(Material.ENCHANTED_BOOK)
                (book.itemMeta as? EnchantmentStorageMeta)?.let {
                    it.addStoredEnchant(
                        enchant,
                        currentLevel,
                        true
                    ); book.itemMeta = it
                }
                book
            } else ItemStack(Material.BOOK)

            gui.addItem(
                ItemBuilder.from(display)
                    .name(
                        formatter.deserializeKey(
                            "gui.enchant-selector.enchant-name",
                            TagResolver.resolver(
                                "enchant_name",
                                Tag.inserting(net.kyori.adventure.text.Component.text(name))
                            )
                        )
                    )
                    .lore(lore.toList())
                    .asGuiItem { event ->
                        event.isCancelled = true
                        when (event.click) {
                            org.bukkit.event.inventory.ClickType.LEFT -> {
                                val nl = (itemStack.enchantments[enchant] ?: 0) + 1
                                if (nl <= maxLevel) {
                                    itemStack.addUnsafeEnchantment(enchant, nl); open(player, itemStack)
                                }
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
        val m = try {
            Material.valueOf(c.material.uppercase())
        } catch (e: Exception) {
            Material.STONE
        }
        val b = ItemBuilder.from(m).amount(c.amount.coerceIn(1, 64))
        if (c.name != null) b.name(miniMessage.deserialize(c.name))
        if (c.lore != null) b.lore(c.lore.map { miniMessage.deserialize(it) })
        return b.asGuiItem { event -> event.isCancelled = true }
    }

    private fun makeClick(screen: String, key: String, action: () -> Unit): GuiItem? {
        val c = plugin.guiConfigManager.getItem(screen, key) ?: return null
        val m = try {
            Material.valueOf(c.material.uppercase())
        } catch (e: Exception) {
            Material.STONE
        }
        val b = ItemBuilder.from(m).amount(c.amount.coerceIn(1, 64))
        if (c.name != null) b.name(miniMessage.deserialize(c.name))
        if (c.lore != null) b.lore(c.lore.map { miniMessage.deserialize(it) })
        return b.asGuiItem { event -> event.isCancelled = true; action() }
    }
}

