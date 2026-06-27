package tech.qhuyy.hqngOrder.gui.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import tech.qhuyy.hqngOrder.HqngOrder
import java.io.File
import java.util.logging.Level

class GuiConfigManager(private val plugin: HqngOrder) {

    private val guiDir: File
        get() = File(plugin.dataFolder, "gui")

    private val cache = mutableMapOf<String, GuiScreenConfig>()

    fun init() {
        if (!guiDir.exists()) {
            guiDir.mkdirs()
            plugin.logger.info("Created gui directory: ${guiDir.absolutePath}")
        }

        val screenNames = listOf(
            "market", "your_orders", "item_selector", "item_edit",
            "enchant_selector", "confirm_order", "deliver_items",
            "confirm_delivery", "admin_panel"
        )

        for (screen in screenNames) {
            val file = File(guiDir, "${screen}.yml")
            if (!file.exists()) {
                plugin.saveResource("gui/${screen}.yml", false)
            }
        }

        reload()
    }

    fun reload() {
        cache.clear()

        guiDir.listFiles { f -> f.extension == "yml" }?.forEach { file ->
            val screenName = file.nameWithoutExtension
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val screenConfig = parseScreenConfig(screenName, config)
                cache[screenName] = screenConfig
                plugin.logger.fine("Loaded GUI config: ${file.name}")
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Failed to load GUI config: ${file.name}", e)
            }
        }

        plugin.logger.info("Loaded ${cache.size} GUI screen configurations")
    }

    fun getScreen(screenName: String): GuiScreenConfig {
        return cache[screenName] ?: run {
            plugin.logger.warning("GUI config for '$screenName' not found, using defaults")
            defaultConfig(screenName)
        }
    }

    fun getItem(screenName: String, itemKey: String): GuiItemConfig? {
        return getScreen(screenName).items[itemKey]
    }

    private fun parseScreenConfig(screenName: String, config: YamlConfiguration): GuiScreenConfig {
        val title = config.getString("title") ?: run {
            plugin.logger.warning("GUI '$screenName' is missing 'title', using default")
            "<gray>${screenName.replace("_", " ").replaceFirstChar { it.uppercase() }}</gray>"
        }

        val rows = config.getInt("rows", 6)

        val items = mutableMapOf<String, GuiItemConfig>()
        val itemsSection = config.getConfigurationSection("items")

        if (itemsSection != null) {
            for (key in itemsSection.getKeys(false)) {
                val itemConfig = parseItemConfig(screenName, key, itemsSection.getConfigurationSection(key))
                if (itemConfig != null) {
                    items[key] = itemConfig
                }
            }
        }

        return GuiScreenConfig(screenName, title, rows, items)
    }

    private fun parseItemConfig(
        screenName: String,
        itemKey: String,
        section: ConfigurationSection?
    ): GuiItemConfig? {
        if (section == null) {
            plugin.logger.warning("GUI '$screenName' item '$itemKey' section is null, skipping")
            return null
        }

        val slot = if (section.contains("slot")) {
            section.getInt("slot", -1)
        } else {
            plugin.logger.warning("GUI '$screenName' item '$itemKey' is missing 'slot', defaulting to -1")
            -1
        }

        val material = section.getString("material") ?: run {
            plugin.logger.warning("GUI '$screenName' item '$itemKey' is missing 'material', defaulting to STONE")
            "STONE"
        }

        val name = section.getString("name")
        val lore = section.getStringList("lore").ifEmpty { null }

        val amount = section.getInt("amount", 1)
        val glowing = section.getBoolean("glowing", false)

        return GuiItemConfig(
            key = itemKey,
            slot = slot,
            material = material,
            name = name,
            lore = lore,
            amount = amount,
            glowing = glowing
        )
    }

    private fun defaultConfig(screenName: String): GuiScreenConfig {
        return GuiScreenConfig(
            screenName = screenName,
            title = "<gray>${screenName.replace("_", " ").replaceFirstChar { it.uppercase() }}</gray>",
            rows = 6,
            items = emptyMap()
        )
    }

    data class GuiScreenConfig(
        val screenName: String,
        val title: String,
        val rows: Int,
        val items: Map<String, GuiItemConfig>
    )

    data class GuiItemConfig(
        val key: String,
        val slot: Int,
        val material: String,
        val name: String?,
        val lore: List<String>?,
        val amount: Int,
        val glowing: Boolean
    )
}

