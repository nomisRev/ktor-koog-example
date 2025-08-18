package io.ktor.server.auth.openid

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

/**
 * Discovers the OpenID Connect configuration for the given issuer
 *
 * @param issuer The OpenID Connect issuer URL
 * @return The OpenID Connect configuration
 * @throws IllegalArgumentException if the issuer URL is blank
 * @throws ClientRequestException if the discovery request fails
 * @throws IllegalStateException if the discovery response is missing required fields
 */
suspend fun HttpClient.discover(issuer: String): OpenIdConfiguration {
    require(issuer.isNotBlank()) { "Issuer URL must not be blank" }
    val discoveryUrl = "${issuer.trimEnd('/')}/.well-known/openid-configuration"
    return withTimeout(10.seconds) {
        get(discoveryUrl).body<OpenIdConfiguration>()
    }
}

/**
 * OpenID Connect Discovery Document
 * Based on https://openid.net/specs/openid-connect-discovery-1_0.html
 *
 * Only has 4 required values [issuer, authorization_endpoint, token_endpoint, jwksUri].
 * That is all that is needed to automatically discover OpenID Connect oauth2 and JWK configurations.
 */
@Serializable
data class OpenIdConfiguration(
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("userinfo_endpoint")
    val userinfoEndpoint: String? = null,
    @SerialName("jwks_uri")
    val jwksUri: String,
    @SerialName("registration_endpoint")
    val registrationEndpoint: String? = null,
    @SerialName("scopes_supported")
    val scopesSupported: List<String>? = null,
    @SerialName("response_types_supported")
    val responseTypesSupported: List<String>? = null,
    @SerialName("response_modes_supported")
    val responseModesSupported: List<String>? = null,
    @SerialName("grant_types_supported")
    val grantTypesSupported: List<String>? = null,
    @SerialName("acr_values_supported")
    val acrValuesSupported: List<String>? = null,
    @SerialName("subject_types_supported")
    val subjectTypesSupported: List<String>? = null,
    @SerialName("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>? = null,
    @SerialName("id_token_encryption_alg_values_supported")
    val idTokenEncryptionAlgValuesSupported: List<String>? = null,
    @SerialName("id_token_encryption_enc_values_supported")
    val idTokenEncryptionEncValuesSupported: List<String>? = null,
    @SerialName("userinfo_signing_alg_values_supported")
    val userinfoSigningAlgValuesSupported: List<String>? = null,
    @SerialName("userinfo_encryption_alg_values_supported")
    val userinfoEncryptionAlgValuesSupported: List<String>? = null,
    @SerialName("userinfo_encryption_enc_values_supported")
    val userinfoEncryptionEncValuesSupported: List<String>? = null,
    @SerialName("request_object_signing_alg_values_supported")
    val requestObjectSigningAlgValuesSupported: List<String>? = null,
    @SerialName("request_object_encryption_alg_values_supported")
    val requestObjectEncryptionAlgValuesSupported: List<String>? = null,
    @SerialName("request_object_encryption_enc_values_supported")
    val requestObjectEncryptionEncValuesSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgValuesSupported: List<String>? = null,
    @SerialName("display_values_supported")
    val displayValuesSupported: List<String>? = null,
    @SerialName("claim_types_supported")
    val claimTypesSupported: List<String>? = null,
    @SerialName("claims_supported")
    val claimsSupported: List<String>? = null,
    @SerialName("service_documentation")
    val serviceDocumentation: String? = null,
    @SerialName("claims_locales_supported")
    val claimsLocalesSupported: List<String>? = null,
    @SerialName("ui_locales_supported")
    val uiLocalesSupported: List<String>? = null,
    @SerialName("claims_parameter_supported")
    val claimsParameterSupported: Boolean? = null,
    @SerialName("request_parameter_supported")
    val requestParameterSupported: Boolean? = null,
    @SerialName("request_uri_parameter_supported")
    val requestUriParameterSupported: Boolean? = null,
    @SerialName("require_request_uri_registration")
    val requireRequestUriRegistration: Boolean? = null,
    @SerialName("op_policy_uri")
    val opPolicyUri: String? = null,
    @SerialName("op_tos_uri")
    val opTosUri: String? = null,
    @SerialName("end_session_endpoint")
    val endSessionEndpoint: String? = null
)
