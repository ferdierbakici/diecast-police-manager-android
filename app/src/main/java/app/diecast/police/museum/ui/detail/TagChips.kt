package app.diecast.police.museum.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.diecast.police.museum.data.model.TagFilter
import app.diecast.police.museum.data.model.Vehicle

/**
 * A tag under the image of the detail screen.
 *
 * Every tag is a filter criterion: tapping it switches to the search screen and
 * shows the models of that criterion.
 */
internal data class TagChip(
    /** The visible text (the country tag also shows its flag). */
    val label: String,
    /** The spoken description, e.g. "Filter by country Germany". */
    val description: String,
    /** The filter criterion of this tag. */
    val filter: TagFilter
)

/**
 * Builds the tags of a vehicle: country, brand, manufacturer, series and emergency service.
 *
 * A missing value produces no tag (the same way the web app hides an empty field).
 */
internal fun buildTagChips(vehicle: Vehicle): List<TagChip> = listOfNotNull(
    vehicle.countryName?.takeIf { it.isNotBlank() }?.let { name ->
        TagChip(
            label = listOfNotNull(vehicle.countryFlag, name).joinToString(" "),
            description = "Filter by country $name",
            filter = TagFilter(country = name)
        )
    },
    vehicle.brandName?.takeIf { it.isNotBlank() }?.let { name ->
        TagChip(
            label = name,
            description = "Filter by brand $name",
            filter = TagFilter(brand = name)
        )
    },
    vehicle.manufacturerName?.takeIf { it.isNotBlank() }?.let { name ->
        TagChip(
            label = name,
            description = "Filter by manufacturer $name",
            filter = TagFilter(manufacturer = name)
        )
    },
    vehicle.seriesName?.takeIf { it.isNotBlank() }?.let { name ->
        TagChip(
            label = name,
            description = "Filter by series $name",
            filter = TagFilter(series = name)
        )
    },
    vehicle.emergencyService?.takeIf { it.isNotBlank() }?.let { name ->
        TagChip(
            label = name,
            description = "Filter by emergency service $name",
            filter = TagFilter(service = name)
        )
    }
)

/**
 * The row of clickable tags under the image of the detail screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagChips(
    vehicle: Vehicle,
    onTagClick: (TagFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        buildTagChips(vehicle).forEach { chip ->
            Surface(
                onClick = { onTagClick(chip.filter) },
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = chip.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                        .clearAndSetSemantics { contentDescription = chip.description }
                )
            }
        }
    }
}
