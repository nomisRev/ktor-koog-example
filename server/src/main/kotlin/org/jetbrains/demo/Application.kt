package org.jetbrains.demo

import ai.koog.ktor.Koog
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.openid.OpenIdConnect
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.sse.SSE
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.demo.agent.chat.agent
import org.jetbrains.demo.agent.koog.ExposedPersistencyStorageProvider
import org.jetbrains.demo.user.ExposedUserRepository
import org.jetbrains.demo.user.UserRepository
import org.jetbrains.demo.user.userRoutes
import org.jetbrains.demo.website.website
import kotlin.time.Duration.Companion.seconds

@Serializable
data class AppConfig(
    val host: String,
    val port: Int,
    val auth: AuthConfig,
    val apiKey: String,
    val weatherApiUrl: String,
    val database: DatabaseConfig,
)

@Serializable
data class AuthConfig(val issuer: String, val secret: String, val clientId: String)

fun main() {
    val config = ApplicationConfig("application.yaml")
        .property("app")
        .getAs<AppConfig>()

    embeddedServer(Netty, host = config.host, port = config.port) {
        app(config)
    }.start(wait = true)
}

suspend fun Application.app(config: AppConfig) {
    val database = database(config.database)
    val userRepository: UserRepository = ExposedUserRepository(database)
    val agentPersistency = ExposedPersistencyStorageProvider(database)
    install(Koog) {
        llm {
            openAI(config.apiKey)
        }
    }

    configure(config)
    agent(config, agentPersistency)
    website()
    userRoutes(userRepository)
}

private fun Application.configure(config: AppConfig) {
    install(SSE)
//    install(OpenIdConnect) {
//        jwk(config.auth.issuer) {
//            name = "google"
//        }
//        oauth(config.auth.issuer, config.auth.clientId, config.auth.secret) {
//            loginUri { path("login") }
//            logoutUri { path("logout") }
//            refreshUri { path("refresh") }
//            redirectUri { path("callback") }
//            redirectOnSuccessUri { path("home") }
//        }
//    }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
    }

    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            isLenient = true
        })
    }
}
