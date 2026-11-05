package travel.roame.takehome.android.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import travel.roame.takehome.domain.FilterKind
import travel.roame.takehome.model.Flight
import travel.roame.takehome.domain.FlightDates
import travel.roame.takehome.domain.Formatting
import travel.roame.takehome.presentation.CatalogStatus
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.FlightExplorer

private enum class ExploreMode { LIST, MAP }

internal enum class FilterSheetKind { DATES, POINTS, PROGRAMS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(state: ExplorerState, explorer: FlightExplorer) {
    var mode by rememberSaveable { mutableStateOf(ExploreMode.LIST) }
    var openSheet by rememberSaveable { mutableStateOf<FilterSheetKind?>(null) }
    // Search text is owned by the UI so typing is never delayed by a state round-trip.
    var origin by rememberSaveable { mutableStateOf(state.filter.originQuery) }
    var destination by rememberSaveable { mutableStateOf(state.filter.destinationQuery) }
    val listState = rememberLazyListState()
    val mapCamera = remember { MapCameraMemory() }

    val resetAll = {
        origin = ""
        destination = ""
        explorer.resetFilters()
    }

    BackHandler(enabled = state.selectedFlight != null) { explorer.select(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CenterAlignedTopAppBar(
            title = {
                SingleChoiceSegmentedButtonRow(Modifier.width(220.dp)) {
                    ExploreMode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(index, ExploreMode.entries.size),
                            icon = {
                                Icon(
                                    if (m == ExploreMode.LIST) Icons.AutoMirrored.Filled.List else Icons.Filled.Place,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            },
                        ) { Text(if (m == ExploreMode.LIST) "List" else "Map") }
                    }
                }
            },
            actions = {
                if (state.isRefreshing && state.status == CatalogStatus.READY) {
                    CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = explorer::refresh, enabled = state.status != CatalogStatus.LOADING) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        when {
            state.status == CatalogStatus.LOADING -> Centered {
                StateMessage(
                    title = "Loading flights…",
                    message = "Fetching the latest award availability.",
                    icon = { CircularProgressIndicator() },
                )
            }

            state.status == CatalogStatus.ERROR -> Centered {
                StateMessage(
                    title = "Can't load flights",
                    message = state.errorMessage ?: "Something went wrong.",
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(40.dp)) },
                    action = { Button(onClick = explorer::refresh) { Text("Try again") } },
                )
            }

            state.totalFlightCount == 0 -> Centered {
                StateMessage(
                    title = "No flights available",
                    message = "The server responded, but there are no flights right now.",
                    action = { OutlinedButton(onClick = explorer::refresh) { Text("Refresh") } },
                )
            }

            else -> {
                SearchHeader(
                    state = state,
                    origin = origin,
                    destination = destination,
                    onOriginChange = { origin = it; explorer.setOriginQuery(it) },
                    onDestinationChange = { destination = it; explorer.setDestinationQuery(it) },
                    onOpenSheet = { openSheet = it },
                    onCabin = explorer::setCabin,
                    onReset = resetAll,
                )
                StaleBanner(state, onRetry = explorer::refresh)
                DiscoveryContent(
                    state = state,
                    explorer = explorer,
                    mode = mode,
                    listState = listState,
                    mapCamera = mapCamera,
                    onReset = resetAll,
                )
            }
        }
    }

    openSheet?.let { kind ->
        FilterSheet(kind = kind, state = state, explorer = explorer, onDismiss = { openSheet = null })
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryContent(
    state: ExplorerState,
    explorer: FlightExplorer,
    mode: ExploreMode,
    listState: LazyListState,
    mapCamera: MapCameraMemory,
    onReset: () -> Unit,
) {
    var cardHeightPx by remember { mutableStateOf(0) }
    val cardHeight = with(LocalDensity.current) { cardHeightPx.toDp() }
    val selected = state.selectedFlight

    Box(Modifier.fillMaxSize()) {
        when (mode) {
            ExploreMode.LIST -> if (state.results.isEmpty()) {
                Centered { NoMatches(onReset) }
            } else {
                PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = explorer::refresh) {
                    FlightList(
                        state = state,
                        listState = listState,
                        bottomPadding = if (selected != null) cardHeight + 16.dp else 16.dp,
                        onSelect = { id -> explorer.select(if (selected?.id == id) null else id) },
                    )
                }
            }

            ExploreMode.MAP -> {
                FlightMap(
                    markers = state.markers,
                    selected = selected,
                    camera = mapCamera,
                    modifier = Modifier.fillMaxSize(),
                    onSelectFlight = { explorer.select(it.id) },
                    savedIds = state.savedIds,
                )
                if (state.markers.isEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                    ) { NoMatches(onReset) }
                }
            }
        }

        AnimatedVisibility(
            visible = selected != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            // Keep showing the last flight while the exit animation runs (plain holder, not state).
            val lastShown = remember { arrayOfNulls<Flight>(1) }
            if (selected != null) lastShown[0] = selected
            lastShown[0]?.let { flight ->
                FlightCard(
                    flight = flight,
                    isSaved = state.isSaved(flight.id),
                    isPending = state.isPending(flight.id),
                    onToggleSave = { explorer.toggleSaved(flight) },
                    onClose = { explorer.select(null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .onSizeChanged { cardHeightPx = it.height },
                )
            }
        }
    }
}

@Composable
private fun FlightList(
    state: ExplorerState,
    listState: LazyListState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSelect: (String) -> Unit,
) {
    val selectedId = state.selectedFlight?.id
    // Coming back from the map: bring the selected flight into view.
    LaunchedEffect(Unit) {
        if (selectedId != null) {
            val index = state.results.indexOfFirst { it.id == selectedId }
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.key == selectedId }
            if (index >= 0 && !visible) listState.scrollToItem(index)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        items(state.results, key = { it.id }) { flight ->
            Column {
                FlightRow(
                    flight = flight,
                    isSaved = state.isSaved(flight.id),
                    isSelected = flight.id == selectedId,
                    modifier = Modifier.clickable { onSelect(flight.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun NoMatches(onReset: () -> Unit) {
    StateMessage(
        title = "No matching flights",
        message = "Try a different airport or loosen your filters.",
        icon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(36.dp)) },
        action = { Button(onClick = onReset) { Text("Reset filters") } },
    )
}

@Composable
private fun SearchHeader(
    state: ExplorerState,
    origin: String,
    destination: String,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOpenSheet: (FilterSheetKind) -> Unit,
    onCabin: (String?) -> Unit,
    onReset: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(modifier = Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 1.dp) {
            Column {
                SearchField("From", "City or airport code", Icons.Filled.LocationOn, origin, onOriginChange)
                HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SearchField("To", "City or airport code", Icons.Filled.Place, destination, onDestinationChange)
            }
        }

        val chipScroll = rememberScrollState()
        Row(
            Modifier
                .fillMaxWidth()
                .scrollEdgeFade(chipScroll)
                .horizontalScroll(chipScroll)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dateLabel = state.activeFilters.firstOrNull { it.kind == FilterKind.DATES }?.label
            val fullRange = state.options.let { o ->
                if (o.minDate != null && o.maxDate != null) FlightDates.formatRange(o.minDate!!, o.maxDate!!) else "Dates"
            }
            Chip(dateLabel ?: fullRange, dateLabel != null, Icons.Filled.DateRange) { onOpenSheet(FilterSheetKind.DATES) }

            Box {
                var expanded by remember { mutableStateOf(false) }
                Chip(state.filter.cabin ?: "Cabin", state.filter.cabin != null) { expanded = true }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Any cabin") }, onClick = { onCabin(null); expanded = false })
                    state.options.cabins.forEach { cabin ->
                        DropdownMenuItem(
                            text = { Text(cabin, fontWeight = if (cabin == state.filter.cabin) FontWeight.SemiBold else null) },
                            onClick = { onCabin(cabin); expanded = false },
                        )
                    }
                }
            }

            val pointsLabel = state.activeFilters.firstOrNull { it.kind == FilterKind.MAX_POINTS }?.label
            Chip(pointsLabel ?: "Points", pointsLabel != null) { onOpenSheet(FilterSheetKind.POINTS) }

            val programsLabel = state.activeFilters.firstOrNull { it.kind == FilterKind.PROGRAMS }?.label
            Chip(programsLabel ?: "Programs", programsLabel != null) { onOpenSheet(FilterSheetKind.PROGRAMS) }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            val count = Formatting.flightCount(state.resultCount)
            Text(
                if (state.hasActiveFilters) "$count of ${Formatting.groupedNumber(state.totalFlightCount)}" else count,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (state.hasActiveFilters) {
                TextButton(onClick = { focus.clearFocus(); onReset() }) { Text("Reset filters") }
            }
        }
    }
}

@Composable
private fun SearchField(label: String, placeholder: String, icon: ImageVector, value: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear $label") }
            }
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Fades out the edges of a horizontally scrolling row when more content lies beyond them. */
private fun Modifier.scrollEdgeFade(state: ScrollState, fadeWidth: Dp = 28.dp): Modifier = composed {
    val leading by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, label = "leadingFade")
    val trailing by animateFloatAsState(if (state.canScrollForward) 1f else 0f, label = "trailingFade")
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = fadeWidth.toPx()
            if (leading > 0f) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 1f - leading), Color.Black), startX = 0f, endX = w),
                    size = size.copy(width = w),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (trailing > 0f) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - trailing)), startX = size.width - w, endX = size.width),
                    topLeft = Offset(size.width - w, 0f),
                    size = size.copy(width = w),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

@Composable
private fun Chip(label: String, active: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
        shape = RoundedCornerShape(50),
    )
}

@Composable
private fun StaleBanner(state: ExplorerState, onRetry: () -> Unit) {
    if (!state.isStale) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = Warning.copy(alpha = 0.18f),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Offline — showing saved results", style = MaterialTheme.typography.labelLarge)
                state.lastUpdatedEpochMs?.let {
                    Text(
                        "Last updated ${DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (state.isRefreshing) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
