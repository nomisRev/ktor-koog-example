package io.ktor.server.auth.openid

import kotlinx.serialization.Serializable

@Serializable
data class TokenRefreshResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int? = null,
    val refresh_token: String? = null,
    val id_token: String? = null,
    val scope: String? = null
)