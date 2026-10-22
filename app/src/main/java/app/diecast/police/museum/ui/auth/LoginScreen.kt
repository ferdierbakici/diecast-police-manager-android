package app.diecast.police.museum.ui.auth

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.diecast.police.museum.core.Config
import app.diecast.police.museum.ui.appViewModel
import app.diecast.police.museum.ui.theme.MuseumInkSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: LoginViewModel = appViewModel { LoginViewModel(it.authRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val authMessage by viewModel.authMessage.collectAsStateWithLifecycle()
    val googleUrl by viewModel.googleUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var registerMode by remember { mutableStateOf(false) }

    // Was there a session when the screen opened? (the OAuth return must trigger it)
    //
    // NOTE: The presence of a session cannot be used to detect the "sign-in event":
    // the session is persisted, so it already exists on every screen open.
    // When a new session is created the "signedInEvents" event is published; we watch that.
    LaunchedEffect(Unit) {
        viewModel.signedInEvents.collect { onSignedIn() }
    }

    // Error message arriving through the deep link
    LaunchedEffect(authMessage) {
        val message = authMessage ?: return@LaunchedEffect
        viewModel.showAuthMessage(message)
    }

    // Google OAuth: once the URL is generated the browser (CustomTab) is opened.
    LaunchedEffect(googleUrl) {
        val url = googleUrl ?: return@LaunchedEffect
        val intent = CustomTabsIntent.Builder().build()
        runCatching { intent.launchUrl(context, android.net.Uri.parse(url)) }
            .onFailure { viewModel.showAuthMessage("Could not open the browser. Google sign-in failed.") }
        viewModel.consumeGoogleUrl()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val activeSession = session
        if (activeSession != null) {
            // --- Signed in: account info -----------------------------
            Text(
                text = "My Account",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = activeSession.email ?: activeSession.userId,
                style = MaterialTheme.typography.bodyLarge
            )
            activeSession.displayName?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MuseumInkSoft)
            }
            OutlinedButton(
                onClick = { viewModel.signOut() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Sign out")
            }
            return@Column
        }

        Text(
            text = if (registerMode) "Create account" else "Sign in",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Sign in to manage your collection and wishlist.",
            style = MaterialTheme.typography.bodySmall,
            color = MuseumInkSoft,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (registerMode) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Full name") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        val errorText = state.error
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        val infoText = state.info
        if (infoText != null) {
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = {
                viewModel.clearMessages()
                if (registerMode) viewModel.signUp(email, password, displayName)
                else viewModel.signIn(email, password)
            },
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (registerMode) "Sign up" else "Sign in")
            }
        }

        OutlinedButton(
            onClick = { viewModel.startGoogleLogin() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue with Google")
        }

        Text(
            text = "To sign in with Google, add this address to the\n" +
                "\"Redirect URLs\" list in the Supabase dashboard:\n${Config.REDIRECT_URI}",
            style = MaterialTheme.typography.labelSmall,
            color = MuseumInkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = {
                viewModel.clearMessages()
                registerMode = !registerMode
            }) {
                Text(if (registerMode) "Already have an account" else "Sign up")
            }
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
