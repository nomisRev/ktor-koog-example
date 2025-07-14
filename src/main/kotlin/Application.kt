package com.example

import com.example.agent.AgentEvent
import com.example.agent.agent
import com.example.agent.agentConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.server.util.getOrFail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppConfig(
    val host: String,
    val port: Int,
    val apiKey: String,
    val mcpGatewayImage: String,
    val weatherApiUrl: String,
)

fun main() {
    val config = ApplicationConfig("application.yaml")
        .property("app")
        .getAs<AppConfig>()

    embeddedServer(Netty, host = config.host, port = config.port) {
        app(config)
    }.start(wait = true)
}

suspend fun Application.app(config: AppConfig) {
    val agentConfig = agentConfig(config)
    install(SSE)
    routing {
        sse("/plan") {
            val userQuestion = call.request.queryParameters.getOrFail("question")
            agentConfig.agent(userQuestion) {
                println(it)
                send(data = Json.encodeToString(AgentEvent.serializer(), it))
            }
        }
    }
}
