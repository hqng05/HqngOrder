package tech.qhuyy.hqngOrder.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import tech.qhuyy.hqngOrder.HqngOrder

class ItemSelectorGUI(private val plugin: HqngOrder) {

    private val miniMessage = MiniMessage.miniMessage()
    private var currentCategory = "all"

    private val allMaterials: List<Material> = Material.entries
        .filter { !it.isAir && it.isItem && !it.isLegacy }
        .sortedBy { it.name }

    fun open(player: Player) {
        currentCategory = "all"
        reopen(player)
    }

    private fun reopen(player: Player) {
        val screenConfig = plugin.guiConfigManager.getScreen("item_selector")
        val rows = screenConfig.rows.coerceIn(1, 6)

        val gui: PaginatedGui = Gui.paginated()
            .title(miniMessage.deserialize(screenConfig.title))
            .rows(rows)
            .pageSize(36)
            .create()

        gui.setDefaultClickAction { event -> event.isCancelled = true }

        val borderItem = ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE)
            .name(Component.text(" "))
            .asGuiItem { event -> event.isCancelled = true }
        gui.filler.fillBorder(borderItem)

        val handItem = player.inventory.itemInMainHand
        if (!handItem.type.isAir) {
            gui.setItem(4, ItemBuilder.from(handItem.clone())
                .name(miniMessage.deserialize("<yellow>✋ ${handItem.type.name}</yellow>"))
                .lore(miniMessage.deserialize("<gray>Click to use</gray>"))
                .asGuiItem { event ->
                    event.isCancelled = true
                    plugin.guiHandler.openItemEditGUI(player, handItem.clone())
                })
        }

        makeClick("item_selector", "back") { plugin.guiHandler.openYourOrdersGUI(player) }?.let { gui.setItem(45, it) }

        val slots = mapOf(46 to "filter_all", 47 to "filter_blocks", 48 to "filter_tools",
            49 to "filter_combat", 50 to "filter_misc")
        val cats = mapOf(46 to "all", 47 to "blocks", 48 to "tools", 49 to "combat", 50 to "misc")
        for ((slot, key) in slots) {
            makeClick("item_selector", key) {
                currentCategory = cats[slot] ?: "all"
                reopen(player)
            }?.let { gui.setItem(slot, it) }
        }

        makeClick("item_selector", "previous_page") { gui.previous() }?.let { gui.setItem(51, it) }
        makeClick("item_selector", "next_page") { gui.next() }?.let { gui.setItem(52, it) }

        val materials = when (currentCategory) {
            "blocks" -> allMaterials.filter { it.isBlock }
            "tools" -> allMaterials.filter { m ->
                m.name.endsWith("_AXE") || m.name.endsWith("_PICKAXE") ||
                    m.name.endsWith("_SHOVEL") || m.name.endsWith("_HOE") ||
                    m.name.contains("FISHING") || m.name.contains("SHEARS") ||
                    m.name.contains("FLINT") || m.name.endsWith("_SWORD")
            }
            "combat" -> allMaterials.filter { m ->
                m.name.contains("SWORD") || m.name.contains("BOW") ||
                    m.name.contains("CROSSBOW") || m.name.contains("TRIDENT") ||
                    m.name.endsWith("_HELMET") || m.name.endsWith("_CHESTPLATE") ||
                    m.name.endsWith("_LEGGINGS") || m.name.endsWith("_BOOTS") ||
                    m.name.contains("SHIELD")
            }
            else -> allMaterials
        }

        for (mat in materials) {
            val item = ItemBuilder.from(mat)
                .name(miniMessage.deserialize("<yellow>${mat.name}</yellow>"))
                .asGuiItem { event ->
                    event.isCancelled = true
                    plugin.guiHandler.openItemEditGUI(player, ItemStack(mat))
                }
            gui.addItem(item)
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

