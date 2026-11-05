package travel.roame.takehome.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import travel.roame.takehome.domain.FlightFiltering
import travel.roame.takehome.model.CachedFlights
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.SavedFlight

/** What the app currently knows about the flight catalog. */
data class CatalogState(
    /** The flights on screen: the latest successful response, or the cached one. Null if we have none. */
    val flights: List<Flight>? = null,
    /** When [flights] was fetched from the API. */
    val fetchedAtEpochMs: Long? = null,
    val isRefreshing: Boolean = false,
    /** Message of the most recent failed refresh; cleared by the next successful one. */
    val refreshError: String? = null,
    /** False until the first local load + network attempt has finished. */
    val hasCompletedInitialLoad: Boolean = false,
)

/**
 * Single source of truth for flights and saved flights.
 *
 * - The catalog is served from the on-disk cache first, then refreshed from the API. A failed refresh
 *   keeps whatever data we already had and records [CatalogState.refreshError].
 * - Saved flights are stored separately as full snapshots, so they stay available even when they
 *   disappear from the API. State only changes after the write to disk succeeds, so a failed save or
 *   remove can never look successful.
 */
class FlightRepository(
    private val api: FlightApi,
    private val cacheStore: DocumentStore<CachedFlights>,
    private val savedStore: DocumentStore<List<SavedFlight>>,
    private val now: () -> Long,
) {
    private val _catalog = MutableStateFlow(CatalogState())
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _saved = MutableStateFlow<List<SavedFlight>>(emptyList())
    val saved: StateFlow<List<SavedFlight>> = _saved.asStateFlow()

    private val _savedLoadError = MutableStateFlow<String?>(null)
    val savedLoadError: StateFlow<String?> = _savedLoadError.asStateFlow()

    private val refreshMutex = Mutex()
    private val savedMutex = Mutex()
    private var savedLoaded = false

    /** Loads the cached catalog and saved flights from disk. Never throws (except cancellation). */
    suspend fun loadLocal() {
        val cached = attempt { cacheStore.read() }.getOrNull()
        if (cached != null) {
            // Never let a slow disk read overwrite fresher network data.
            _catalog.update { current ->
                if (current.flights != null) current
                else current.copy(flights = cached.flights.sortedWith(FlightFiltering.defaultOrder), fetchedAtEpochMs = cached.fetchedAtEpochMs)
            }
        }
        loadSaved()
    }

    private suspend fun loadSaved() = savedMutex.withLock { ensureSavedLoadedLocked() }

    /** Must hold [savedMutex]. Returns false if the saved flights couldn't be read from disk. */
    private suspend fun ensureSavedLoadedLocked(): Boolean {
        if (savedLoaded) return true
        return attempt { savedStore.read() }
            .onSuccess {
                _saved.value = it.orEmpty()
                _savedLoadError.value = null
                savedLoaded = true
            }
            .onFailure { _savedLoadError.value = "Couldn't load your saved flights." }
            .isSuccess
    }

    /**
     * Fetches the catalog from the API. Returns true on success. Concurrent calls are coalesced:
     * while one refresh is in flight, further calls return immediately.
     */
    suspend fun refresh(): Boolean {
        if (!refreshMutex.tryLock()) return false
        try {
            _catalog.update { it.copy(isRefreshing = true) }
            val result = attempt { api.fetchFlights() }
            result.onSuccess { flights ->
                val sorted = flights.sortedWith(FlightFiltering.defaultOrder)
                val fetchedAt = now()
                _catalog.value = CatalogState(
                    flights = sorted,
                    fetchedAtEpochMs = fetchedAt,
                    isRefreshing = false,
                    refreshError = null,
                    hasCompletedInitialLoad = true,
                )
                // A failed cache write doesn't invalidate the fresh data on screen; the next
                // successful refresh will try again.
                attempt { cacheStore.write(CachedFlights(fetchedAt, sorted)) }
            }.onFailure { error ->
                _catalog.update {
                    it.copy(isRefreshing = false, refreshError = describe(error), hasCompletedInitialLoad = true)
                }
            }
            return result.isSuccess
        } finally {
            // Also runs on cancellation, so the spinner never sticks.
            _catalog.update { if (it.isRefreshing) it.copy(isRefreshing = false) else it }
            refreshMutex.unlock()
        }
    }

    /**
     * Saves (or re-saves) [flight]. Saving an already-saved flight replaces its details in place,
     * keeping its original save time, so there are never duplicates. Throws if persisting fails.
     */
    suspend fun save(flight: Flight) = savedMutex.withLock {
        // Writing without having read the existing list would wipe the user's other saved flights.
        check(ensureSavedLoadedLocked()) { "Saved flights are unavailable." }
        val current = _saved.value
        val existing = current.firstOrNull { it.flight.id == flight.id }
        val updated = if (existing != null) {
            current.map { if (it.flight.id == flight.id) it.copy(flight = flight) else it }
        } else {
            listOf(SavedFlight(flight, now())) + current
        }
        savedStore.write(updated)
        _saved.value = updated
    }

    /** Removes the saved flight with [flightId]. Throws if persisting fails. */
    suspend fun remove(flightId: String) = savedMutex.withLock {
        check(ensureSavedLoadedLocked()) { "Saved flights are unavailable." }
        val current = _saved.value
        if (current.none { it.flight.id == flightId }) return@withLock
        val updated = current.filterNot { it.flight.id == flightId }
        savedStore.write(updated)
        _saved.value = updated
    }

    private fun describe(error: Throwable): String = when (error) {
        is FlightApiException -> error.message ?: "The flight server sent an unexpected response."
        else -> "Couldn't reach the flight server. Check your connection and that the local API is running."
    }
}

/** Like runCatching, but never swallows coroutine cancellation. */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
