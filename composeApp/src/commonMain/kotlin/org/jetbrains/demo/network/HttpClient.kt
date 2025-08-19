package org.jetbrains.demo.network

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.*
import org.jetbrains.demo.auth.TokenProvider
import org.jetbrains.demo.config.AppConfig

/**
 * [TokenProvider] is optional since browser works with sessions.
 */
fun HttpClient(
    baseLogger: Logger,
    config: AppConfig,
    tokenProvider: TokenProvider?
): HttpClient = HttpClient {
    val logger = baseLogger.withTag("HttpClient")
    install(ContentNegotiation) { json() }
    install(SSE)
    logger.d("BaseURL: ${config.apiBaseUrl})")
    defaultRequest { url(config.apiBaseUrl) }
    if (tokenProvider != null) {
        logger.d("TokenProvider is not null, applying header authentication")
        withAuthBearer(tokenProvider, logger)
    } else {
        logger.d("TokenProvider is null, skipping header authentication. (On browser this is expected, we use sessions)")
    }
}

private fun HttpClientConfig<*>.withAuthBearer(tokenProvider: TokenProvider, logger: Logger) {
    install(Auth) {
        bearer {
            loadTokens {
                val token = tokenProvider.getToken() ?: tokenProvider.refreshToken()
                logger.d("Loading token for request. Token available: ${token != null}")
                token?.let {
                    BearerTokens(accessToken = it, refreshToken = "null")
                }?.also { logger.d("BearerTokens token: $it") }
            }

            refreshTokens {
                logger.d("Refreshing token...")
                val originalToken = tokenProvider.getToken()
                Logger.d("Original token: $originalToken")
                val newToken = if (originalToken == null) tokenProvider.refreshToken() else originalToken
                Logger.d("New token: $newToken")
                if (newToken != null) {
                    logger.d("Token refresh successful")
                    BearerTokens(accessToken = newToken, refreshToken = null)
                } else {
                    logger.w("Token refresh failed")
                    null
                }
            }
        }
    }
}
