package org.jetbrains.demo.agent.chat

import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.sse
import kotlinx.serialization.json.Json
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.JourneyForm

fun Application.agent(agent: TravelAgent) {
    routing {
        route("/plan", HttpMethod.Post) {
            sse {
                val form = call.receive<JourneyForm>()
                agent.playJourney(form)
                    .collect { send(data = Json.encodeToString(AgentEvent.serializer(), it)) }
            }
        }
    }
}
