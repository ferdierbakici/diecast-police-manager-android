package app.diecast.police.museum

import android.app.Application
import android.content.Context
import app.diecast.police.museum.core.Network
import app.diecast.police.museum.data.local.SessionStore
import app.diecast.police.museum.data.remote.RestClient
import app.diecast.police.museum.data.repository.AuthRepository
import app.diecast.police.museum.data.repository.MuseumRepository

class MuseumApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manuel DI (ServiceLocator). Hilt/KSP kullanilmaz - derleme suresi ve
 * bagimlilik riski minimumda tutulur.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val rest: RestClient by lazy { RestClient(Network.client) }

    val sessionStore: SessionStore by lazy { SessionStore(appContext) }

    val museumRepository: MuseumRepository by lazy { MuseumRepository(rest) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(appContext, rest, sessionStore)
    }
}
