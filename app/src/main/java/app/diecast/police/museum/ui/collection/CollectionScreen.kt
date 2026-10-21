package app.diecast.police.museum.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.diecast.police.museum.ui.appViewModel
import app.diecast.police.museum.ui.components.EmptyState
import app.diecast.police.museum.ui.components.ErrorState
import app.diecast.police.museum.ui.components.LoadingState
import app.diecast.police.museum.ui.components.VehicleListItem
import app.diecast.police.museum.ui.theme.MuseumInkSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onVehicleClick: (Int) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = appViewModel { container ->
        CollectionViewModel(container.museumRepository, container.authRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = if (state.tab == CollectionViewModel.TAB_WISHLIST) 1 else 0) {
            Tab(
                selected = state.tab == CollectionViewModel.TAB_COLLECTION,
                onClick = { viewModel.switchTab(CollectionViewModel.TAB_COLLECTION) },
                text = {
                    Text("Koleksiyonum (${state.collection.size})")
                }
            )
            Tab(
                selected = state.tab == CollectionViewModel.TAB_WISHLIST,
                onClick = { viewModel.switchTab(CollectionViewModel.TAB_WISHLIST) },
                text = {
                    Text("Wishlist (${state.wishlist.size})")
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                !state.signedIn && !state.loading -> SignedOutPrompt(onLoginClick)

                state.loading -> LoadingState(label = "Listeler yukleniyor...")

                state.error != null -> ErrorState(
                    message = state.error ?: "Listeler alinamadi.",
                    onRetry = viewModel::load
                )

                state.visible.isEmpty() -> EmptyState(
                    title = if (state.tab == CollectionViewModel.TAB_WISHLIST) {
                        "Wishlist bos"
                    } else {
                        "Koleksiyonunuz bos"
                    },
                    subtitle = "Arac detay sayfasindan koleksiyona ekleyebilirsiniz."
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.visible, key = { it.id }) { vehicle ->
                        VehicleListItem(
                            vehicle = vehicle,
                            onClick = { onVehicleClick(vehicle.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignedOutPrompt(onLoginClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Koleksiyonunuzu gormek icin giris yapin",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Kendi koleksiyonunuzu ve wishlist'inizi yonetmek icin hesabiniza giris yapin.",
                style = MaterialTheme.typography.bodySmall,
                color = MuseumInkSoft,
                textAlign = TextAlign.Center
            )
            Button(onClick = onLoginClick) { Text("Giris yap") }
        }
    }
}
