package org.jetbrains.demo.user

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.openid.OpenIdConnectPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail

fun Application.userRoutes(users: UserRepository) = routing {
    authenticate("google") {
        route("user") {
            post("/register") {
                val principal =
                    call.principal<OpenIdConnectPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val user = users.create(principal.userInfo.subject)
                call.respond(HttpStatusCode.OK, user)
            }

            get("/") {
                val principal =
                    call.principal<OpenIdConnectPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val user = users.findOrNull(principal.userInfo.subject)
                if (user == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(HttpStatusCode.OK, user)
            }

            get("/email/{email}") {
                val email = call.parameters.getOrFail("email")
                val user = users.findByEmail(email) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(HttpStatusCode.OK, user)
            }

            put("/update") {
                val principal =
                    call.principal<OpenIdConnectPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val update = call.receive<UpdateUser>()
                val updatedUser = users.create(principal.userInfo.subject, update)
                call.respond(HttpStatusCode.OK, updatedUser)
            }

            delete("/delete") {
                val principal =
                    call.principal<OpenIdConnectPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                users.delete(principal.userInfo.subject)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
