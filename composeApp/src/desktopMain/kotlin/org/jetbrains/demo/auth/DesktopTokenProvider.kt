package org.jetbrains.demo.auth

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.engine.cio.CIO as CIOClient
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.install
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.oauth
import io.ktor.server.auth.openid.OpenIdConnect
import io.ktor.server.auth.openid.OpenIdConnectPrincipal
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.*
import org.jetbrains.demo.config.*
import java.awt.*
import java.net.*

private const val KEY_ID_TOKEN = "id_token"

/**
 * Desktop implementation of TokenProvider using Google OAuth2 flow with local HTTP server.
 * This implementation:
 * 1. Starts a local CIO server to handle OAuth callbacks
 * 2. Opens browser to Google OAuth URL
 * 3. Exchanges authorization code for ID token using Ktor HTTP client
 */
class DesktopTokenProvider(
    private val config: DesktopConfig,
    private val preferences: EncryptedPreferences,
    base: Logger,
) : TokenProvider, AutoCloseable {
    private val logger = base.withTag("DesktopTokenProvider")
    private val httpClient = HttpClient(CIOClient) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        followRedirects = false
    }

    override fun getToken(): String? {
        val token = preferences.get(KEY_ID_TOKEN, null)
        logger.d("TokenProvider: Retrieved token, exists: ${token != null}")
        return token
    }

    override fun clearToken() {
        logger.d("Clearing token")
        preferences.remove(KEY_ID_TOKEN)
    }

    override suspend fun refreshToken(): String? = withContext(Dispatchers.IO) {
        logger.d("Refreshing token")
        HttpClient(CIOClient) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }.use { httpClient ->
            val callback = CompletableDeferred<OAuthAccessTokenResponse.OAuth2>()
            val server = embeddedServer(CIO) {
                val port = async { engine.resolvedConnectors().first().port }
                authentication {
                    oauth("oauth") {
                        @OptIn(ExperimentalCoroutinesApi::class)
                        urlProvider = { "http://localhost:${port.getCompleted()}/callback" }
                        providerLookup = {
                            OAuthServerSettings.OAuth2ServerSettings(
                                name = "google",
                                authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                                accessTokenUrl = "https://oauth2.googleapis.com/token",
                                requestMethod = HttpMethod.Post,
                                clientId = config.clientId,
                                clientSecret = config.clientSecret,
                                defaultScopes = listOf("email"),
                            )
                        }
                        client = httpClient
                    }
                }
                routing {
                    authenticate("oauth") {
                        get("/login") {}
                        get("/callback") {
                            val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
                            if (principal == null) {
                                callback.completeExceptionally(IllegalStateException("No OAuth2 principal"))
                                call.respondText(createErrorResponseHtml(), ContentType.Text.Html)
                            } else {
                                callback.complete(principal)
                                call.respondText(createSuccessResponseHtml(), ContentType.Text.Html)
                            }
                        }
                    }
                }
            }

            try {
                server.startSuspend(wait = false)
                logger.d("Refreshing token. Server started.")
                val port = server.engine.resolvedConnectors().first().port
                val response = httpClient.config {
                    followRedirects = false
                }.get("http://localhost:$port/login")
                val url = requireNotNull(response.headers["Location"]) {
                    "Expected Location header and 302 Found, but found ${response.status}."
                }
                logger.d("Refreshing token. Opening browser.")
                Desktop.getDesktop().browse(URI(url))
                val oauth = callback.await()
                val idToken = oauth.extraParameters[KEY_ID_TOKEN]
                if (idToken != null) preferences.put(KEY_ID_TOKEN, idToken)
                logger.d("Received, and stored token.")
                oauth.extraParameters[KEY_ID_TOKEN]
            } finally {
                withContext(NonCancellable) {
                    server.stopSuspend(1000, 5000)
                }
            }
        }
    }

    override fun close() {
        httpClient.close()
    }

    private fun createSuccessResponseHtml(): String = createHTML().html {
        head {
            title("Authentication Successful")
            style {
                unsafe {
                    +createSuccessPageStyles()
                }
            }
        }
        body {
            div("container") {
                div("success-icon") { +"✅" }
                h1 { +"Authentication Successful!" }
                p { +"Great! You have been successfully authenticated." }
                div("close-instruction") {
                    +"You can now close this browser window and return to the application."
                }
            }
        }
    }

    private fun createErrorResponseHtml(): String = createHTML().html {
        head {
            title("Authentication Error")
            style {
                unsafe {
                    +createErrorPageStyles()
                }
            }
        }
        body {
            div("container") {
                div("error-icon") { +"❌" }
                h1 { +"Authentication Error" }
                p { +"Something went wrong during the authentication process." }
                div("retry-instruction") {
                    +"Please close this browser window and try again."
                }
            }
        }
    }
}

private fun createSuccessPageStyles(): String =
    """
            :root {
                --lavender-pink: #ffb8d1;
                --orchid-pink: #e4b4c2;
                --thistle: #e7cee3;
                --ghost-white: #e0e1e9;
                --light-cyan: #ddfdfe;
                --text: #262836; /* ghost_white[100] */
            }
            
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                background: linear-gradient(135deg, var(--ghost-white) 0%, var(--thistle) 50%, var(--lavender-pink) 100%);
                min-height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
                color: var(--text);
                position: relative;
            }
            
            body::before {
                content: '';
                position: absolute;
                inset: 0;
                background:
                    radial-gradient(circle at 20% 80%, rgba(221, 253, 254, 0.35) 0%, transparent 50%), /* light-cyan */
                    radial-gradient(circle at 80% 20%, rgba(228, 180, 194, 0.35) 0%, transparent 50%), /* orchid-pink */
                    radial-gradient(circle at 40% 40%, rgba(231, 206, 227, 0.25) 0%, transparent 50%); /* thistle */
            }
            
            .container {
                background: rgba(221, 253, 254, 0.8); /* light-cyan */
                backdrop-filter: blur(12px);
                border-radius: 20px;
                padding: 3rem 2.5rem;
                text-align: center;
                border: 1px solid #abaec4; /* ghost_white[400] */
                box-shadow:
                    0 10px 25px rgba(38, 40, 54, 0.10),
                    inset 0 1px 0 rgba(255, 255, 255, 0.6);
                max-width: 520px;
                width: 90%;
                position: relative;
                z-index: 1;
            }
            
            .success-icon {
                font-size: 5rem;
                margin-bottom: 1.5rem;
                display: inline-block;
                filter: drop-shadow(0 0 18px rgba(255, 96, 152, 0.45)); /* lavender_pink[400] */
            }
            
            h1 {
                font-size: 2.5rem;
                margin-bottom: 1rem;
                font-weight: 800;
                background: linear-gradient(135deg, #af003d 0%, #ff6098 100%); /* lavender_pink 200->400 */
                -webkit-background-clip: text;
                -webkit-text-fill-color: transparent;
                background-clip: text;
                text-shadow: 0 2px 4px rgba(13, 12, 18, 0.06);
                line-height: 1.2;
            }
            
            p {
                font-size: 1.1rem;
                line-height: 1.7;
                margin-bottom: 2rem;
                color: #4c506b; /* ghost_white[200] */
            }
            
            .close-instruction {
                background: rgba(224, 225, 233, 0.45); /* ghost_white */
                border-radius: 14px;
                padding: 1.25rem;
                font-size: 1rem;
                border: 1px solid #abaec4; /* ghost_white[400] */
                color: var(--text);
                transition: background-color 0.2s ease;
            }
            
            .close-instruction:hover {
                background: rgba(224, 225, 233, 0.6);
            }
            
            @media (prefers-color-scheme: dark) {
                :root {
                    --text: #f9f9fb; /* ghost_white[900] */
                }
                body {
                    background: linear-gradient(135deg, #262836 0%, #4c506b 50%, #763a6c 100%);
                    color: var(--text);
                }
                .container {
                    background: rgba(38, 40, 54, 0.55);
                    border-color: #767a9e; /* ghost_white[300] */
                }
                p { color: #e7e8ee; }
                .close-instruction {
                    background: rgba(76, 80, 107, 0.45);
                    border-color: #767a9e;
                    color: var(--text);
                }
            }
        """.trimIndent()

private fun createErrorPageStyles(): String =
    """
            :root {
                --lavender-pink: #ffb8d1;
                --orchid-pink: #e4b4c2;
                --thistle: #e7cee3;
                --ghost-white: #e0e1e9;
                --light-cyan: #ddfdfe;
                --text: #262836; /* ghost_white[100] */
            }
            
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                background: linear-gradient(135deg, var(--ghost-white) 0%, var(--thistle) 50%, var(--orchid-pink) 100%);
                min-height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
                color: var(--text);
                position: relative;
            }
            
            body::before {
                content: '';
                position: absolute;
                inset: 0;
                background:
                    radial-gradient(circle at 20% 80%, rgba(221, 253, 254, 0.35) 0%, transparent 50%), /* light-cyan */
                    radial-gradient(circle at 80% 20%, rgba(255, 184, 209, 0.35) 0%, transparent 50%), /* lavender-pink */
                    radial-gradient(circle at 40% 40%, rgba(231, 206, 227, 0.25) 0%, transparent 50%); /* thistle */
            }
            
            .container {
                background: rgba(221, 253, 254, 0.8); /* light-cyan */
                backdrop-filter: blur(12px);
                border-radius: 20px;
                padding: 3rem 2.5rem;
                text-align: center;
                border: 1px solid #abaec4; /* ghost_white[400] */
                box-shadow:
                    0 10px 25px rgba(38, 40, 54, 0.10),
                    inset 0 1px 0 rgba(255, 255, 255, 0.6);
                max-width: 520px;
                width: 90%;
                position: relative;
                z-index: 1;
            }
            
            .error-icon {
                font-size: 5rem;
                margin-bottom: 1.5rem;
                display: inline-block;
                filter: drop-shadow(0 0 18px rgba(175, 0, 61, 0.45)); /* lavender_pink[200] */
            }
            
            h1 {
                font-size: 2.5rem;
                margin-bottom: 1rem;
                font-weight: 800;
                background: linear-gradient(135deg, #af003d 0%, #ff6098 100%); /* lavender_pink 200->400 */
                -webkit-background-clip: text;
                -webkit-text-fill-color: transparent;
                background-clip: text;
                text-shadow: 0 2px 4px rgba(13, 12, 18, 0.06);
                line-height: 1.2;
            }
            
            .error-message {
                background: rgba(224, 225, 233, 0.45); /* ghost_white */
                border-radius: 14px;
                padding: 1.25rem;
                margin: 1.5rem 0 2rem 0;
                font-size: 1rem;
                border: 1px solid #abaec4; /* ghost_white[400] */
                word-break: break-word;
                color: var(--text);
                font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, 'Courier New', monospace;
                text-align: left;
                box-shadow: inset 0 2px 4px rgba(13, 12, 18, 0.06);
            }
            
            p {
                font-size: 1.1rem;
                line-height: 1.7;
                margin-bottom: 2rem;
                color: #4c506b; /* ghost_white[200] */
            }
            
            .retry-instruction {
                background: rgba(224, 225, 233, 0.45); /* ghost_white */
                border-radius: 14px;
                padding: 1.25rem;
                font-size: 1rem;
                border: 1px solid #abaec4; /* ghost_white[400] */
                color: var(--text);
                transition: background-color 0.2s ease;
            }
            
            .retry-instruction:hover {
                background: rgba(224, 225, 233, 0.6);
            }
            
            @media (prefers-color-scheme: dark) {
                :root { --text: #f9f9fb; }
                body { background: linear-gradient(135deg, #262836 0%, #4c506b 50%, #763a6c 100%); }
                .container { background: rgba(38, 40, 54, 0.55); border-color: #767a9e; }
                p { color: #e7e8ee; }
                .error-message { background: rgba(76, 80, 107, 0.45); border-color: #767a9e; color: var(--text); }
                .retry-instruction { background: rgba(76, 80, 107, 0.45); border-color: #767a9e; color: var(--text); }
            }
        """.trimIndent()