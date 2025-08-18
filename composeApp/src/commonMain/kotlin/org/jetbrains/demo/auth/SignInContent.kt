package org.jetbrains.demo.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.demo.ui.Logger
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignInContent(
    authViewModel: AuthViewModel = koinViewModel(),
    onSignInClick: () -> Unit
) = Column(
    modifier = Modifier
        .safeContentPadding()
        .fillMaxSize()
        .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
    val scope = rememberCoroutineScope()
    val state by authViewModel.state.collectAsStateWithLifecycle()

    if (state == AuthViewModel.AuthState.SignedIn) {
        Logger.app.d("SignInContent: Signed in, redirecting to main screen")
        onSignInClick()
    } else {
        Logger.app.d("SignInContent: Displaying sign-in UI $state")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Google OAuth Demo",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sign in with your Google account to continue",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(24.dp))

                val errorOrNull = state as? AuthViewModel.AuthState.Error
                if (errorOrNull != null) {
                    val message = errorOrNull.message.ifBlank { "Unknown error" }
                    Logger.app.d("SignInContent: Displaying error: $message")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(onClick = {
                                Logger.app.d("SignInContent: Error dismiss button clicked")
                                authViewModel.clearError()
                            }) {
                                Text("Dismiss")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                val isLoading = state is AuthViewModel.AuthState.Loading
                Button(
                    onClick = {
                        // Trigger Google Sign-In flow
                        Logger.app.d("SignInContent: Sign in button clicked")
                        scope.launch {
                            authViewModel.signIn()
                            onSignInClick()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        Logger.app.d("SignInContent: Showing loading indicator")
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLoading) "Signing in..." else "Sign in with Google")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This app uses Google Sign-In for authentication.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
