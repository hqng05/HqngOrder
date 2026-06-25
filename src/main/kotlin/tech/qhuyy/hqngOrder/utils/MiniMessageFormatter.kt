package tech.qhuyy.hqngOrder.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import tech.qhuyy.hqngOrder.message.MessageManager

class MiniMessageFormatter(
    private val messageManager: MessageManager
) {
    private val miniMessage = MiniMessage.miniMessage()

    private val prefix: String
        get() = messageManager.getString(
            "prefix",
            "<gradient:#FF6B35:#FFA500>HqngOrder</gradient>"
        )

    fun sendMessage(
        target: CommandSender,
        key: String,
        vararg resolvers: TagResolver
    ) {
        val template = messageManager.getMessage(key)
        val component = if (resolvers.isEmpty()) {
            miniMessage.deserialize(template)
        } else {
            miniMessage.deserialize(template, TagResolver.resolver(*resolvers))
        }
        target.sendMessage(component)
    }

    fun sendMessageWithPrefix(
        target: CommandSender,
        key: String,
        vararg resolvers: TagResolver
    ) {
        val template = messageManager.getMessage(key)
        val resolver = if (resolvers.isNotEmpty()) {
            TagResolver.resolver(*resolvers)
        } else {
            TagResolver.empty()
        }
        val component = miniMessage.deserialize(prefix + template, resolver)
        target.sendMessage(component)
    }

    fun sendMessageList(
        target: CommandSender,
        key: String,
        vararg resolvers: TagResolver
    ) {
        val templates = messageManager.getMessageList(key)
        val resolver = if (resolvers.isNotEmpty()) {
            TagResolver.resolver(*resolvers)
        } else {
            TagResolver.empty()
        }
        templates.forEach { template ->
            val component = miniMessage.deserialize(template, resolver)
            target.sendMessage(component)
        }
    }

    fun sendMessageListWithPrefix(
        target: CommandSender,
        key: String,
        vararg resolvers: TagResolver
    ) {
        val templates = messageManager.getMessageList(key)
        val resolver = if (resolvers.isNotEmpty()) {
            TagResolver.resolver(*resolvers)
        } else {
            TagResolver.empty()
        }
        templates.forEach { template ->
            val component = miniMessage.deserialize(prefix + template, resolver)
            target.sendMessage(component)
        }
    }

    fun deserialize(template: String, vararg resolvers: TagResolver): Component {
        return if (resolvers.isEmpty()) {
            miniMessage.deserialize(template)
        } else {
            miniMessage.deserialize(template, TagResolver.resolver(*resolvers))
        }
    }

    fun deserializeKey(key: String, vararg resolvers: TagResolver): Component {
        val template = messageManager.getMessage(key)
        return if (resolvers.isEmpty()) {
            miniMessage.deserialize(template)
        } else {
            miniMessage.deserialize(template, TagResolver.resolver(*resolvers))
        }
    }

    fun deserializeKeyWithPrefix(key: String, vararg resolvers: TagResolver): Component {
        val template = messageManager.getMessage(key)
        val resolver = if (resolvers.isNotEmpty()) {
            TagResolver.resolver(*resolvers)
        } else {
            TagResolver.empty()
        }
        return miniMessage.deserialize(prefix + template, resolver)
    }

    fun resolvers(vararg pairs: Pair<String, Any>): TagResolver {
        return TagResolver.resolver(
            pairs.map { (key, value) ->
                TagResolver.resolver(key, Tag.inserting(Component.text(value.toString())))
            }
        )
    }

    fun placeholder(key: String, value: String): TagResolver {
        return TagResolver.resolver(key, Tag.inserting(Component.text(value)))
    }

    fun placeholder(key: String, value: Number): TagResolver {
        return TagResolver.resolver(key, Tag.inserting(Component.text(value.toString())))
    }

    fun text(text: String): Component {
        return Component.text(text)
    }
}