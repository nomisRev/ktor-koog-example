package org.jetbrains.demo

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.sse.SSE
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.demo.agent.chat.agent
import org.jetbrains.demo.user.userRoutes
import org.jetbrains.demo.website.website
import kotlin.String

@Serializable
data class AppConfig(
    val host: String,
    val port: Int,
    val auth: AuthConfig,
    val openAIKey: String,
    val anthropicKey: String,
    val langfuseUrl: String,
    val langfusePublicKey: String,
    val langfuseSecretKey: String,
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
    val module = module(config)
    install(SSE)
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            isLenient = true
        })
    }

    agent(module.travelAgent)
    website()
    userRoutes(module.userRepository)
}

