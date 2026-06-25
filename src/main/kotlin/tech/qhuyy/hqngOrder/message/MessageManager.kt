package tech.qhuyy.hqngOrder.message

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import tech.qhuyy.hqngOrder.HqngOrder
import java.io.File

class MessageManager(
    private val plugin: HqngOrder
) {
    lateinit var messagesConfig: FileConfiguration private set
    private var messagesFile: File? = null
    private val logger = plugin.logger

    fun init() {
        loadMessagesConfig()
    }

    fun getMessage(key: String): String {
        return messagesConfig.getString(key) ?: run {
            plugin.logger.warning("Missing message key: $key")
            "<red>Missing message: $key</red>"
        }
    }

    fun getMessageOrNull(key: String): String? {
        return messagesConfig.getString(key)
    }

    fun getMessageList(key: String): List<String> {
        val list = messagesConfig.getStringList(key)
        if (list.isEmpty()) {
            plugin.logger.warning("Missing or empty message list: $key")
            return listOf("<red>Missing message list: $key</red>")
        }
        return list
    }

    fun getMessageListOrNull(key: String): List<String>? {
        val list = messagesConfig.getStringList(key)
        return if (list.isEmpty()) null else list
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return messagesConfig.getBoolean(key, default)
    }

    fun getInt(key: String, default: Int = 0): Int {
        return messagesConfig.getInt(key, default)
    }

    fun getString(key: String, default: String = ""): String {
        return messagesConfig.getString(key, default) ?: default
    }

    fun hasKey(key: String): Boolean {
        return messagesConfig.contains(key)
    }

    fun reloadMessagesConfig() {
        val file = messagesFile ?: return
        messagesConfig = YamlConfiguration.loadConfiguration(file)
        logger.fine("Reloaded messages.yml")
    }

    private fun loadMessagesConfig() {
        val file = if (messagesFile == null) {
            val f = File(plugin.dataFolder, "messages.yml")
            messagesFile = f
            f
        } else {
            messagesFile!!
        }

        if (!file.exists()) {
            plugin.saveResource("messages.yml", false)
            logger.info("Created default messages.yml")
        }

        messagesConfig = YamlConfiguration.loadConfiguration(file)
        logger.fine("Loaded messages.yml")
    }
}