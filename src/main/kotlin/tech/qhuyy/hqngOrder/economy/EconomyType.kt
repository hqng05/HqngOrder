package tech.qhuyy.hqngOrder.economy

enum class EconomyType {
    VAULT,
    VAULT_UNLOCKED,
    PLAYER_POINTS;

    companion object {
        fun fromString(value: String?): EconomyType {
            val normalized = value?.trim()?.uppercase()?.replace(" ", "_") ?: ""
            return when (normalized) {
                "VAULTUNLOCKED" -> VAULT_UNLOCKED
                else -> try {
                    valueOf(normalized)
                } catch (_: IllegalArgumentException) {
                    VAULT
                }
            }
        }
    }
}