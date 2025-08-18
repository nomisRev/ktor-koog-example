package org.jetbrains.demo.chat.repository

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.demo.config.AppConfig
import co.touchlab.kermit.Logger
import io.ktor.client.request.url

/**
 * Implementation of ChatRepository that uses HttpClient to communicate with the chat API.
 */
class HttpChatRepository(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    base: Logger
) : ChatRepository {
    override suspend fun sendMessage(message: String): Flow<String> = flow {
        httpClient.sse(request = {
            url("chat").apply {
                parameter("question", message)
            }
        }) {
            incoming.collect { event ->
                event.data?.let { token ->
                    emit(token)
                }
            }
        }
    }
}
