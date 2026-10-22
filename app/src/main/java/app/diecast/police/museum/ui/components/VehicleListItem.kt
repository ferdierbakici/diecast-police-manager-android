package app.diecast.police.museum.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.ui.theme.MuseumInkSoft

/**
 * Arama sonuclarinda kullanilan yatay liste satiri.
 * Gorsel solda, metin sagda - telefonda tek elle kaydirmada daha iyi okunur.
 */
@Composable
fun VehicleListItem(
    vehicle: Vehicle,
    onClick: (Vehicle) -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null
) {
    Card(
        onClick = { onClick(vehicle) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VehicleImage(
                vehicle = vehicle,
                modifier = Modifier
                    .width(104.dp)
                    .size(width = 104.dp, height = 78.dp),
                imageWidth = 300
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = vehicle.modelName?.takeIf { it.isNotBlank() } ?: "Unnamed model",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = listOfNotNull(
                    vehicle.modelYear?.takeIf { it > 0 }?.toString(),
                    vehicle.scale?.takeIf { it.isNotBlank() },
                    vehicle.countryName?.takeIf { it.isNotBlank() }
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

                if (vehicle.brandName != null || vehicle.emergencyService != null) {
                    Text(
                        text = listOfNotNull(
                            vehicle.brandName?.takeIf { it.isNotBlank() },
                            vehicle.emergencyService?.takeIf { it.isNotBlank() }
                        ).joinToString(" - "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MuseumInkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(vehicle.availabilityStatus)
                    if (trailingText != null) {
                        Text(
                            text = trailingText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MuseumInkSoft,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
