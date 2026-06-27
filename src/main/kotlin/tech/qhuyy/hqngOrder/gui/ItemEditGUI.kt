package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tech.qhuyy.hqngOrder.HqngOrder

class ItemEditGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()

    fun open(player: Player, itemStack: ItemStack) {
        val screenConfig = plugin.guiConfigManager.getScreen("item_edit")
        val title = miniMessage.deserialize(screenConfig.title)
        val rows = screenConfig.rows.coerceIn(1, 6)

        val gui = Gui.gui().title(title).rows(rows).create()
        gui.setDefaultClickAction { event -> event.isCancelled = true }

        val borderItem = ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE)
            .name(Component.text(" "))
            .asGuiItem { event -> event.isCancelled = true }
        gui.filler.fillBorder(borderItem)

        makeClick("item_edit", "back") { plugin.guiHandler.openItemSelectorGUI(player) }?.let { gui.setItem(10, it) }

        makeClick("item_edit", "select_enchant") {
            plugin.guiHandler.openEnchantSelectorGUI(
                player,
                itemStack
            )
        }?.let { gui.setItem(12, it) }

        val formatter = plugin.miniMessageFormatter
        val clone = itemStack.clone().apply { amount = 1 }
        val loreLines: MutableList<net.kyori.adventure.text.Component> = mutableListOf()
        loreLines.add(
            formatter.deserializeKey(
                "gui.item-edit.type-line",
                TagResolver.resolver(
                    "item_name",
                    Tag.inserting(net.kyori.adventure.text.Component.text(itemStack.type.name))
                )
            )
        )
        if (itemStack.enchantments.isNotEmpty()) {
            loreLines.add(net.kyori.adventure.text.Component.empty())
            loreLines.add(formatter.deserializeKey("gui.item-edit.enchantments-header"))
            for ((ench, lvl) in itemStack.enchantments) {
                val en = ench.key.key.replace("_", " ").replaceFirstChar { it.uppercase() }
                loreLines.add(
                    formatter.deserializeKey(
                        "gui.item-edit.enchant-line",
                        TagResolver.resolver("enchant", Tag.inserting(net.kyori.adventure.text.Component.text(en))),
                        TagResolver.resolver(
                            "level",
                            Tag.inserting(net.kyori.adventure.text.Component.text(toRoman(lvl)))
                        )
                    )
                )
            }
        }
        val preview = ItemBuilder.from(clone)
            .name(
                formatter.deserializeKey(
                    "gui.item-edit.preview-name",
                    TagResolver.resolver(
                        "item_name",
                        Tag.inserting(net.kyori.adventure.text.Component.text(itemStack.type.name))
                    )
                )
            )
            .lore(loreLines.toList())
            .asGuiItem { event -> event.isCancelled = true }
        gui.setItem(13, preview)

        makeClick("item_edit", "clear_enchant") {
            val meta = itemStack.itemMeta ?: return@makeClick
            for (e in meta.enchants.keys.toList()) meta.removeEnchant(e)
            itemStack.itemMeta = meta
            plugin.guiHandler.openItemEditGUI(player, itemStack)
        }?.let { gui.setItem(14, it) }

        makeClick("item_edit", "confirm_item") {
            player.closeInventory()
            plugin.inputListener.startSession(player, itemStack.clone())
        }?.let { gui.setItem(16, it) }

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

    private fun toRoman(level: Int): String = when (level) {
        1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"
        6 -> "VI"; 7 -> "VII"; 8 -> "VIII"; 9 -> "IX"; 10 -> "X"
        else -> level.toString()
    }
}

