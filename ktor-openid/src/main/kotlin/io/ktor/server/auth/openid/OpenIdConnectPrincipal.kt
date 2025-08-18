package io.ktor.server.auth.openid

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.interfaces.Payload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Principal for OpenID Connect authenticated user
 *
 * This class represents an authenticated user in the OpenID Connect flow.
 * It contains the ID token, an optional refresh token, and user information.
 */
@Serializable
data class OpenIdConnectPrincipal(
    val idToken: String,
    val refreshToken: String? = null,
    val userInfo: UserInfo
) {
    /**
     * User information extracted from the ID token
     *
     * This class contains standard OpenID Connect claims about the user.
     * Only the subject field is required, all other fields are optional.
     */
    @Serializable
    data class UserInfo(
        val subject: String,
        val name: String? = null,
        @SerialName("given_name")
        val givenName: String? = null,
        @SerialName("family_name")
        val familyName: String? = null,
        @SerialName("middle_name")
        val middleName: String? = null,
        val nickname: String? = null,
        @SerialName("preferred_username")
        val preferredUsername: String? = null,
        val profile: String? = null,
        val picture: String? = null,
        val website: String? = null,
        val gender: String? = null,
        val birthdate: String? = null,
        val zoneinfo: String? = null,
        val locale: String? = null,
        @SerialName("updated_at")
        val updatedAt: Long? = null,
        val email: String? = null,
        @SerialName("email_verified")
        val emailVerified: Boolean? = null,
        val address: Address? = null,
        @SerialName("phone_number")
        val phoneNumber: String? = null,
        @SerialName("phone_number_verified")
        val phoneNumberVerified: Boolean? = null
    ) {
        init {
            require(subject.isNotBlank()) { "subject must not be blank" }
        }

        /**
         * Address information for the user
         *
         * This class contains standard OpenID Connect address claims.
         * All fields are optional.
         */
        @Serializable
        data class Address(
            val formatted: String? = null,
            @SerialName("street_address")
            val streetAddress: String? = null,
            val locality: String? = null,
            val region: String? = null,
            @SerialName("postal_code")
            val postalCode: String? = null,
            val country: String? = null
        )
    }
}

/**
 * Extracts user information from a JWT payload
 *
 * @param this@extractUserInfo The JWT payload to extract user information from
 * @return User information extracted from the payload
 * @throws IllegalArgumentException if the subject claim is missing or blank
 */
fun Payload.extractUserInfo(): OpenIdConnectPrincipal.UserInfo {
    val subjectValue = subject ?: throw IllegalArgumentException("subject claim is missing from the JWT payload")
    if (subjectValue.isBlank()) throw IllegalArgumentException("subject claim must not be blank")

    return OpenIdConnectPrincipal.UserInfo(
        subject = subjectValue,
        name = claimOrNull("name"),
        givenName = claimOrNull("given_name"),
        familyName = claimOrNull("family_name"),
        middleName = claimOrNull("middle_name"),
        nickname = claimOrNull("nickname"),
        preferredUsername = claimOrNull("preferred_username"),
        profile = claimOrNull("profile"),
        picture = claimOrNull("picture"),
        website = claimOrNull("website"),
        gender = claimOrNull("gender"),
        birthdate = claimOrNull("birthdate"),
        zoneinfo = claimOrNull("zoneinfo"),
        locale = claimOrNull("locale"),
        updatedAt = claimOrNull("updated_at"),
        email = claimOrNull("email"),
        emailVerified = claimOrNull("email_verified"),
        address = claimOrNull<Map<String, Any?>>("address")?.let {
            OpenIdConnectPrincipal.UserInfo.Address(
                formatted = it["formatted"] as? String,
                streetAddress = it["street_address"] as? String,
                locality = it["locality"] as? String,
                region = it["region"] as? String,
                postalCode = it["postal_code"] as? String,
                country = it["country"] as? String
            )
        },
        phoneNumber = claimOrNull("phone_number"),
        phoneNumberVerified = claimOrNull("phone_number_verified")
    )
}

/**
 * Safely extracts a claim from a JWT payload
 *
 * @param name The name of the claim to extract
 * @return The claim value, or null if the claim is missing or cannot be decoded
 */
private inline fun <reified T : Any> Payload.claimOrNull(name: String): T? =
    try {
        getClaim(name).`as`(T::class.javaObjectType)
    } catch (ex: JWTDecodeException) {
        null
    }
