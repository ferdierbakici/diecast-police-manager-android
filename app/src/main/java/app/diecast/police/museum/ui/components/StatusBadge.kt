package app.diecast.police.museum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.diecast.police.museum.domain.Status
import app.diecast.police.museum.ui.theme.StatusAccent
import app.diecast.police.museum.ui.theme.StatusAccentBg
import app.diecast.police.museum.ui.theme.StatusCollection
import app.diecast.police.museum.ui.theme.StatusCollectionBg
import app.diecast.police.museum.ui.theme.StatusMissing
import app.diecast.police.museum.ui.theme.StatusMissingBg
import app.diecast.police.museum.ui.theme.StatusNeutral
import app.diecast.police.museum.ui.theme.StatusNeutralBg

data class StatusStyle(val label: String, val fg: Color, val bg: Color)

/** The mobile counterpart of the web app's `getStatusStyle` logic. */
fun statusStyle(rawStatus: String?): StatusStyle {
    val label = Status.displayLabel(rawStatus)
    return when (label) {
        "Collection" -> StatusStyle(label, StatusCollection, StatusCollectionBg)
        "Missing" -> StatusStyle(label, StatusMissing, StatusMissingBg)
        "Ordered" -> StatusStyle(label, StatusAccent, StatusAccentBg)
        "To Process" -> StatusStyle(label, StatusNeutral, StatusNeutralBg)
        else -> StatusStyle(label, StatusNeutral, StatusNeutralBg)
    }
}

@Composable
fun StatusBadge(
    rawStatus: String?,
    modifier: Modifier = Modifier
) {
    val style = statusStyle(rawStatus)
    Text(
        text = style.label,
        style = MaterialTheme.typography.labelSmall,
        color = style.fg,
        modifier = modifier
            .background(style.bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
