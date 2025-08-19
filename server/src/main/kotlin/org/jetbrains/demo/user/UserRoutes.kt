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
                val userInfo =
                    call.principal<OpenIdConnectPrincipal.UserInfo>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val user = users.create(userInfo.subject)
                call.respond(HttpStatusCode.OK, user)
            }

            get("/") {
                val userInfo =
                    call.principal<OpenIdConnectPrincipal.UserInfo>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val user = users.findOrNull(userInfo.subject)
                if (user == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(HttpStatusCode.OK, user)
            }

            get("/email/{email}") {
                val email = call.parameters.getOrFail("email")
                val user = users.findByEmail(email) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(HttpStatusCode.OK, user)
            }

            put("/update") {
                val userInfo =
                    call.principal<OpenIdConnectPrincipal.UserInfo>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val update = call.receive<UpdateUser>()
                val updatedUser = users.create(userInfo.subject, update)
                call.respond(HttpStatusCode.OK, updatedUser)
            }

            delete("/delete") {
                val userInfo =
                    call.principal<OpenIdConnectPrincipal.UserInfo>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                users.delete(userInfo.subject)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
