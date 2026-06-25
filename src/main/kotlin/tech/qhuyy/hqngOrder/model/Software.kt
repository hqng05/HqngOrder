package tech.qhuyy.hqngOrder.model

import com.tcoded.folialib.FoliaLib

enum class Software {
    PAPER, FOLIA, SPIGOT, UNKNOWN;

    companion object {
        fun detectServerSoftware(foliaLib: FoliaLib): Software = when {
            foliaLib.isFolia -> FOLIA
            foliaLib.isPaper -> PAPER
            foliaLib.isSpigot -> SPIGOT
            else -> UNKNOWN
        }
    }
}
