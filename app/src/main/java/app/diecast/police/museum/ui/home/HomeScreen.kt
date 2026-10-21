package app.diecast.police.museum.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.diecast.police.museum.ui.appViewModel
import app.diecast.police.museum.ui.components.ErrorState
import app.diecast.police.museum.ui.components.LoadingState
import app.diecast.police.museum.ui.components.VehicleCard
import app.diecast.police.museum.ui.components.VehicleRowCard
import app.diecast.police.museum.ui.theme.MuseumGold
import app.diecast.police.museum.ui.theme.MuseumInkSoft

@Composable
fun HomeScreen(
    onVehicleClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: HomeViewModel = appViewModel { HomeViewModel(it.museumRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading && state.lastAdded.isEmpty() ->
                LoadingState(label = "Muzeye yukleniyor...")

            state.error != null && state.lastAdded.isEmpty() -> ErrorState(
                message = state.error ?: "Veriler alinamadi.",
                onRetry = viewModel::refresh
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(2) }) {
                    HeroHeader(
                        totalAssets = state.stats.totalAssets,
                        countries = state.stats.countries,
                        brands = state.stats.brands
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    SectionTitle("Koleksiyona son katilanlar")
                }

                item(span = { GridItemSpan(2) }) {
                    if (state.recentlyInCollection.isEmpty()) {
                        Text(
                            text = "Koleksiyona yeni katilan model bulunmuyor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MuseumInkSoft,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.recentlyInCollection, key = { it.id }) { vehicle ->
                                VehicleRowCard(
                                    vehicle = vehicle,
                                    onClick = { onVehicleClick(it.id) }
                                )
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    SectionTitle("Son eklenen modeller")
                }

                items(state.lastAdded, key = { it.id }) { vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        onClick = { onVehicleClick(it.id) }
                    )
                }

                if (state.error != null) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(totalAssets: Int, countries: Int, brands: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Diecast Police Museum",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Dunya genelinde model araclari koleksiyonu",
            style = MaterialTheme.typography.bodySmall,
            color = MuseumInkSoft,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("TOTAL ASSETS", totalAssets.toString(), Modifier.weight(1f))
            StatCard("COUNTRIES", countries.toString(), Modifier.weight(1f))
            StatCard("BRANDS", brands.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MuseumGold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MuseumInkSoft,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
