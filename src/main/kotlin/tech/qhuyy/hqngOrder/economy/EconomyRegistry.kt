package tech.qhuyy.hqngOrder.economy

import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.economy.providers.PlayerPointsProvider
import tech.qhuyy.hqngOrder.economy.providers.VaultProvider
import tech.qhuyy.hqngOrder.economy.providers.VaultUnlockedProvider
import tech.qhuyy.hqngOrder.model.Software
import java.util.logging.Logger

class EconomyRegistry(
    private val plugin: HqngOrder,
    private val platform: Software
) {
    private val logger: Logger = plugin.logger
    private val preferred: EconomyType = plugin.configManager.getPreferedEconomyProvider()

    private val priorityList: List<EconomyType>
        get() = when(platform) {
            Software.FOLIA -> listOf(
                EconomyType.VAULT_UNLOCKED,
                EconomyType.PLAYER_POINTS
            )
            else -> listOf(
                EconomyType.VAULT,
                EconomyType.VAULT_UNLOCKED,
                EconomyType.PLAYER_POINTS
            )
        }

    fun resolve() : EconomyProvider? {
        if (isCompatible(preferred)) {
            createProvider(preferred)?.let {
                logger.info("Using economy provider: $preferred")
                return it
            }
            logger.warning("Preferred economy $preferred not available, trying fallback...")
        } else {
            logger.warning("Preferred economy $preferred is not compatible with $platform, trying next provider...")
        }

        for (type in priorityList) {
            createProvider(type)?.let {
                logger.info("Using economy provider: $type")
                return it
            }
        }

        return null
    }

    private fun isCompatible(type: EconomyType): Boolean {
        return !(platform == Software.FOLIA && type == EconomyType.VAULT)
    }

    private fun createProvider(type: EconomyType): EconomyProvider? {
        return when (type) {
            EconomyType.VAULT -> VaultProvider.create()
            EconomyType.VAULT_UNLOCKED -> VaultUnlockedProvider.create()
            EconomyType.PLAYER_POINTS -> PlayerPointsProvider.create()
        }
    }
}