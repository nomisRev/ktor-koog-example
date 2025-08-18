package org.jetbrains.demo.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateUser(
    val email: String?,
    val displayName: String?,
    @SerialName("about_me")
    val aboutMe: String?
)

@Serializable
data class User(
    val userId: Long,
    val subject: String,
    val email: String? = null,
    val displayName: String?,
    val aboutMe: String?,
)
