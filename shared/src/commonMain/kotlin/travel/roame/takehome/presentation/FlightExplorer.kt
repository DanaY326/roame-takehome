package travel.roame.takehome.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.data.attempt
import travel.roame.takehome.domain.ActiveFilters
import travel.roame.takehome.domain.DestinationMarker
import travel.roame.takehome.domain.FilterKind
import travel.roame.takehome.domain.FilterOptions
import travel.roame.takehome.domain.FlightFilter
import travel.roame.takehome.domain.FlightFiltering
import travel.roame.takehome.domain.MapAggregation
import travel.roame.takehome.domain.ActiveFilter
import travel.roame.takehome.model.Flight

/** Handle returned by [FlightExplorer.observe]; call [cancel] to stop receiving updates. */
interface StateObservation {
    fun cancel()
}

/**
 * The shared presentation layer. Both apps send user intents here and render [state].
 *
 * Filtering and map aggregation run on [computeDispatcher] with `mapLatest`, so the UI thread never
 * does the work and a newer filter always cancels an in-progress older computation — the results
 * on screen always reflect the user's latest input.
 */
class FlightExplorer(
    private val repository: FlightRepository,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val filter = MutableStateFlow(FlightFilter())
    private val selectedId = MutableStateFlow<String?>(null)
    private val pendingSaveIds = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<UserMessage?>(null)
    private var nextMessageId = 1L
    private var started = false

    private data class Derived(
        val filter: FlightFilter,
        val options: FilterOptions,
        val results: List<Flight>,
        val resultsById: Map<String, Flight>,
        val markers: List<DestinationMarker>,
        val activeFilters: List<ActiveFilter>,
        val catalogIds: Set<String>,
    )

    private val catalogFlights = repository.catalog
        .map { it.flights.orEmpty() }
        .distinctUntilChanged { a, b -> a === b }

    private val catalogDerived = catalogFlights
        .mapLatest { flights ->
            withContext(computeDispatcher) {
                Triple(flights, FilterOptions.from(flights), flights.mapTo(HashSet(flights.size)) { it.id })
            }
        }

    private val derived: StateFlow<Derived?> = combine(catalogDerived, filter) { catalog, f -> catalog to f }
        .mapLatest { (catalog, f) ->
            val (flights, options, ids) = catalog
            withContext(computeDispatcher) {
                val results = FlightFiltering.apply(flights, f)
                Derived(
                    filter = f,
                    options = options,
                    results = results,
                    resultsById = results.associateBy { it.id },
                    markers = MapAggregation.byDestination(results),
                    activeFilters = ActiveFilters.describe(f, options),
                    catalogIds = ids,
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private data class Inputs(
        val filter: FlightFilter,
        val selectedId: String?,
        val pending: Set<String>,
        val message: UserMessage?,
        val savedError: String?,
    )

    private val inputs = combine(filter, selectedId, pendingSaveIds, message, repository.savedLoadError, ::Inputs)

    val state: StateFlow<ExplorerState> =
        combine(repository.catalog, derived, repository.saved, inputs) { catalog, d, saved, input ->
            val status = when {
                catalog.flights != null -> CatalogStatus.READY
                catalog.hasCompletedInitialLoad && !catalog.isRefreshing -> CatalogStatus.ERROR
                else -> CatalogStatus.LOADING
            }
            val catalogIds = d?.catalogIds.orEmpty()
            ExplorerState(
                status = status,
                isRefreshing = catalog.isRefreshing,
                isStale = catalog.flights != null && catalog.refreshError != null,
                errorMessage = catalog.refreshError,
                lastUpdatedEpochMs = catalog.fetchedAtEpochMs,
                totalFlightCount = catalog.flights?.size ?: 0,
                filter = input.filter,
                options = d?.options ?: FilterOptions.EMPTY,
                results = d?.results.orEmpty(),
                markers = d?.markers.orEmpty(),
                activeFilters = d?.activeFilters.orEmpty(),
                selectedFlight = input.selectedId?.let { d?.resultsById?.get(it) },
                saved = saved.map { SavedFlightItem(it.flight, it.savedAtEpochMs, it.flight.id in catalogIds) },
                savedIds = saved.mapTo(HashSet(saved.size)) { it.flight.id },
                pendingSaveIds = input.pending,
                savedError = input.savedError,
                message = input.message,
            )
        }.stateIn(scope, SharingStarted.Eagerly, ExplorerState.INITIAL)

    init {
        // Selection only lives while the flight is in the filtered results: once a filter
        // excludes it, the selection is cleared (and does not come back when the filter is relaxed).
        scope.launch {
            derived.collect { d ->
                val id = selectedId.value ?: return@collect
                if (d != null && d.filter == filter.value && id !in d.resultsById) selectedId.value = null
            }
        }
    }

    /** Loads cached data, then refreshes from the network. Safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            repository.loadLocal()
            repository.refresh()
        }
    }

    fun refresh() {
        scope.launch { repository.refresh() }
    }

    // region Filters

    fun setOriginQuery(query: String) = filter.update { it.copy(originQuery = query) }
    fun setDestinationQuery(query: String) = filter.update { it.copy(destinationQuery = query) }

    /** Sets an inclusive date range (ISO dates). Reversed bounds are swapped; null means unbounded. */
    fun setDateRange(startDate: String?, endDate: String?) {
        val (start, end) = if (startDate != null && endDate != null && startDate > endDate) endDate to startDate else startDate to endDate
        val options = state.value.options
        filter.update {
            it.copy(
                startDate = start?.takeUnless { s -> options.minDate != null && s <= options.minDate },
                endDate = end?.takeUnless { e -> options.maxDate != null && e >= options.maxDate },
            )
        }
    }

    fun setCabin(cabin: String?) = filter.update { it.copy(cabin = cabin) }

    /** Sets the inclusive points ceiling. A value at or above the catalog maximum means "no limit". */
    fun setMaxPoints(maxPoints: Int?) {
        val ceiling = state.value.options.maxPoints
        filter.update { it.copy(maxPoints = maxPoints?.takeUnless { p -> ceiling > 0 && p >= ceiling }) }
    }

    fun toggleProgram(program: String) = filter.update {
        it.copy(programs = if (program in it.programs) it.programs - program else it.programs + program)
    }

    fun setPrograms(programs: Set<String>) = filter.update { it.copy(programs = programs) }

    fun clearFilter(kind: FilterKind) = filter.update {
        when (kind) {
            FilterKind.ORIGIN -> it.copy(originQuery = "")
            FilterKind.DESTINATION -> it.copy(destinationQuery = "")
            FilterKind.DATES -> it.copy(startDate = null, endDate = null)
            FilterKind.CABIN -> it.copy(cabin = null)
            FilterKind.MAX_POINTS -> it.copy(maxPoints = null)
            FilterKind.PROGRAMS -> it.copy(programs = emptySet())
        }
    }

    fun resetFilters() {
        filter.value = FlightFilter()
    }

    // endregion

    // region Selection & saving

    /** Selects a flight from the current results (or clears with null). Flights not in the results are ignored. */
    fun select(flightId: String?) {
        if (flightId == null || derived.value?.resultsById?.containsKey(flightId) == true) {
            selectedId.value = flightId
        }
    }

    fun save(flight: Flight) = mutateSaved(flight.id, "Couldn't save this flight. Please try again.") {
        repository.save(flight)
    }

    fun remove(flightId: String) = mutateSaved(flightId, "Couldn't remove this flight. Please try again.") {
        repository.remove(flightId)
    }

    fun toggleSaved(flight: Flight) {
        if (state.value.isSaved(flight.id)) remove(flight.id) else save(flight)
    }

    private fun mutateSaved(flightId: String, failureText: String, block: suspend () -> Unit) {
        if (flightId in pendingSaveIds.value) return
        pendingSaveIds.update { it + flightId }
        scope.launch {
            try {
                attempt { block() }.onFailure { postMessage(failureText) }
            } finally {
                pendingSaveIds.update { it - flightId }
            }
        }
    }

    // endregion

    private fun postMessage(text: String) {
        message.value = UserMessage(nextMessageId++, text)
    }

    fun dismissMessage(id: Long) {
        message.update { if (it?.id == id) null else it }
    }

    /**
     * Callback-style observation for Swift. [onEach] is invoked on the main thread with the current
     * state immediately and on every change, until the returned handle is cancelled.
     */
    fun observe(onEach: (ExplorerState) -> Unit): StateObservation {
        val job = scope.launch { state.collect { onEach(it) } }
        return object : StateObservation {
            override fun cancel() = job.cancel()
        }
    }

    /** Cancels all work. The instance can't be used afterwards. */
    fun close() {
        scope.cancel()
    }
}
