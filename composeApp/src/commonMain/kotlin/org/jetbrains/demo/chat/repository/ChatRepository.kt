package org.jetbrains.demo.chat.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chat functionality.
 * Handles communication with the chat API.
 */
interface ChatRepository {
    /**
     * Sends a message to the chat API and returns a flow of response tokens.
     * @param message The message to send
     * @return A flow of response tokens that can be collected to build the complete response
     */
    suspend fun sendMessage(message: String): Flow<String>
}