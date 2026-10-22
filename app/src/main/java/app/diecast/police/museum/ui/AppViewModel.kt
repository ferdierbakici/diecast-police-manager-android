package app.diecast.police.museum.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.diecast.police.museum.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not found - MuseumApp.onCreate must run first")
}

/** Creates a ViewModel from the container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer) -> VM
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        factory = viewModelFactory {
            initializer { create(container) }
        }
    )
}
