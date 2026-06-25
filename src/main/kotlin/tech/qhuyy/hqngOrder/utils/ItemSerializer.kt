package tech.qhuyy.hqngOrder.utils

import org.bukkit.inventory.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.logging.Logger

object ItemSerializer {
    private const val STACK = 64;

    fun serializeArray(items: Array<ItemStack?>, logger: Logger): String {
        return try {
            val bytes = ByteArrayOutputStream().use { baos ->
                DataOutputStream(baos).use { dos ->
                    dos.writeInt(items.size)
                    for(item in items) {
                        if(item == null || item.type.isAir) {
                            dos.writeInt(0)
                        } else {
                            val serialized = item.serializeAsBytes()
                            dos.writeInt(serialized.size)
                            dos.write(serialized)
                        }
                    }
                }
                baos.toByteArray()
            }
            Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            logger.severe("Failed to serialize ItemStack array: ${e.message}")
            ""
        }
    }

    fun deserializeArray(base64: String, logger: Logger): Array<ItemStack?> {
        if (base64.isBlank()) return arrayOfNulls(STACK)

        return try {
            val bytes = Base64.getDecoder().decode(base64)
            ByteArrayInputStream(bytes).use { bais ->
                DataInputStream(bais).use { dis ->
                    val length = dis.readInt()
                    Array(length) {
                        val byteLength = dis.readInt()
                        if (byteLength == 0) {
                            null
                        } else {
                            val itemBytes = ByteArray(byteLength)
                            dis.readFully(itemBytes)
                            ItemStack.deserializeBytes(itemBytes)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.severe("Failed to deserialize ItemStack array: ${e.message}")
            arrayOfNulls(STACK)
        }
    }

    fun serialize(item: ItemStack): String {
        val bytes = item.serializeAsBytes()
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun deserialize(base64: String): ItemStack? {
        return try {
            val bytes = Base64.getDecoder().decode(base64)
            ItemStack.deserializeBytes(bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun itemsMatch(a: ItemStack, b: ItemStack): Boolean {
        if (a.type != b.type) return false
        val aBytes = a.serializeAsBytes()
        val bBytes = b.serializeAsBytes()
        return aBytes.contentEquals(bBytes)
    }
}