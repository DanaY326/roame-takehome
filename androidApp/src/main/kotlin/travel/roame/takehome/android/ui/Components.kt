package travel.roame.takehome.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import travel.roame.takehome.domain.FlightDates
import travel.roame.takehome.domain.Formatting
import travel.roame.takehome.model.Flight

/** One row in a flight list. */
@Composable
fun FlightRow(
    flight: Flight,
    isSaved: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.6f) else colors.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(flight.origin, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "to",
                    modifier = Modifier.size(14.dp),
                    tint = colors.onSurfaceVariant,
                )
                Text(flight.destination, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (isSaved) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Saved", modifier = Modifier.size(14.dp), tint = colors.primary)
                }
            }
            Text(
                "${flight.originCity} to ${flight.destinationCity}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${FlightDates.formatMedium(flight.departureDate)} · ${flight.cabin} · ${flight.program}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (footnote != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(12.dp), tint = Warning)
                    Text(footnote, style = MaterialTheme.typography.labelSmall, color = Warning)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Formatting.groupedNumber(flight.points),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text("points", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

/** Origin and destination joined by a dashed connector with a plane. */
@Composable
fun RouteConnector(flight: Flight, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Endpoint(flight.origin, flight.originCity, Alignment.Start)
        Box(Modifier.weight(1f).height(28.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                val y = size.height / 2
                val r = 3.5.dp.toPx()
                drawLine(
                    color = accent.copy(alpha = 0.5f),
                    start = Offset(r * 2, y),
                    end = Offset(size.width - r * 2, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
                )
                drawCircle(accent, radius = r, center = Offset(r, y))
                drawCircle(accent, radius = r, center = Offset(size.width - r, y), style = Stroke(width = 2.dp.toPx()))
            }
            Text(
                "✈",
                color = accent,
                fontSize = 16.sp,
                modifier = Modifier.background(surface, CircleShape).padding(horizontal = 4.dp),
            )
        }
        Endpoint(flight.destination, flight.destinationCity, Alignment.End)
    }
}

@Composable
private fun Endpoint(code: String, city: String, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        Text(code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            city,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp),
        )
    }
}

/** The selected-flight card: route, date, cabin, points, program and Save/Remove. */
@Composable
fun FlightCard(
    flight: Flight,
    isSaved: Boolean,
    isPending: Boolean,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    flight.program.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f),
                )
                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }
            RouteConnector(flight)
            Row {
                Detail("Departs", FlightDates.formatLong(flight.departureDate), Modifier.weight(1f))
                Detail("Cabin", flight.cabin, Modifier.weight(1f))
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Formatting.groupedNumber(flight.points),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "points · ${flight.program}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SaveButton(isSaved = isSaved, isPending = isPending, onClick = onToggleSave)
            }
        }
    }
}

@Composable
private fun Detail(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SaveButton(isSaved: Boolean, isPending: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        if (isPending) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(if (isSaved) "Remove" else "Save")
    }
    if (isSaved) {
        OutlinedButton(onClick = onClick, enabled = !isPending, modifier = Modifier.widthIn(min = 112.dp)) { content() }
    } else {
        Button(onClick = onClick, enabled = !isPending, modifier = Modifier.widthIn(min = 112.dp)) { content() }
    }
}

/** Centered icon + title + message + optional action, used for loading/empty/error states. */
@Composable
fun StateMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.invoke()
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}
