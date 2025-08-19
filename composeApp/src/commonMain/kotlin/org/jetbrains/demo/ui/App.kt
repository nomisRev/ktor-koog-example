package org.jetbrains.demo.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.auth.*
import org.jetbrains.demo.chat.ChatScreen
import org.jetbrains.demo.agent.AgentPlannerViewModel
import org.jetbrains.demo.agent.ui.AgentCard
import org.jetbrains.demo.journey.EmptyJourneyForm
import org.jetbrains.demo.journey.JourneySpannerRoute
import org.koin.compose.viewmodel.koinViewModel

interface AuthState {
    fun hasToken(): Boolean
    fun clearToken()
}

fun AuthSession(auth: AuthViewModel): AuthState = object : AuthState {
    override fun hasToken(): Boolean = auth.state.value is AuthViewModel.AuthState.SignedIn
    override fun clearToken() = auth.signOut()
}

@Composable
fun App(
    onNavHostReady: suspend (NavController) -> Unit = {},
    authState: AuthState,
) {
    Logger.app.d("App: Composable started")
    val start = if (authState.hasToken()) Screen.Form else Screen.LogIn
    val planner = koinViewModel<AgentPlannerViewModel>()

    AppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            Logger.app.d("App: Creating NavHost with $start")
            NavHost(navController, start) {
                Logger.app.d("NavHost building")
                composable<Screen.Chat> {
                    Logger.app.d("NavHost: Screen.Chat")
                    ChatScreen {
                        authState.clearToken()
                        navController.navigate(Screen.LogIn)
                    }
                }

                composable<Screen.LogIn> {
                    Logger.app.d("NavHost: Screen.LogIn")
                    SignInContent { navController.navigate(Screen.Form) }
                }

                composable<Screen.Planner> {
                    Logger.app.d("NavHost: Screen.Planner")
                    AgentCard(planner)
                }

                composable<Screen.Form> {
                    Logger.app.d("NavHost: Screen.Form")

                    JourneySpannerRoute { form ->
                        Logger.app.d("NavHost: Navigating to Screen.Planner & started $form")
                        planner.start(form)
                        navController.navigate(Screen.Planner)
                    }
                }
            }

            LaunchedEffect(navController) {
                Logger.app.d("App: onNavHostReady")
                onNavHostReady(navController)
                Logger.app.d("App: onNavHostReady - Done")
            }
        }
    }
}

@Serializable
object Screen {
    @Serializable
    @SerialName("chat")
    data object Chat

    @Serializable
    @SerialName("login")
    data object LogIn

    @Serializable
    @SerialName("form")
    data object Form

    @Serializable
    @SerialName("planner")
    data object Planner
}
