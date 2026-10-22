@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.diecast.police.museum.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.diecast.police.museum.data.model.TagFilter
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.ui.appViewModel
import app.diecast.police.museum.ui.components.ErrorState
import app.diecast.police.museum.ui.components.LoadingState
import app.diecast.police.museum.ui.components.StatusBadge
import app.diecast.police.museum.ui.components.VehicleImage
import app.diecast.police.museum.ui.theme.MuseumBorder
import app.diecast.police.museum.ui.theme.MuseumGold
import app.diecast.police.museum.ui.theme.MuseumInk
import app.diecast.police.museum.ui.theme.MuseumInkSoft
import app.diecast.police.museum.ui.theme.StatusCollection
import app.diecast.police.museum.domain.Timestamps

/**
 * The mobile detail screen.
 *
 * The mobile problems of the web app's `VehicleDetailModal` are fixed here:
 *  - Instead of a fixed 500-600px image, at most 32% of the screen
 *  - Instead of a 2-column grid, a single column "Row" list
 *  - Instead of 9px labels, at least 13sp labels / 15sp values
 *  - Instead of p-10 inner padding, 16px
 *  - The whole screen scrolls (not only the right panel)
 *  - A fixed action bar at the bottom
 */
@Composable
fun VehicleDetailScreen(
    vehicleId: Int,
    onBack: () -> Unit,
    onLoginRequired: () -> Unit,
    /** A tag under the image was tapped: the matching filter is applied on the search screen. */
    onTagClick: (TagFilter) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel = appViewModel { container ->
        app.diecast.police.museum.ui.detail.VehicleDetailViewModel(
            container.museumRepository,
            container.authRepository
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(vehicleId) { viewModel.load(vehicleId) }

    LaunchedEffect(state.needsAuth) {
        if (state.needsAuth) {
            viewModel.consumeNeedsAuth()
            onLoginRequired()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (state.vehicle != null) {
                ActionBar(
                    inCollection = state.inCollection,
                    inWishlist = state.inWishlist,
                    busy = state.busy,
                    onCollection = { viewModel.toggle(VehicleDetailViewModel.STATUS_COLLECTION) },
                    onWishlist = { viewModel.toggle(VehicleDetailViewModel.STATUS_WISHLIST) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingState(
                    modifier = Modifier.padding(innerPadding),
                    label = "Loading vehicle details..."
                )

                state.error != null -> ErrorState(
                    message = state.error ?: "Could not load vehicle details.",
                    modifier = Modifier.padding(innerPadding),
                    onRetry = { viewModel.load(vehicleId) }
                )

                state.vehicle != null -> DetailContent(
                    vehicle = state.vehicle!!,
                    collectionCount = state.collectionCount,
                    wishlistCount = state.wishlistCount,
                    onBack = onBack,
                    onTagClick = onTagClick,
                    contentPadding = innerPadding
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    vehicle: Vehicle,
    collectionCount: Int,
    wishlistCount: Int,
    onBack: () -> Unit,
    onTagClick: (TagFilter) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val imageHeight = (screenHeight * 0.32f).dp.coerceIn(200.dp, 330.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp)
        ) {
            // ---------- Image ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                VehicleImage(
                    vehicle = vehicle,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(0.dp)),
                    imageWidth = 1200
                )

                // Back button - on top of the image
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            // ---------- Title ----------
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = vehicle.modelName?.takeIf { it.isNotBlank() } ?: "Unnamed model",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MuseumInk
                )

                Spacer(modifier = Modifier.height(8.dp))

                TagChips(vehicle = vehicle, onTagClick = onTagClick)

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(vehicle.availabilityStatus)
                    if (collectionCount > 0 || wishlistCount > 0) {
                        Text(
                            text = "$collectionCount in collection  -  $wishlistCount in wishlist",
                            style = MaterialTheme.typography.labelSmall,
                            color = MuseumInkSoft,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MuseumBorder,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // ---------- Detail rows ----------
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

                DetailSection("Model Details") {
                    DetailRow("Model Year", vehicle.modelYear?.toString())
                    DetailRow("Scale", vehicle.scale)
                    DetailRow("Model Code", vehicle.modelCode)
                    DetailRow("Color", vehicle.color)
                    DetailRow("Material", vehicle.material)
                    DetailRow("Dimensions", vehicle.modelLength?.let {
                        "${trimNumber(it)} ${vehicle.modelLengthUnit.orEmpty()}".trim()
                    })
                }

                DetailSection("Status") {
                    DetailRow("Current Status", vehicle.availabilityStatus)
                    DetailRow("Previous Status", vehicle.previousStatus)
                    DetailRow("Status Changed", vehicle.statusChangedAt?.let {
                        formatDateTime(it)
                    })
                }

                DetailSection("Location & Comparison") {
                    DetailRow("Country", vehicle.countryName?.let {
                        listOfNotNull(vehicle.countryFlag, it).joinToString(" ")
                    })
                    DetailRow("Manufacturer", vehicle.manufacturerName)
                    DetailRow("Brand", vehicle.brandName)
                    DetailRow("Series", vehicle.seriesName)
                    DetailRow("Display Type", vehicle.exhibitionType)
                }

                DetailSection("Collection") {
                    DetailRow("Acquisition Date", vehicle.acquisitionDate)
                    DetailRow("Acquired From", vehicle.acquiredFrom)
                    DetailRow("Market Value", vehicle.marketValue?.let {
                        "${trimNumber(it)} ${vehicle.currency.orEmpty()}".trim()
                    })
                    DetailRow("Rating", vehicle.rating?.takeIf { it > 0 }?.toString())
                    DetailRow("Display No", vehicle.showcaseNum)
                    DetailRow("Shelf No", vehicle.shelfNum)
                    DetailRow("Box No", vehicle.boxNum)
                }

                if (vehicle.isCustomized == true || !vehicle.customizerName.isNullOrBlank()) {
                    DetailSection("Customization") {
                        DetailRow("Customized", if (vehicle.isCustomized == true) "Yes" else "No")
                        DetailRow("Customizer", vehicle.customizerName)
                    }
                }

                if (!vehicle.notes.isNullOrBlank()) {
                    DetailSection("Notes") {
                        Text(
                            text = vehicle.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MuseumInk,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                val links = listOfNotNull(
                    vehicle.instagramUrl?.takeIf { it.isNotBlank() }?.let { "Instagram" to it },
                    vehicle.websiteUrl?.takeIf { it.isNotBlank() }?.let { "Website" to it }
                )
                if (links.isNotEmpty()) {
                    DetailSection("Links") {
                        links.forEach { (label, url) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MuseumInkSoft,
                                    modifier = Modifier.width(110.dp)
                                )
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MuseumGold,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                )
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Record no: ${vehicle.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MuseumInkSoft,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MuseumGold
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MuseumInkSoft,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MuseumInk,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionBar(
    inCollection: Boolean,
    inWishlist: Boolean,
    busy: Boolean,
    onCollection: () -> Unit,
    onWishlist: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onCollection,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (inCollection) StatusCollection else MuseumGold
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (inCollection) "In collection" else "Add to collection")
            }

            OutlinedButton(
                onClick = onWishlist,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (inWishlist) Icons.Outlined.Favorite
                    else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (inWishlist) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (inWishlist) "In wishlist" else "Wishlist")
            }

            if (busy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun formatDateTime(raw: String): String {
    val millis = Timestamps.parseIsoMillis(raw) ?: return raw
    val fmt = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.US)
    return fmt.format(java.util.Date(millis))
}
