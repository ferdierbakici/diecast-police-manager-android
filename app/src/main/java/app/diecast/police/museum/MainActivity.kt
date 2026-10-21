package app.diecast.police.museum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
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
     * Google OAuth donusu: diecastpolice://auth-callback#access_token=...&refresh_token=...
     * Token'lar URL fragment'inda gelir.
     */
    private fun handleOAuthRedirect(intent: Intent?, container: AppContainer) {
        val data = intent?.data ?: return
        if (data.scheme != "diecastpolice" || data.host != "auth-callback") return

        val fragment = data.fragment ?: return
        val params = fragment.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()

        val accessToken = params["access_token"] ?: return
        val refreshToken = params["refresh_token"] ?: return
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L

        lifecycleScope.launch {
            container.authRepository.signInWithTokens(accessToken, refreshToken, expiresIn)
        }
    }
}
