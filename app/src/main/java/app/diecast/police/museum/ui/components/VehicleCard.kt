package app.diecast.police.museum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.ui.theme.MuseumBorder
import app.diecast.police.museum.ui.theme.MuseumCreamDark
import app.diecast.police.museum.ui.theme.MuseumInkSoft

/**
 * A vehicle card. The mobile adaptation of the small "model card" of the web app:
 * image on top, below it a 2-line name + meta line + status badge.
 */
@Composable
fun VehicleCard(
    vehicle: Vehicle,
    onClick: (Vehicle) -> Unit,
    modifier: Modifier = Modifier,
    showStatus: Boolean = true
) {
    Card(
        onClick = { onClick(vehicle) },
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            VehicleImage(
                vehicle = vehicle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            )

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = vehicle.modelName?.takeIf { it.isNotBlank() } ?: "Unnamed model",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = listOfNotNull(
                    vehicle.modelYear?.takeIf { it > 0 }?.toString(),
                    vehicle.scale?.takeIf { it.isNotBlank() }
                ).joinToString(" - ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MuseumInkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val country = buildString {
                    vehicle.countryFlag?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
                    vehicle.countryName?.takeIf { it.isNotBlank() }?.let { append(it) }
                }
                if (country.isNotEmpty()) {
                    Text(
                        text = country,
                        style = MaterialTheme.typography.bodySmall,
                        color = MuseumInkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (showStatus) {
                    Spacer(modifier = Modifier.height(6.dp))
                    StatusBadge(vehicle.availabilityStatus)
                }
            }
        }
    }
}

/** The image, or a placeholder when there is none. */
@Composable
fun VehicleImage(
    vehicle: Vehicle,
    modifier: Modifier = Modifier,
    imageWidth: Int = 800
) {
    val url = cloudinaryUrl(vehicle.imageUrl, imageWidth)
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MuseumCreamDark),
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = MuseumBorder,
                modifier = Modifier.size(40.dp)
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = vehicle.modelName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** A horizontal list card (for the home screen sections). */
@Composable
fun VehicleRowCard(
    vehicle: Vehicle,
    onClick: (Vehicle) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onClick(vehicle) },
        modifier = modifier.width(172.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            VehicleImage(
                vehicle = vehicle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
                imageWidth = 400
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = vehicle.modelName?.takeIf { it.isNotBlank() } ?: "Unnamed model",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        vehicle.modelYear?.takeIf { it > 0 }?.toString(),
                        vehicle.countryName?.takeIf { it.isNotBlank() }
                    ).joinToString(" - "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MuseumInkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
