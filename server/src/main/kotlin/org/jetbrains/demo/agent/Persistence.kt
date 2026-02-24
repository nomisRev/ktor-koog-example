package org.jetbrains.demo.agent

import ai.koog.agents.features.sql.providers.CleanupConfig
import ai.koog.agents.features.sql.providers.SQLPersistenceSchemaMigrator
import ai.koog.agents.features.sql.providers.SQLPersistenceStorageProvider
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.less
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Temporary copy from Koog Exposed support since it's pre-1.x.x
 */
public class PostgresPersistenceStorageProvider(
    database: Database,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator = PostgresPersistenceSchemaMigrator(database, tableName),
    json: Json = PersistenceUtils.defaultCheckpointJson
) : ExposedPersistenceStorageProvider(database, tableName, ttlSeconds, migrator, json) {

    override suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }

    /**
     * PostgreSQL-optimized table with JSONB column.
     */
    override val checkpointsTable: PostgresCheckpointsTable = PostgresCheckpointsTable(tableName)

    /**
     * PostgreSQL-specific table definition.
     * Note: Currently uses TEXT for JSON storage. Future versions may use JSONB when Exposed adds better support.
     */
    public class PostgresCheckpointsTable(tableName: String) : CheckpointsTable(tableName)
}

/**
 * Implementation of [SQLPersistenceSchemaMigrator] for handling schema migrations in PostgreSQL
 * databases using the Exposed SQL library.
 *
 * This class focuses on PostgreSQL-specific schema migration requirements and provides
 * mechanisms to apply the necessary schema updates to ensure database compatibility.
 *
 * The [migrate] function, when implemented, will handle the execution of the schema
 * migrations asynchronously, allowing for seamless database updates as part of
 * an application's lifecycle.
 *
 * Designed to work with PostgreSQL, this migrator ensures that schema operations
 * respect PostgreSQL constraints, data types, and optimizations.
 */
public class PostgresPersistenceSchemaMigrator(private val database: Database, private val tableName: String) :
    SQLPersistenceSchemaMigrator {
    override suspend fun migrate() {
        transaction(database) {
            // Execute the raw PostgreSQL DDL
            exec(
                """
            -- Create the checkpoints table
            CREATE TABLE IF NOT EXISTS $tableName (
                persistence_id VARCHAR(255) NOT NULL,
                checkpoint_id VARCHAR(255) NOT NULL,
                created_at BIGINT NOT NULL,
                checkpoint_json TEXT NOT NULL,
                ttl_timestamp BIGINT NULL,
                version BIGINT NOT NULL,

                -- Primary key constraint
                CONSTRAINT ${tableName}_pkey PRIMARY KEY (persistence_id, checkpoint_id)
            )
                """.trimIndent()
            )

            // Create indexes
            exec(
                """
            CREATE INDEX IF NOT EXISTS idx_${tableName}_created_at ON $tableName (created_at)
                """.trimIndent()
            )

            exec(
                """
            CREATE INDEX IF NOT EXISTS idx_${tableName}_ttl_timestamp ON $tableName (ttl_timestamp)
                """.trimIndent()
            )

            exec(
                """
            CREATE UNIQUE INDEX IF NOT EXISTS ${tableName}_persistence_id_version_idx ON $tableName (persistence_id, version);
                """.trimIndent()
            )
        }
    }
}

public abstract class ExposedPersistenceStorageProvider(
    protected val database: Database,
    tableName: String = "agent_checkpoints",
    ttlSeconds: Long? = null,
    migrator: SQLPersistenceSchemaMigrator,
    private val json: Json = PersistenceUtils.defaultCheckpointJson
) : SQLPersistenceStorageProvider<ExposedPersistenceFilter>(
    tableName = tableName,
    ttlSeconds = ttlSeconds,
    migrator
) {
    /**
     * The Exposed table definition for checkpoints.
     * Uses a composite primary key and JSON column for checkpoint data.
     */
    protected open val checkpointsTable: CheckpointsTable = CheckpointsTable(tableName)

    /**
     * Track last cleanup time to avoid excessive cleanup operations
     */
    private var lastCleanupTime: Long = 0

    /**
     * Conditionally performs cleanup based on configuration and TTL settings.
     * Only runs cleanup if:
     * 1. Cleanup is enabled in config
     * 2. TTL is configured (ttlSeconds is not null)
     * 3. Enough time has passed since last cleanup
     */
    public suspend fun conditionalCleanup(cleanupConfig: CleanupConfig = CleanupConfig.default()) {
        // Skip cleanup entirely if disabled or no TTL configured
        if (!cleanupConfig.enabled || ttlSeconds == null) {
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()

        // Skip cleanup if we've cleaned up recently
        if (now - lastCleanupTime < cleanupConfig.intervalMs) {
            return
        }

        cleanupExpired()
    }

    override suspend fun cleanupExpired() {
        // Only perform cleanup if TTL is configured
        if (ttlSeconds == null) {
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()

        transaction {
            val deletedCount = checkpointsTable.deleteWhere {
                (checkpointsTable.ttlTimestamp less now) and (checkpointsTable.ttlTimestamp.isNotNull())
            }
            if (deletedCount > 0) {
                lastCleanupTime = now
            }
        }
    }

    override suspend fun getCheckpoints(agentId: String, filter: ExposedPersistenceFilter?): List<AgentCheckpointData> {
        if (filter == null) {
            val now = Clock.System.now().toEpochMilliseconds()
            return transaction {
                checkpointsTable.select(checkpointsTable.checkpointJson).where {
                    (checkpointsTable.persistenceId eq agentId) and
                            ((checkpointsTable.ttlTimestamp eq null) or (checkpointsTable.ttlTimestamp greaterEq now))
                }.mapNotNull { row ->
                    runCatching {
                        json.decodeFromString<AgentCheckpointData>(row[checkpointsTable.checkpointJson])
                    }.getOrNull()
                }
            }
        }

        return transaction {
            filter.query(checkpointsTable)
                .mapNotNull { row ->
                    runCatching {
                        json.decodeFromString<AgentCheckpointData>(row[checkpointsTable.checkpointJson])
                    }.getOrNull()
                }
        }
    }

    override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        val checkpointJson = json.encodeToString(agentCheckpointData)
        val ttlTimestamp = calculateTtlTimestamp(agentCheckpointData.createdAt)

        transaction {
            checkpointsTable.upsert {
                it[checkpointsTable.persistenceId] = agentId
                it[checkpointsTable.checkpointId] = agentCheckpointData.checkpointId
                it[checkpointsTable.createdAt] = agentCheckpointData.createdAt.toEpochMilliseconds()
                it[checkpointsTable.checkpointJson] = checkpointJson
                it[checkpointsTable.ttlTimestamp] = ttlTimestamp
                it[checkpointsTable.version] = agentCheckpointData.version
            }
        }
    }

    override suspend fun getLatestCheckpoint(agentId: String, filter: ExposedPersistenceFilter?): AgentCheckpointData? {
        if (filter == null) {
            val now = Clock.System.now().toEpochMilliseconds()
            return transaction {
                checkpointsTable
                    .select(checkpointsTable.checkpointJson)
                    .where {
                        (checkpointsTable.persistenceId eq agentId) and
                                ((checkpointsTable.ttlTimestamp eq null) or (checkpointsTable.ttlTimestamp greaterEq now))
                    }
                    .orderBy(checkpointsTable.version to SortOrder.DESC)
                    .limit(1)
                    .firstNotNullOfOrNull { row ->
                        runCatching {
                            json.decodeFromString<AgentCheckpointData>(row[checkpointsTable.checkpointJson])
                        }
                    }?.getOrNull()
            }
        }

        return transaction {
            filter
                .query(checkpointsTable)
                .limit(1)
                .firstOrNull()?.let { row ->
                    runCatching {
                        json.decodeFromString<AgentCheckpointData>(row[checkpointsTable.checkpointJson])
                    }.getOrNull()
                }
        }
    }

    override suspend fun deleteCheckpoint(agentId: String, checkpointId: String) {
        transaction {
            checkpointsTable.deleteWhere {
                (checkpointsTable.persistenceId eq agentId) and (checkpointsTable.checkpointId eq checkpointId)
            }
        }
    }

    override suspend fun deleteAllCheckpoints(agentId: String) {
        transaction {
            checkpointsTable.deleteWhere {
                checkpointsTable.persistenceId eq agentId
            }
        }
    }

    override suspend fun getCheckpointCount(agentId: String): Long {
        return transaction {
            checkpointsTable.selectAll().where {
                checkpointsTable.persistenceId eq agentId
            }.count()
        }
    }
}

public open class CheckpointsTable(tableName: String) : Table(tableName) {
    /**
     * Represents the column "persistence_id" in the CheckpointsTable.
     *
     * This column is part of the composite primary key (persistence_id, checkpoint_id) used to uniquely
     * identify checkpoint records in the table. It is a string column with a maximum length of 255
     * characters, and its value indicates the persistence identifier associated with a specific
     * checkpoint.
     */
    public val persistenceId: Column<String> = varchar("persistence_id", 255)

    /**
     * Represents the column "checkpoint_id" in the CheckpointsTable.
     *
     * This column is part of the composite primary key (persistence_id, checkpoint_id) used to uniquely
     * identify checkpoint records in the table. It is a string column with a maximum length of 255
     * characters, and its value specifies the unique identifier for a checkpoint within the context of
     * a persistence ID.
     */
    public val checkpointId: Column<String> = varchar("checkpoint_id", 255)

    /**
     * Represents the "created_at" column in the CheckpointsTable.
     *
     * This column stores the creation timestamp of a checkpoint.
     * It is indexed to enable efficient ordering and querying of checkpoints by their creation time.
     */
    public val createdAt: Column<Long> = long("created_at").index()

    /**
     * Represents the `checkpoint_json` column in the `CheckpointsTable`.
     *
     * This column stores serialized checkpoint data as a JSON string. It is a required field,
     * ensuring the persistence of critical state information for agent checkpoints.
     */
    public val checkpointJson: Column<String> = text("checkpoint_json")

    /**
     * Represents the TTL (Time-To-Live) timestamp for a checkpoint.
     *
     * This column stores an optional expiration timestamp, in milliseconds since the epoch,
     * indicating when the checkpoint is considered expired and eligible for removal or archival.
     *
     * If null, the checkpoint does not have a TTL and is retained indefinitely.
     *
     * Indexed to allow for efficient queries based on TTL.
     */
    public val ttlTimestamp: Column<Long?> = long("ttl_timestamp").nullable().index()

    /**
     * Represents the version of the checkpoint.
     *
     * This column stores a long integer indicating the version of the checkpoint.
     */
    public val version: Column<Long> = long("version")

    override val primaryKey: PrimaryKey = PrimaryKey(persistenceId, checkpointId)

    init {
        // Create composite index for efficient queries
        index(isUnique = false, persistenceId, createdAt)
        index(isUnique = true, persistenceId, version)
    }
}

public interface ExposedPersistenceFilter {
    /**
     * Build an Exposed DSL [Query] that selects rows from the given [table]
     * matching the desired conditions.
     *
     * The returned query is expected to be a complete selection (e.g. `table.select { ... }`),
     * which the storage provider may further refine (ordering, limiting) when fetching either
     * the latest checkpoint or a list of checkpoints.
     *
     * Requirements and tips for implementors:
     * - Do not perform side effects; just construct and return a query expression.
     * - Use the provided [CheckpointsTable] columns (e.g. `agentId`, `checkpointId`, `createdAt`, etc.).
     * - Keep the query portable across different SQL dialects supported by Exposed.
     *
     * @param table The Exposed [CheckpointsTable] to select from.
     * @return An Exposed [Query] that yields rows conforming to this filter.
     */
    public fun query(table: CheckpointsTable): Query
}
