package app.diecast.police.museum.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.diecast.police.museum.data.model.ALL
import app.diecast.police.museum.data.model.FilterOptions
import app.diecast.police.museum.data.model.Filters
import app.diecast.police.museum.domain.EmergencyGroups
import app.diecast.police.museum.ui.components.SelectField
import app.diecast.police.museum.ui.theme.MuseumInkSoft

/**
 * The filter form in a bottom sheet. The mobile adaptation of the
 * "Search Criteria" form of the web app.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    initial: Filters,
    options: FilterOptions,
    onDismiss: () -> Unit,
    onApply: (Filters) -> Unit,
    onClear: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            if (!options.loaded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }

            SelectField(
                label = "Country",
                value = draft.country,
                options = options.countries,
                onSelect = { draft = draft.copy(country = it) },
                enabled = options.loaded
            )

            SelectField(
                label = "Manufacturer",
                value = draft.manufacturer,
                options = options.manufacturers,
                onSelect = { draft = draft.copy(manufacturer = it) },
                enabled = options.loaded,
                modifier = Modifier.padding(top = 10.dp)
            )

            SelectField(
                label = "Brand",
                value = draft.brand,
                options = options.brands,
                onSelect = { draft = draft.copy(brand = it) },
                enabled = options.loaded,
                modifier = Modifier.padding(top = 10.dp)
            )

            SelectField(
                label = "Series",
                value = draft.series,
                options = options.series,
                onSelect = { draft = draft.copy(series = it) },
                enabled = options.loaded,
                modifier = Modifier.padding(top = 10.dp)
            )

            SelectField(
                label = "Status",
                value = draft.status,
                options = options.statuses,
                onSelect = { draft = draft.copy(status = it) },
                enabled = options.loaded,
                modifier = Modifier.padding(top = 10.dp)
            )

            Text(
                text = "Emergency Service Group",
                style = MaterialTheme.typography.titleSmall,
                color = MuseumInkSoft,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = draft.serviceGroup == ALL,
                    onClick = { draft = draft.copy(serviceGroup = ALL, service = ALL) },
                    label = { Text("All") }
                )
                EmergencyGroups.GROUPS.forEach { group ->
                    FilterChip(
                        selected = draft.serviceGroup == group,
                        onClick = {
                            draft = if (draft.serviceGroup == group) {
                                draft.copy(serviceGroup = ALL, service = ALL)
                            } else {
                                draft.copy(serviceGroup = group, service = ALL)
                            }
                        },
                        label = { Text(group) }
                    )
                }
            }

            val serviceOptions = remember(draft.serviceGroup, options.services) {
                if (draft.serviceGroup == ALL) options.services
                else options.services.filter { EmergencyGroups.groupOf(it) == draft.serviceGroup }
            }

            SelectField(
                label = "Emergency Service",
                value = draft.service,
                options = serviceOptions,
                onSelect = { draft = draft.copy(service = it) },
                enabled = options.loaded && serviceOptions.isNotEmpty(),
                modifier = Modifier.padding(top = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }

                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }
            }
        }
    }
}
