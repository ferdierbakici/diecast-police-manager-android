package app.diecast.police.museum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import app.diecast.police.museum.core.OAuthRedirect
import app.diecast.police.museum.data.repository.AuthResult
import app.diecast.police.museum.ui.LocalAppContainer
import app.diecast.police.museum.ui.navigation.MuseumApp
import app.diecast.police.museum.ui.theme.DiecastMuseumTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MuseumApp).container

        handleOAuthRedirect(intent, container)

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                DiecastMuseumTheme {
                    MuseumApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = (application as MuseumApp).container
        handleOAuthRedirect(intent, container)
    }

    /**
     * Google OAuth return: diecastpolice://auth-callback
     *
     * In the PKCE flow the return arrives as `?code=...` and the tokens are exchanged.
     * The old (implicit) flow delivers the tokens in the `#access_token=...` fragment;
     * that path is still supported for backward compatibility.
     */
    private fun handleOAuthRedirect(intent: Intent?, container: AppContainer) {
        val data = intent?.data ?: return
        val redirect = OAuthRedirect.parse(
            scheme = data.scheme,
            host = data.host,
            query = data.query,
            fragment = data.fragment
        ) ?: return

        // --- PKCE: the server sends `?code=...` -------------------------------
        if (redirect.hasCode) {
            lifecycleScope.launch {
                when (val result = container.authRepository.exchangeCodeForSession(redirect.code!!)) {
                    is AuthResult.Success -> Unit
                    is AuthResult.NeedsEmailConfirmation ->
                        container.authRepository.setAuthMessage("Your account needs to be verified by email.")
                    is AuthResult.Failure ->
                        container.authRepository.setAuthMessage(result.message)
                }
            }
            return
        }

        // --- Error return: ?error=...&error_description=... ---------------------
        if (redirect.hasError) {
            container.authRepository.setAuthMessage(redirect.errorMessage())
            return
        }

        // --- Implicit (old flow): the tokens are in the fragment ---------------------
        if (!redirect.hasTokens) return

        lifecycleScope.launch {
            container.authRepository.signInWithTokens(
                accessToken = redirect.accessToken!!,
                refreshToken = redirect.refreshToken!!,
                expiresInSeconds = redirect.expiresIn ?: 3600L
            )
        }
    }
}
