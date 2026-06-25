/*
 * Copyright (c) 2026 hqng05 <hqng05@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package tech.qhuyy.hqngOrder

import com.tcoded.folialib.FoliaLib
import org.bukkit.plugin.java.JavaPlugin
import tech.qhuyy.hqngOrder.message.MessageManager
import tech.qhuyy.hqngOrder.model.Software
import tech.qhuyy.hqngOrder.utils.MiniMessageFormatter
import tech.qhuyy.hqngOrder.utils.PluginBuildInfo

class HqngOrder : JavaPlugin() {
    lateinit var foliaLib: FoliaLib
        private set
    lateinit var software: Software
        private set
    lateinit var pluginBuildInfo: PluginBuildInfo
        private set
    lateinit var messageManager: MessageManager
        private set
    lateinit var miniMessageFormatter: MiniMessageFormatter
        private set

    override fun onEnable() {
        // Platform
        foliaLib = FoliaLib(this)
        platformInitializing()
        checkingIfSpigot()

        pluginBuildInfo = PluginBuildInfo(this)
        logSchedulingStatus()

        messageManager = MessageManager(this).also { it.init() }
        miniMessageFormatter = MiniMessageFormatter(messageManager)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    private fun platformInitializing() {
        software = Software.detectServerSoftware(foliaLib)
    }

    private fun checkingIfSpigot() {
        if (software == Software.SPIGOT || software == Software.UNKNOWN) {
            logger.severe("═══════════════════════════════════════════════════════════════")
            logger.severe("HqngOrder requires Paper or Folia to run (including forks).")
            logger.severe("Spigot, non-bukkit and other server software are not supported.")
            logger.severe("Please upgrade to Paper: https://papermc.io/downloads/paper")
            logger.severe("═══════════════════════════════════════════════════════════════")
            server.pluginManager.disablePlugin(this)
        }
    }

    private fun logSchedulingStatus() {
        if (software == Software.FOLIA) {
            logger.info("Running on Folia - region-safe scheduling enabled")
        } else {
            logger.info("Running on Paper - standard scheduling enabled")
        }
    }
}
