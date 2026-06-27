package tech.qhuyy.hqngOrder.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.inventory.ItemStack
import tech.qhuyy.hqngOrder.HqngOrder
import tech.qhuyy.hqngOrder.config.ConfigManager
import tech.qhuyy.hqngOrder.model.BuyOrder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Instant
import java.util.*
import java.util.logging.Level

class DatabaseManager(
    private val plugin: HqngOrder,
    private val configManager: ConfigManager
) {

    private lateinit var pool: HikariDataSource
    private lateinit var dialect: SqlDialect

    val isInitialized: Boolean
        get() = ::pool.isInitialized && !pool.isClosed

    fun init() {
        val startTime = System.nanoTime()
        plugin.logger.info("Initializing database layer...")

        try {
            dialect = resolveDialect()
            plugin.logger.info("Storage dialect: ${dialect.displayName}")

            plugin.logger.info("Creating connection pool...")
            val hikariConfig = buildHikariConfig()
            pool = HikariDataSource(hikariConfig)

            pool.connection.use { conn ->
                conn.createStatement().use { it.executeQuery("SELECT 1") }
            }
            val elapsed = (System.nanoTime() - startTime) / 1_000_000.0
            plugin.logger.info("Connection pool established in ${"%.2f".format(elapsed)}ms")

            plugin.logger.info("Setting up database tables...")
            createTables()
            plugin.logger.info("Tables verified/created successfully")

            plugin.logger.info("Running migrations...")
            runMigrations()

            plugin.logger.info("Database layer initialized successfully")
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize database — SQL error", e)
            plugin.logger.severe("The plugin may not function correctly without a database connection")
            closePoolSafely()
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize database — unexpected error", e)
            closePoolSafely()
        }
    }

    fun close() {
        plugin.logger.info("Shutting down database layer...")
        closePoolSafely()
        plugin.logger.info("Database layer shut down complete")
    }

    private fun closePoolSafely() {
        try {
            if (::pool.isInitialized && !pool.isClosed) {
                pool.close()
                plugin.logger.info("Connection pool closed")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error while closing connection pool", e)
        }
    }

    private fun createTables() {
        try {
            pool.connection.use { conn ->

                if (dialect == SqlDialect.SQLITE) {
                    try {
                        conn.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
                        plugin.logger.info("SQLite WAL mode enabled")
                    } catch (e: SQLException) {
                        plugin.logger.warning("Could not enable WAL mode: ${e.message}")
                    }
                }

                conn.createStatement().use { stmt ->
                    stmt.execute(dialect.sqlCreateOrdersTable)
                    stmt.execute(dialect.sqlCreateStashTable)
                    stmt.execute(dialect.sqlCreateLogsTable)
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to create database tables", e)
            throw e
        }
    }

    private fun runMigrations() {
        try {
            pool.connection.use { conn ->
                val meta = conn.metaData

                val hasCurrencyColumn = meta.getColumns(null, null, "hqng_orders", "currency").use { it.next() }
                        || meta.getColumns(null, null, "hqng_ORDERS", "currency").use { it.next() }

                if (!hasCurrencyColumn) {
                    plugin.logger.info("Migration: adding 'currency' column to hqng_orders...")
                    try {
                        conn.createStatement().use {
                            it.execute("ALTER TABLE hqng_orders ADD COLUMN currency VARCHAR(20) NOT NULL DEFAULT 'MONEY'")
                        }
                        plugin.logger.info("Migration: 'currency' column added successfully")
                    } catch (e: SQLException) {

                        plugin.logger.info("Migration note: ${e.message}")
                    }
                } else {
                    plugin.logger.info("Migration: 'currency' column already exists, skipping")
                }
            }
        } catch (e: Exception) {

            plugin.logger.log(Level.WARNING, "Migration check failed (non-fatal)", e)
        }
    }

    fun createOrder(order: BuyOrder): Int {
        val sql = """
            INSERT INTO hqng_orders
                (buyer_uuid, buyer_name, item_serialized, amount_needed, amount_fulfilled,
                 price_per_item, currency, created_at, expiry_time, status, locked_by, lock_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            pool.connection.use { conn ->
                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.bindOrderParams(order)
                    val affectedRows = ps.executeUpdate()

                    if (affectedRows == 0) {
                        plugin.logger.warning("Creating order returned 0 affected rows")
                        return -1
                    }

                    ps.generatedKeys.use { rs ->
                        if (rs.next()) {
                            val id = rs.getInt(1)
                            plugin.logger.fine("Created order #$id for ${order.buyerName}")
                            id
                        } else {
                            plugin.logger.warning("Creating order succeeded but no generated key returned")
                            -1
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to create order for ${order.buyerName}", e)
            -1
        }
    }

    fun updateOrder(order: BuyOrder) {
        val sql = """
            UPDATE hqng_orders
            SET amount_fulfilled = ?, status = ?, locked_by = ?, lock_time = ?
            WHERE id = ?
        """.trimIndent()

        try {
            pool.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, order.amountFulfilled)
                    ps.setString(2, order.status)
                    ps.setString(3, order.lockedBy?.toString())
                    ps.setLong(4, order.lockTime)
                    ps.setInt(5, order.id)

                    val affected = ps.executeUpdate()
                    if (affected == 0) {
                        plugin.logger.warning("Update for order #${order.id} affected 0 rows (order may not exist)")
                    } else {
                        plugin.logger.fine("Updated order #${order.id}")
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to update order #${order.id}", e)
        }
    }

    fun loadActiveOrders(): List<BuyOrder> {
        return try {
            pool.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM hqng_orders WHERE status = ?").use { ps ->
                    ps.setString(1, "ACTIVE")
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                rs.toBuyOrder(plugin)?.let { add(it) }
                            }
                        }
                    }
                }
            }.also { orders ->
                plugin.logger.info("Loaded ${orders.size} active orders")
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to load active orders", e)
            emptyList()
        }
    }

    fun loadOrder(orderId: Int): BuyOrder? {
        return try {
            pool.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM hqng_orders WHERE id = ?").use { ps ->
                    ps.setInt(1, orderId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.toBuyOrder(plugin) else null
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to load order #$orderId", e)
            null
        }
    }

    fun loadStash(playerUuid: UUID): Array<ItemStack?> {
        return try {
            pool.connection.use { conn ->
                conn.prepareStatement("SELECT stash_serialized FROM hqng_order_stash WHERE player_uuid = ?").use { ps ->
                    ps.setString(1, playerUuid.toString())
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            deserializeItemStackArray(rs.getString("stash_serialized"), plugin)
                        } else {
                            plugin.logger.fine("No stash found for $playerUuid, returning empty")
                            arrayOfNulls(STASH_SIZE)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to load stash for $playerUuid", e)
            arrayOfNulls(STASH_SIZE)
        }
    }

    fun saveStash(playerUuid: UUID, stash: Array<ItemStack?>) {
        try {
            val serialized = serializeItemStackArray(stash, plugin)
            val sql = dialect.sqlUpsertStash

            pool.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerUuid.toString())
                    ps.setString(2, serialized)
                    if (dialect == SqlDialect.MYSQL) {
                        ps.setString(3, serialized)
                    }
                    ps.executeUpdate()
                    plugin.logger.fine("Saved stash for $playerUuid (${stash.count { it != null }} items)")
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to save stash for $playerUuid", e)
        }
    }

    fun logTransaction(orderId: Int, playerUuid: UUID, action: String, amount: Int, details: String?) {
        val sql = """
            INSERT INTO hqng_order_logs (order_id, player_uuid, action, amount, details, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        try {
            pool.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, orderId)
                    ps.setString(2, playerUuid.toString())
                    ps.setString(3, action)
                    ps.setInt(4, amount)
                    ps.setString(5, details)
                    ps.setLong(6, System.currentTimeMillis())
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to log transaction for order #$orderId", e)
        }
    }

    fun loadLogs(limit: Int): List<LogEntry> {
        return try {
            pool.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM hqng_order_logs ORDER BY timestamp DESC LIMIT ?").use { ps ->
                    ps.setInt(1, limit)
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(
                                    LogEntry(
                                        orderId = rs.getInt("order_id"),
                                        playerUuid = UUID.fromString(rs.getString("player_uuid")),
                                        action = rs.getString("action"),
                                        amount = rs.getInt("amount"),
                                        details = rs.getString("details"),
                                        timestamp = rs.getLong("timestamp")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to load transaction logs", e)
            emptyList()
        }
    }

    private fun resolveDialect(): SqlDialect {
        return when (configManager.getDatabaseType()) {
            ConfigManager.DatabaseType.MYSQL -> SqlDialect.MYSQL
            ConfigManager.DatabaseType.SQLITE -> SqlDialect.SQLITE
        }
    }

    private fun buildHikariConfig(): HikariConfig {
        val config = HikariConfig()

        when (dialect) {
            SqlDialect.MYSQL -> configureMysql(config)
            SqlDialect.SQLITE -> configureSqlite(config)
        }

        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        config.addDataSourceProperty("useServerPrepStmts", "true")

        return config
    }

    private fun configureMysql(config: HikariConfig) {
        val mysql = configManager.getMySQLConfig()

        config.jdbcUrl = "jdbc:mysql://${mysql.host}:${mysql.port}/${mysql.database}" +
                "?useSSL=${mysql.useSSL}&allowPublicKeyRetrieval=true"
        config.username = mysql.username
        config.password = mysql.password
        config.driverClassName = "com.mysql.cj.jdbc.Driver"
        config.maximumPoolSize = mysql.maxPoolSize
        config.minimumIdle = mysql.minIdle
        config.connectionTimeout = mysql.connectionTimeout
        config.idleTimeout = mysql.idleTimeout
        config.maxLifetime = mysql.maxLifetime

        plugin.logger.info("MySQL config: ${mysql.host}:${mysql.port}/${mysql.database} (pool: ${mysql.maxPoolSize})")
    }

    private fun configureSqlite(config: HikariConfig) {
        val sqlite = configManager.getSQLiteConfig()
        val dbFile = java.io.File(plugin.dataFolder, sqlite.filePath)

        if (dbFile.parentFile != null && !dbFile.parentFile.exists()) {
            dbFile.parentFile.mkdirs()
            plugin.logger.info("Created database directory: ${dbFile.parentFile.absolutePath}")
        }

        config.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        config.driverClassName = "org.sqlite.JDBC"
        config.maximumPoolSize = 1

        plugin.logger.info("SQLite config: ${dbFile.absolutePath}")
    }

    private fun serializeItemStackArray(items: Array<ItemStack?>, plugin: HqngOrder): String {
        return try {
            val bytes = ByteArrayOutputStream().use { baos ->
                DataOutputStream(baos).use { dos ->
                    dos.writeInt(items.size)
                    for (item in items) {
                        if (item == null || item.type.isAir) {
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
            plugin.logger.log(Level.SEVERE, "Failed to serialize ItemStack array", e)
            ""
        }
    }

    private fun deserializeItemStackArray(base64: String, plugin: HqngOrder): Array<ItemStack?> {
        if (base64.isBlank()) return arrayOfNulls(STASH_SIZE)

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
            plugin.logger.log(Level.SEVERE, "Failed to deserialize ItemStack array", e)
            arrayOfNulls(STASH_SIZE)
        }
    }

    private fun PreparedStatement.bindOrderParams(order: BuyOrder) {
        setString(1, order.buyerUuid.toString())
        setString(2, order.buyerName)
        setString(3, order.itemStack.let { item ->
            val bytes = item.serializeAsBytes()
            Base64.getEncoder().encodeToString(bytes)
        })
        setInt(4, order.amountNeeded)
        setInt(5, order.amountFulfilled)
        setDouble(6, order.pricePerItem)
        setString(7, order.currency)
        setLong(8, order.createdAt)
        setLong(9, order.expiryTime)
        setString(10, order.status)
        setString(11, order.lockedBy?.toString())
        setLong(12, order.lockTime)
    }

    private fun ResultSet.toBuyOrder(plugin: HqngOrder): BuyOrder? {
        val orderId = getInt("id")
        val itemBase64 = getString("item_serialized") ?: run {
            plugin.logger.warning("Order #$orderId has null item_serialized, skipping")
            return null
        }

        val itemBytes = try {
            Base64.getDecoder().decode(itemBase64)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Corrupted item data for order #$orderId", e)
            return null
        }

        val itemStack = try {
            ItemStack.deserializeBytes(itemBytes)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to deserialize item for order #$orderId", e)
            return null
        }

        return BuyOrder(
            id = orderId,
            buyerUuid = UUID.fromString(getString("buyer_uuid")),
            buyerName = getString("buyer_name"),
            itemStack = itemStack,
            amountNeeded = getInt("amount_needed"),
            amountFulfilled = getInt("amount_fulfilled"),
            pricePerItem = getDouble("price_per_item"),
            currency = getString("currency"),
            createdAt = getLong("created_at"),
            expiryTime = getLong("expiry_time"),
            status = getString("status"),
            lockedBy = getString("locked_by")?.let { UUID.fromString(it) },
            lockTime = getLong("lock_time")
        )
    }

    data class LogEntry(
        val orderId: Int,
        val playerUuid: UUID,
        val action: String,
        val amount: Int,
        val details: String?,
        val timestamp: Long
    ) {

        fun format(): String {
            val time = Instant.ofEpochMilli(timestamp)
            return "[$time] Order #$orderId | Player: $playerUuid | Action: $action | Amount: $amount | Info: $details"
        }
    }
}

private enum class SqlDialect(
    val displayName: String,
    val sqlCreateOrdersTable: String,
    val sqlCreateStashTable: String,
    val sqlCreateLogsTable: String,
    val sqlUpsertStash: String
) {
    MYSQL(
        displayName = "MySQL",
        sqlCreateOrdersTable = """
            CREATE TABLE IF NOT EXISTS hqng_orders (
                id INT AUTO_INCREMENT PRIMARY KEY,
                buyer_uuid VARCHAR(36) NOT NULL,
                buyer_name VARCHAR(16) NOT NULL,
                item_serialized TEXT NOT NULL,
                amount_needed INT NOT NULL,
                amount_fulfilled INT NOT NULL DEFAULT 0,
                price_per_item DOUBLE NOT NULL,
                currency VARCHAR(20) NOT NULL DEFAULT 'MONEY',
                created_at BIGINT NOT NULL,
                expiry_time BIGINT NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                locked_by VARCHAR(36) NULL,
                lock_time BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """.trimIndent(),
        sqlCreateStashTable = """
            CREATE TABLE IF NOT EXISTS hqng_order_stash (
                player_uuid VARCHAR(36) PRIMARY KEY,
                stash_serialized TEXT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """.trimIndent(),
        sqlCreateLogsTable = """
            CREATE TABLE IF NOT EXISTS hqng_order_logs (
                id INT AUTO_INCREMENT PRIMARY KEY,
                order_id INT NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                action VARCHAR(64) NOT NULL,
                amount INT NOT NULL,
                details TEXT NULL,
                timestamp BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """.trimIndent(),
        sqlUpsertStash = """
            INSERT INTO hqng_order_stash (player_uuid, stash_serialized)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE stash_serialized = ?
        """.trimIndent()
    ),
    SQLITE(
        displayName = "SQLite",
        sqlCreateOrdersTable = """
            CREATE TABLE IF NOT EXISTS hqng_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                buyer_uuid VARCHAR(36) NOT NULL,
                buyer_name VARCHAR(16) NOT NULL,
                item_serialized TEXT NOT NULL,
                amount_needed INTEGER NOT NULL,
                amount_fulfilled INTEGER NOT NULL DEFAULT 0,
                price_per_item DOUBLE NOT NULL,
                currency VARCHAR(20) NOT NULL DEFAULT 'MONEY',
                created_at BIGINT NOT NULL,
                expiry_time BIGINT NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                locked_by VARCHAR(36) NULL,
                lock_time BIGINT NOT NULL DEFAULT 0
            )
        """.trimIndent(),
        sqlCreateStashTable = """
            CREATE TABLE IF NOT EXISTS hqng_order_stash (
                player_uuid VARCHAR(36) PRIMARY KEY,
                stash_serialized TEXT NOT NULL
            )
        """.trimIndent(),
        sqlCreateLogsTable = """
            CREATE TABLE IF NOT EXISTS hqng_order_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                action VARCHAR(64) NOT NULL,
                amount INTEGER NOT NULL,
                details TEXT NULL,
                timestamp BIGINT NOT NULL
            )
        """.trimIndent(),
        sqlUpsertStash = """
            INSERT OR REPLACE INTO hqng_order_stash (player_uuid, stash_serialized)
            VALUES (?, ?)
        """.trimIndent()
    )
}

private const val STASH_SIZE = 54