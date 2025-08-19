package io.ktor.server.auth.openid

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.http.fullPath
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.BaseApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthAuthenticationProvider
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTAuthenticationProvider
import io.ktor.server.auth.jwt.JWTConfigureFunction
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.config.getAs
import io.ktor.server.plugins.origin
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.CookieSessionBuilder
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

class OpenIdConnect(
    val application: Application,
    private val oauth: Map<String, Triple<String, String, Config.OAuthConfig>>,
    private val jwk: Map<String, Config.JwkConfig>,
    private val configurations: Map<String, Deferred<OpenIdConfiguration>>,
    private val client: HttpClient,
) {

    /**
     * Refreshes an OpenID Connect token using the refresh token
     */
    suspend fun refreshToken(
        issuer: String,
        refreshToken: String
    ): OpenIdConnectPrincipal {
        val (clientId, clientSecret, _) = oauth[issuer]
            ?: throw IllegalArgumentException("No OAuth configuration found for issuer: $issuer")
        val configuration = configurations[issuer]?.await()
            ?: throw IllegalArgumentException("No configuration found for issuer: $issuer")

        return refreshOpenIdToken(configuration, clientId, clientSecret, refreshToken, client)
    }

    /**
     * Refreshes a token and updates the session
     */
//    suspend fun refreshTokenAndUpdateSession(call: ApplicationCall, issuer: String): OpenIdConnectPrincipal? {
//        val currentSession = call.sessions.get(sessionConfig.name) as? OpenIdConnectPrincipal
//            ?: return null
//
//        val refreshTokenValue = currentSession.refreshToken
//            ?: return null
//
//        return try {
//            val newPrincipal = refreshToken(issuer, refreshTokenValue)
//            call.sessions.set(newPrincipal)
//            application.log.info("Token refreshed successfully")
//            newPrincipal
//        } catch (e: Exception) {
//            application.log.warn("Failed to refresh token: ${e.message}")
//            // Clear invalid session
//            call.sessions.clear(sessionConfig.name)
//            null
//        }
//    }

    /**
     * Middleware to automatically refresh tokens when they're about to expire
     */
//    suspend fun handleTokenRefreshMiddleware(call: ApplicationCall, issuer: String): Boolean {
//        if (!sessionConfig.autoRefreshTokens) {
//            return true
//        }
//
//        val currentSession = call.sessions.get(sessionConfig.name) as? OpenIdConnectPrincipal
//            ?: return true // No session, continue
//
//        if (shouldRefreshToken(currentSession)) {
//            application.log.debug("Token needs refresh")
//            val refreshedPrincipal = refreshTokenAndUpdateSession(call, issuer)
//            if (refreshedPrincipal == null) {
//                application.log.warn("Token refresh failed, session invalidated")
//                return false // Token refresh failed, session is invalid
//            }
//        }
//
//        return true // Continue with request
//    }

//    /**
//     * Checks if a token needs to be refreshed based on its expiration time
//     */
//    fun shouldRefreshToken(principal: OpenIdConnectPrincipal): Boolean {
//        if (!sessionConfig.autoRefreshTokens || principal.refreshToken == null) {
//            return false
//        }
//
//        return try {
//            val jwt = JWT.decode(principal.idToken)
//            val expiresAt = jwt.expiresAt?.toInstant() ?: return false
//            val now = Instant.now()
//            val threshold = now.plusSeconds(sessionConfig.tokenRefreshThreshold)
//
//            expiresAt.isBefore(threshold)
//        } catch (e: Exception) {
//            application.log.warn("Failed to decode JWT for refresh check: ${e.message}")
//            false
//        }
//    }

    class Config {
        internal var jwks: MutableMap<String, JwkConfig> = mutableMapOf()
        internal var oauth: MutableMap<String, Triple<String, String, OAuthConfig>> = mutableMapOf()

        var httpClient: HttpClient? = null
        var storage: SessionStorageMemory? = null

        class JwkConfig {
            var name: String = "openid-connect-jwk"
            var jwkBuilder: JwkProviderBuilder.() -> Unit = { cached(true).rateLimited(true) }
            var verify: JWTConfigureFunction = {}
            var validate: suspend ApplicationCall.(JWTCredential) -> Any? =
                { credential -> credential.payload.extractUserInfo() }
        }

        fun jwk(issuer: String, configure: JwkConfig.() -> Unit = {}) {
            jwks[issuer] = JwkConfig().apply(configure)
        }

        class OAuthConfig {
            var name: String = "openid-connect-oauth"
            var scopes: List<String> = listOf("openid", "profile", "email")
            internal var redirectUri: URLBuilder.() -> Unit = { path("oauth", name, "redirect") }
            internal var refreshUri: URLBuilder.() -> Unit = { path("oauth", name, "refresh") }
            internal var loginUri: URLBuilder.() -> Unit = { path("oauth", name, "login") }
            internal var logoutUri: URLBuilder.() -> Unit = { path("oauth", name, "logout") }
            internal var redirectOnSuccessUri: (URLBuilder.() -> Unit)? = null
            internal var postLogoutRedirectUri: (URLBuilder.() -> Unit)? = null
            internal var sessionName: String = "OPENID_SESSION"
            internal var cookieSessionBuilder: (CookieSessionBuilder<OpenIdConnectPrincipal>.() -> Unit)? =
                null
            internal var handleSuccess: (suspend RoutingContext.(OpenIdConnectPrincipal) -> Unit)? = null
            internal var handleFailure: (suspend RoutingContext.() -> Unit)? = null
            internal var handleLogoutWithEndSession: (suspend RoutingContext.(OpenIdConnectPrincipal, OpenIdConfiguration, String) -> Unit)? =
                null
            internal var handleLogoutFallback: (suspend RoutingContext.(OpenIdConnectPrincipal, OpenIdConfiguration) -> Unit)? =
                null

            fun session(
                name: String = "OPENID_SESSION",
                configure: CookieSessionBuilder<OpenIdConnectPrincipal>.() -> Unit
            ) {
                sessionName = name
                cookieSessionBuilder = configure
            }

            fun redirectUri(uri: URLBuilder.() -> Unit) {
                redirectUri = uri
            }

            fun refreshUri(uri: URLBuilder.() -> Unit) {
                refreshUri = uri
            }

            fun loginUri(uri: URLBuilder.() -> Unit) {
                loginUri = uri
            }

            fun logoutUri(uri: URLBuilder.() -> Unit) {
                logoutUri = uri
            }

            fun redirectOnSuccessUri(uri: URLBuilder.() -> Unit) {
                redirectOnSuccessUri = uri
            }

            fun postLogoutRedirectUri(uri: URLBuilder.() -> Unit) {
                postLogoutRedirectUri = uri
            }

            fun onSuccess(block: suspend RoutingContext.(OpenIdConnectPrincipal) -> Unit) {
                handleSuccess = block
            }

            fun onFailure(block: suspend RoutingContext.() -> Unit) {
                handleFailure = block
            }

            fun onLogoutWithEndSession(block: suspend RoutingContext.(OpenIdConnectPrincipal, OpenIdConfiguration, String) -> Unit) {
                handleLogoutWithEndSession = block
            }

            fun onLogoutFallback(block: suspend RoutingContext.(OpenIdConnectPrincipal, OpenIdConfiguration) -> Unit) {
                handleLogoutFallback = block
            }
        }

        fun oauth(
            issuer: String,
            clientId: String,
            clientSecret: String,
            configure: OAuthConfig.() -> Unit = {}
        ) {
            oauth[issuer] = Triple(clientId, clientSecret, OAuthConfig().apply(configure))
        }

        /**
         * Represents a single configured openid connect provider, the provider name is the 'key' to this value.
         */
        @Serializable
        private data class EnvConfig(
            val issuer: String,
            val clientId: String? = null,
            val clientSecret: String? = null,
            val scopes: List<String> = listOf("openid", "profile", "email"),
        )

        /**
         * Load configurations from application.environment.config
         * The `ktor.openid` key points to a map, which has _dynamic_ key entries that correspond to the authentication config
         * Example:
         *
         * ```yaml
         * ktor.openid:
         *     google.issuer: "www.accounts.google.com"
         *     supabase:
         *         issuer: "wwww.accounts.supabase.com
         *         clientId: "$SUPABASE_CLIENT_ID"
         *         clientSecret: "$SUPABSE_CLIENT_SECRET"
         * ```
         *
         * This needs to setup a:
         *  - jwk name = "google"  issuer = ktor.openid.google.issuer
         *  - jwk name = "supabase" issuer =  ktor.openid.supabase.issuer
         *  - oauth for supabase ktor.openid.supabase.issuer, clientId, clientSecret
         */
        internal fun loadConfigFromEnvironment(application: Application) {
            val openIdConfig = application.environment.config.config("ktor.openid")
            openIdConfig.keys().forEach { key ->
                val providerName = key.substringBefore(".")
                val env = openIdConfig.property(providerName).getAs<EnvConfig>()
                val jwkProviderName =
                    if (env.clientId == null || env.clientSecret == null) providerName else "$providerName-jwk"

                jwk(env.issuer) {
                    name = jwkProviderName
                }

                application.log.debug("Added JWK configuration for $providerName with issuer ${env.issuer}")

                if (env.clientId != null && env.clientSecret != null) {
                    oauth(env.issuer, env.clientId, env.clientSecret) {
                        name = "$providerName-oauth"
                        scopes = env.scopes
                    }

                    application.log.debug("Added OAuth configuration for $providerName with issuer ${env.issuer}")
                }
            }
        }
    }

    companion object : BaseApplicationPlugin<ApplicationCallPipeline, Config, OpenIdConnect> {
        override val key: AttributeKey<OpenIdConnect> = AttributeKey("OpenIdConnect")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Config.() -> Unit
        ): OpenIdConnect {
            val application = when (pipeline) {
                is RoutingNode -> pipeline.application
                is Application -> pipeline
                else -> error("Unsupported pipeline type: ${pipeline::class}")
            }

            val config = Config().apply {
                loadConfigFromEnvironment(application)
                configure()
            }

            val client = config.httpClient ?: application.defaultHttpClient()

            val configurations = (config.oauth.keys + config.jwks.keys)
                .associateWith { issuer -> application.async { client.discover(issuer) } }

            application.configureAuthentication(
                config,
                configurations,
                client,
                config.storage ?: SessionStorageMemory()
            )

            val openIdConnect = OpenIdConnect(
                application,
                config.oauth,
                config.jwks,
                configurations,
                client,
            )

            return openIdConnect
        }
    }
}

/**
 * Configure JWK, and OAuth when clientId, and clientSecret are available.
 */
private fun Application.configureAuthentication(
    config: OpenIdConnect.Config,
    configurations: Map<String, Deferred<OpenIdConfiguration>>,
    client: HttpClient,
    storageMemory: SessionStorageMemory
) {
    if (config.oauth.isNotEmpty()) {
        this@configureAuthentication.install(Sessions) {
            config.oauth.forEach { (_, triple) ->
                cookie<OpenIdConnectPrincipal>(
                    triple.third.sessionName,
                    storage = storageMemory
                ) {
                    if (triple.third.cookieSessionBuilder != null) {
                        triple.third.cookieSessionBuilder?.invoke(this)
                    } else {
                        cookie.secure = !this@configureAuthentication.developmentMode
                        cookie.httpOnly = true
                        cookie.extensions["SameSite"] = "lax"
                    }
                }
            }
        }
    }

    config.oauth.forEach { (issuer, triple) ->
        val (clientId, clientSecret, oauthConfig) = triple
        val redirectUri = URLBuilder().apply(oauthConfig.redirectUri).build()
        val loginUri = URLBuilder().apply(oauthConfig.loginUri).build()
        val redirectOnSuccessUri = URLBuilder().apply(oauthConfig.redirectOnSuccessUri ?: oauthConfig.loginUri).build()
        val refreshUri = URLBuilder().apply(oauthConfig.refreshUri).build()
        val logoutUri = URLBuilder().apply(oauthConfig.logoutUri).build()
        val postLogoutRedirectUri =
            URLBuilder().apply(oauthConfig.postLogoutRedirectUri ?: oauthConfig.loginUri).build()
        val handleFailure = oauthConfig.handleFailure ?: { call.respond(Unauthorized) }
        val handleSuccess = oauthConfig.handleSuccess ?: { principal ->
            call.sessions.set(principal)
            call.respondRedirect(redirectOnSuccessUri.fullPath)
        }
        val handleLogoutWithEndSession = oauthConfig.handleLogoutWithEndSession ?: { principal, _, endSessionEndpoint ->
            val absolutePostLogout =
                "${call.request.origin.scheme}://${call.request.host()}:${call.request.port()}$postLogoutRedirectUri"
            val redirectUrl = URLBuilder(endSessionEndpoint).apply {
                parameters.append("id_token_hint", principal.idToken)
                parameters.append("post_logout_redirect_uri", absolutePostLogout)
            }.buildString()
            call.respondRedirect(redirectUrl)
        }
        val handleLogoutFallback =
            oauthConfig.handleLogoutFallback ?: { _, _ -> call.respondRedirect(postLogoutRedirectUri) }

        openIdOauth2(
            configurations[issuer]!!,
            oauthConfig,
            client,
            clientId,
            clientSecret,
            loginUri.fullPath,
            redirectUri.encodedPathAndQuery,
            refreshUri.fullPath,
            logoutUri.fullPath,
            postLogoutRedirectUri.fullPath,
            handleSuccess,
            handleFailure,
            handleLogoutWithEndSession,
            handleLogoutFallback
        )
    }

    config.jwks.forEach { (issuer, jwkConfig) ->
        val checkSession = config.oauth[issuer] != null
        authentication {
            register(
                OpenIdConnectJwkAndSessionAuthenticationProvider(
                    application = this@configureAuthentication,
                    name = jwkConfig.name,
                    config = OpenIdConnectJwkAndSessionAuthenticationProvider.Config(jwkConfig.name),
                    configuration = configurations[issuer]!!,
                    checkSession = checkSession,
                    jwkConfig = jwkConfig,
                )
            )
        }
    }
}

/** Preconfigured HttpClient that automatically closes when the server stops */
private fun Application.defaultHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}.also { closeable -> monitor.subscribe(ApplicationStopped) { closeable.close() } }

/**
 * [AuthenticationProvider] that delegates to [OAuthAuthenticationProvider] but awaits the auto-discovery in [onAuthenticate].
 */
private class OpenIdConnectOauthAuthenticationProvider(
    application: Application,
    name: String,
    config: Config,
    private val httpClient: HttpClient,
    private val redirect: String,
    private val scopes: Set<String>,
    private val clientId: String,
    private val clientSecret: String,
    private val configuration: Deferred<OpenIdConfiguration>,
) : AuthenticationProvider(config) {
    private val provider = application.async {
        val config = configuration.await()
        @Suppress("unused", "invisible_reference")
        OAuthAuthenticationProvider(OAuthAuthenticationProvider.Config(name).apply {
            urlProvider = {
                val url = "${request.origin.scheme}://${request.host()}:${request.port()}$redirect"
                application.log.debug("OAuth callback url: $url")
                url
            }
            client = httpClient
            this.providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = name,
                    authorizeUrl = config.authorizationEndpoint,
                    accessTokenUrl = config.tokenEndpoint,
                    requestMethod = HttpMethod.Post,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    defaultScopes = this@OpenIdConnectOauthAuthenticationProvider.scopes.toList()
                )
            }
        })
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        provider.await().onAuthenticate(context)
    }

    class Config(name: String?) : AuthenticationProvider.Config(name)
}

/**
 * Utilitiy function for setting up both OAuth, and Routing for OpenId Connect
 */
private fun Application.openIdOauth2(
    configuration: Deferred<OpenIdConfiguration>,
    config: OpenIdConnect.Config.OAuthConfig,
    client: HttpClient,
    clientId: String,
    clientSecret: String,
    login: String,
    redirect: String,
    refresh: String,
    logout: String,
    postLogoutRedirect: String,
    handleCallback: suspend RoutingContext.(OpenIdConnectPrincipal) -> Unit,
    handleFailure: suspend RoutingContext.() -> Unit,
    handleLogoutWithEndSession: suspend RoutingContext.(OpenIdConnectPrincipal, OpenIdConfiguration, String) -> Unit,
    handleLogoutFallback: suspend RoutingContext.(OpenIdConnectPrincipal, OpenIdConfiguration) -> Unit,
) {
    authentication {
        register(
            OpenIdConnectOauthAuthenticationProvider(
                application = this@openIdOauth2,
                name = config.name,
                config = OpenIdConnectOauthAuthenticationProvider.Config(config.name),
                httpClient = client,
                redirect = redirect,
                scopes = config.scopes.toSet(),
                clientId = clientId,
                clientSecret = clientSecret,
                configuration = configuration,
            )
        )
    }

    routing {
        authenticate(config.name) {
            get(login) {}
            get(redirect) {
                val oauth = call.principal<OAuthAccessTokenResponse.OAuth2>() ?: return@get handleFailure()
                val idToken = oauth.extraParameters["id_token"] ?: return@get handleFailure()
                val principal = OpenIdConnectPrincipal(
                    idToken = idToken,
                    refreshToken = oauth.refreshToken,
                    userInfo = JWT.decode(idToken).extractUserInfo()
                )
                handleCallback(principal)
            }
            get(refresh) {
                val refreshToken =
                    (call.sessions.get(config.sessionName) as? OpenIdConnectPrincipal)?.refreshToken
                        ?: return@get call.respond(Unauthorized, "No refresh token found")

                val principal = refreshOpenIdToken(
                    configuration = configuration.await(),
                    clientId = clientId,
                    clientSecret = clientSecret,
                    refreshToken = refreshToken,
                    httpClient = client,
                )

                call.sessions.set(principal)
            }
        }
        get(logout) {
            val configuration = configuration.await()
            val endSessionEndpoint = configuration.endSessionEndpoint
            val principal = (call.sessions.get(config.sessionName) as? OpenIdConnectPrincipal)
                ?: return@get call.respond(Unauthorized)
            call.sessions.clear(config.sessionName)

            if (endSessionEndpoint != null) {
                application.log.trace("Logging out with end session endpoint: $endSessionEndpoint")
                handleLogoutWithEndSession(principal, configuration, endSessionEndpoint)
            } else {
                application.log.trace("No end session endpoint found, redirecting to post logout redirect uri: $postLogoutRedirect")
                handleLogoutFallback(principal, configuration)
            }
        }
    }
}

/**
 * [AuthenticationProvider] that delegates to [JWTAuthenticationProvider] but awaits the auto-discovery in [onAuthenticate].
 * Combines JWK & Session authentication in a single provider.
 */
private class OpenIdConnectJwkAndSessionAuthenticationProvider(
    application: Application,
    name: String,
    config: Config,
    private val configuration: Deferred<OpenIdConfiguration>,
    private val checkSession: Boolean,
    private val jwkConfig: OpenIdConnect.Config.JwkConfig,
) : AuthenticationProvider(config) {
    private val provider = application.async {
        val config = configuration.await()

        application.log.debug("Using issuer ${config.issuer} for JWK ${jwkConfig.name} with sessions($checkSession)")

        @Suppress("unused", "invisible_reference")
        JWTAuthenticationProvider(JWTAuthenticationProvider.Config(name).apply {
            verifier(
                JwkProviderBuilder(URI(config.jwksUri).toURL()).apply(jwkConfig.jwkBuilder).build(),
                config.issuer,
                jwkConfig.verify
            )
            authHeader { call ->
                if (checkSession) {
                    call.request.headers[Authorization]?.let { parseAuthorizationHeader(it) }
                        ?: call.sessions.get<OpenIdConnectPrincipal>()
                            ?.idToken
                            ?.let { HttpAuthHeader.Single("Bearer", it) }
                } else {
                    call.request.headers[Authorization]?.let { parseAuthorizationHeader(it) }
                }

            }
            this.validate(jwkConfig.validate)
        })
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        provider.await().onAuthenticate(context)
    }

    class Config(name: String?) : AuthenticationProvider.Config(name)
}

private suspend fun refreshOpenIdToken(
    configuration: OpenIdConfiguration,
    clientId: String,
    clientSecret: String,
    refreshToken: String,
    httpClient: HttpClient
): OpenIdConnectPrincipal {
    val response = httpClient.submitForm(
        url = configuration.tokenEndpoint,
        formParameters = Parameters.build {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
            append("client_id", clientId)
            append("client_secret", clientSecret)
        }
    ).body<TokenRefreshResponse>()

    val idToken =
        response.id_token ?: throw IllegalStateException("id_token is missing from the refresh token response")

    return OpenIdConnectPrincipal(
        idToken = idToken,
        refreshToken = response.refresh_token
            ?: refreshToken, // Use the new refresh token if provided, otherwise keep the old one,
        userInfo = JWT.decode(idToken).extractUserInfo()
    )
}