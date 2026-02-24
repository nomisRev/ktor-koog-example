package org.jetbrains.demo.website

import io.ktor.server.application.Application
import io.ktor.server.auth.openid.OpenIdConnectPrincipal
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

fun Application.website() = routing {
// TODO: re-enable authentication
//    get("/") {
//        val hasSession = call.sessions.get<OpenIdConnectPrincipal>() != null
//        val redirectUrl = if (hasSession) "/home" else "login"
//        call.respondRedirect(redirectUrl)
//    }

    staticResources("/", "web")
    staticResources("/login", "web")
    staticResources("/home", "web") {
        modify { _, call ->
            if (call.sessions.get<OpenIdConnectPrincipal>() == null) call.respondRedirect("/login")
        }
    }
}
