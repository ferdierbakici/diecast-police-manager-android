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
     * Google OAuth donusu: diecastpolice://auth-callback
     *
     * PKCE akisinda donus `?code=...` ile gelir ve token degisimi yapilir.
     * Eski (implicit) akisde token'lar `#access_token=...` fragment'inda gelir;
     * geriye donuk uyum icin o yol da desteklenir.
     */
    private fun handleOAuthRedirect(intent: Intent?, container: AppContainer) {
        val data = intent?.data ?: return
        val redirect = OAuthRedirect.parse(
            scheme = data.scheme,
            host = data.host,
            query = data.query,
            fragment = data.fragment
        ) ?: return

        // --- PKCE: sunucu `?code=...` gonderir -------------------------------
        if (redirect.hasCode) {
            lifecycleScope.launch {
                when (val result = container.authRepository.exchangeCodeForSession(redirect.code!!)) {
                    is AuthResult.Success -> Unit
                    is AuthResult.NeedsEmailConfirmation ->
                        container.authRepository.setAuthMessage("Hesabiniz e-posta ile dogrulanmali.")
                    is AuthResult.Failure ->
                        container.authRepository.setAuthMessage(result.message)
                }
            }
            return
        }

        // --- Hata donusu: ?error=...&error_description=... ---------------------
        if (redirect.hasError) {
            container.authRepository.setAuthMessage(redirect.errorMessage())
            return
        }

        // --- Implicit (eski akis): token'lar fragment'ta ---------------------
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
