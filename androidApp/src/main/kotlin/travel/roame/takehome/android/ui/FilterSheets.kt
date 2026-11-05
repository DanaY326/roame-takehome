package travel.roame.takehome.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import travel.roame.takehome.domain.FlightDates
import travel.roame.takehome.domain.Formatting
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.FlightExplorer
import kotlin.math.ceil
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(kind: FilterSheetKind, state: ExplorerState, explorer: FlightExplorer, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (kind) {
            FilterSheetKind.DATES -> DatesSheet(state, explorer, onDismiss)
            FilterSheetKind.POINTS -> PointsSheet(state, explorer, onDismiss)
            FilterSheetKind.PROGRAMS -> ProgramsSheet(state, explorer, onDismiss)
        }
    }
}

@Composable
private fun SheetHeader(title: String, onReset: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onReset) { Text("Reset") }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = onDone) { Text("Done", fontWeight = FontWeight.SemiBold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatesSheet(state: ExplorerState, explorer: FlightExplorer, onDismiss: () -> Unit) {
    val options = state.options
    val minMs = options.minDate?.let(FlightDates::toUtcEpochMillis) ?: 0L
    val maxMs = options.maxDate?.let(FlightDates::toUtcEpochMillis) ?: Long.MAX_VALUE
    val years = (FlightDates.fromUtcEpochMillis(minMs).take(4).toInt())..(FlightDates.fromUtcEpochMillis(maxMs.coerceAtMost(253_402_214_400_000L)).take(4).toInt())
    val startMs = (state.filter.startDate ?: options.minDate)?.let(FlightDates::toUtcEpochMillis)
    val endMs = (state.filter.endDate ?: options.maxDate)?.let(FlightDates::toUtcEpochMillis)

    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startMs,
        initialSelectedEndDateMillis = endMs,
        initialDisplayedMonthMillis = startMs,
        yearRange = years,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in minMs..maxMs
            override fun isSelectableYear(year: Int) = year in years
        },
    )

    Column(Modifier.navigationBarsPadding()) {
        SheetHeader(
            title = "Departure dates",
            onReset = {
                explorer.setDateRange(null, null)
                pickerState.setSelection(minMs, maxMs)
            },
            onDone = {
                val start = pickerState.selectedStartDateMillis
                val end = pickerState.selectedEndDateMillis ?: start
                if (start != null && end != null) {
                    explorer.setDateRange(FlightDates.fromUtcEpochMillis(start), FlightDates.fromUtcEpochMillis(end))
                }
                onDismiss()
            },
        )
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.fillMaxWidth().height(480.dp),
            title = null,
            showModeToggle = false,
        )
        Text(
            "Both dates are included. Tap one day twice to pick a single day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PointsSheet(state: ExplorerState, explorer: FlightExplorer, onDismiss: () -> Unit) {
    val options = state.options
    val step = options.pointsStep.toFloat()
    val lower = floor(options.minPoints / step) * step
    val upper = maxOf(ceil(options.maxPoints / step) * step, lower + step)
    var value by remember { mutableFloatStateOf(state.filter.maxPoints?.toFloat() ?: upper) }

    fun apply(v: Float) {
        explorer.setMaxPoints(if (v >= upper) null else v.toInt())
    }

    Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
        SheetHeader("Maximum points", onReset = { value = upper; apply(upper) }, onDone = onDismiss)
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (value >= upper) "Any price" else "Up to ${Formatting.points(value.toInt())}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = value,
                onValueChange = { value = it; apply(it) },
                valueRange = lower..upper,
                steps = (((upper - lower) / step).toInt() - 1).coerceAtLeast(0),
            )
            Row {
                Text(Formatting.compactPoints(lower.toInt()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(Formatting.compactPoints(upper.toInt()), style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "Includes flights priced exactly at the limit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgramsSheet(state: ExplorerState, explorer: FlightExplorer, onDismiss: () -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
        SheetHeader("Loyalty programs", onReset = { explorer.setPrograms(emptySet()) }, onDone = onDismiss)
        state.options.programs.forEach { program ->
            val checked = program in state.filter.programs
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { explorer.toggleProgram(program) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { explorer.toggleProgram(program) })
                Spacer(Modifier.padding(4.dp))
                Text(program, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(
            "Shows flights bookable with any selected program. Select none to include all.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
