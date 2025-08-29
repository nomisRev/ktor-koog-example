package org.jetbrains.demo.agent.koog

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import ai.koog.prompt.message.Message
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.datetime.toStdlibInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private object AgentCheckpointDataTable : IdTable<String>("agent_checkpoint_data_table") {
    override val id = text("checkpoint_id").entityId().uniqueIndex()
    val createdAt = timestamp("created_at")
    val nodeId = text("node_id")
    val lastInput = text("last_input").transform(
        { Json.decodeFromString(JsonElement.serializer(), it) },
        { Json.encodeToString(JsonElement.serializer(), it) },
    )
    val messageHistory: Column<List<Message>> = array<String>("message_history").transform(
        { it.map { Json.decodeFromString(Message.serializer(), it) } },
        { it.map { Json.encodeToString(Message.serializer(), it) } }
    )
}

class ExposedPersistencyStorageProvider(private val database: Database) : PersistencyStorageProvider {
    override suspend fun getCheckpoints(): List<AgentCheckpointData> = transaction(database) {
        AgentCheckpointDataTable.selectAll().map { it.toAgentCheckpointData() }
    }

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData): Unit = transaction(database) {
        AgentCheckpointDataTable.insert { insert ->
            insert[id] = agentCheckpointData.checkpointId
            insert[createdAt] = agentCheckpointData.createdAt.toStdlibInstant()
            insert[nodeId] = agentCheckpointData.nodeId
            insert[lastInput] = agentCheckpointData.lastInput
            insert[messageHistory] = agentCheckpointData.messageHistory
        }
    }

    override suspend fun getLatestCheckpoint(): AgentCheckpointData? = transaction(database) {
        AgentCheckpointDataTable.selectAll()
            .orderBy(AgentCheckpointDataTable.createdAt, SortOrder.ASC)
            .firstOrNull()
            ?.toAgentCheckpointData()
    }

    fun ResultRow.toAgentCheckpointData(): AgentCheckpointData = AgentCheckpointData(
        checkpointId = this[AgentCheckpointDataTable.id].value,
        nodeId = this[AgentCheckpointDataTable.nodeId],
        lastInput = this[AgentCheckpointDataTable.lastInput],
        createdAt = this[AgentCheckpointDataTable.createdAt].toDeprecatedInstant(),
        messageHistory = this[AgentCheckpointDataTable.messageHistory],
    )
}