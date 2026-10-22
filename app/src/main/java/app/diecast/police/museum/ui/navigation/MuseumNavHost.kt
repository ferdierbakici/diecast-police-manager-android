package app.diecast.police.museum.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.diecast.police.museum.data.model.TagFilter
import app.diecast.police.museum.ui.appViewModel
import app.diecast.police.museum.ui.auth.LoginScreen
import app.diecast.police.museum.ui.collection.CollectionScreen
import app.diecast.police.museum.ui.detail.VehicleDetailScreen
import app.diecast.police.museum.ui.home.HomeScreen
import app.diecast.police.museum.ui.search.SearchScreen
import app.diecast.police.museum.ui.session.SessionViewModel

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val COLLECTION = "collection"
    const val LOGIN = "login"
    const val DETAIL = "vehicle/{vehicleId}"

    fun detail(vehicleId: Int) = "vehicle/$vehicleId"
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val TABS = listOf(
    TabItem(Routes.HOME, "Home", Icons.Outlined.Home),
    TabItem(Routes.SEARCH, "Search", Icons.Outlined.Search),
    TabItem(Routes.COLLECTION, "Collection", Icons.Outlined.Inventory2),
    TabItem(Routes.LOGIN, "Account", Icons.Outlined.AccountCircle)
)

@Composable
fun MuseumApp() {
    val navController = rememberNavController()
    // The session view model is kept alive here (app wide).
    val sessionViewModel: SessionViewModel = appViewModel { SessionViewModel(it.authRepository) }
    @Suppress("UNUSED_EXPRESSION")
    sessionViewModel

    // A filter criterion that was requested by tapping a tag on the detail screen.
    // It is handed over to the search screen and consumed there.
    var pendingTagFilter by remember { mutableStateOf<TagFilter?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TABS.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            composable(Routes.HOME) {
                HomeScreen(onVehicleClick = { navController.navigate(Routes.detail(it)) })
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onVehicleClick = { navController.navigate(Routes.detail(it)) },
                    pendingFilter = pendingTagFilter,
                    onPendingFilterConsumed = { pendingTagFilter = null }
                )
            }

            composable(Routes.COLLECTION) {
                CollectionScreen(
                    onVehicleClick = { navController.navigate(Routes.detail(it)) },
                    onLoginClick = { navController.navigate(Routes.LOGIN) }
                )
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    onSignedIn = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
            ) { entry ->
                val vehicleId = entry.arguments?.getInt("vehicleId") ?: return@composable
                VehicleDetailScreen(
                    vehicleId = vehicleId,
                    onBack = { navController.popBackStack() },
                    onLoginRequired = { navController.navigate(Routes.LOGIN) },
                    onTagClick = { tag ->
                        // The tag opens the search screen with the matching filter.
                        pendingTagFilter = tag
                        navController.navigateToSearchWithFilter()
                    }
                )
            }
        }
    }

    DeepLinkEffect(navController)
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Opens the search screen with a filter that was requested by tapping a tag.
 *
 * Everything above the start destination is removed (the detail screen), the same way
 * the bottom navigation does it. `saveState` is deliberately NOT used here: a saved
 * detail screen would be restored on top of the search screen.
 */
private fun NavHostController.navigateToSearchWithFilter() {
    navigate(Routes.SEARCH) {
        popUpTo(graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
    }
}

/** Deep link: diecastpolice://vehicle/123 -> the detail screen. */
@Composable
private fun DeepLinkEffect(navController: NavHostController) {
    val context = LocalContext.current
    var handled by remember { mutableStateOf<Int?>(null) }

    val data = (context as? android.app.Activity)?.intent?.data
    val vehicleId = remember(data) {
        if (data?.scheme == "diecastpolice" && data.host == "vehicle") {
            data.lastPathSegment?.toIntOrNull()
        } else {
            null
        }
    }

    LaunchedEffect(vehicleId) {
        val id = vehicleId ?: return@LaunchedEffect
        if (handled == id) return@LaunchedEffect
        handled = id
        navController.navigate(Routes.detail(id))
    }
}
