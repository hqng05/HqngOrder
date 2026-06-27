package tech.qhuyy.hqngOrder.economy

import kotlin.enums.enumEntries

enum class EconomyType {
    VAULT,
    VAULT_UNLOCKED,
    PLAYER_POINTS;

    companion object {
        fun fromString(value: String?): EconomyType {
            if (value.isNullOrBlank()) return VAULT

            val normalized = value
                .trim()
                .uppercase()
                .replace(Regex("[ _.-]"), "_")

            if (normalized == "VAULTUNLOCKED") return VAULT_UNLOCKED

            return enumEntries<EconomyType>().find { it.name == normalized }
                ?: VAULT
        }
    }
}