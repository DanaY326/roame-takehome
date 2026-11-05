package travel.roame.takehome.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.FlightExplorer

/** Saved flights: independent of discovery filters, available offline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(state: ExplorerState, explorer: FlightExplorer) {
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Saved") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (state.saved.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StateMessage(
                    title = "No saved flights",
                    message = state.savedError ?: "Save flights from Explore and they'll be here — even offline.",
                    icon = { Icon(Icons.Filled.FavoriteBorder, contentDescription = null, modifier = Modifier.size(36.dp)) },
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.saved, key = { it.flight.id }) { item ->
                    Row(
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FlightRow(
                            flight = item.flight,
                            isSaved = false,
                            isSelected = false,
                            footnote = if (item.isInLatestCatalog) null else "Not in the latest results",
                            modifier = Modifier.weight(1f).clickable { detailId = item.flight.id },
                        )
                        if (state.isPending(item.flight.id)) {
                            CircularProgressIndicator(Modifier.padding(horizontal = 14.dp).size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { explorer.remove(item.flight.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove ${item.flight.origin} to ${item.flight.destination}")
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                item {
                    Text(
                        "Saved flights are stored on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    val detail = detailId?.let { id -> state.saved.firstOrNull { it.flight.id == id } }
    LaunchedEffect(detailId, detail) { if (detailId != null && detail == null) detailId = null }
    if (detail != null) {
        ModalBottomSheet(onDismissRequest = { detailId = null }) {
            FlightCard(
                flight = detail.flight,
                isSaved = true,
                isPending = state.isPending(detail.flight.id),
                onToggleSave = { explorer.remove(detail.flight.id) },
                modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            )
        }
    }
}
