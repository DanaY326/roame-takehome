package travel.roame.takehome.presentation

import travel.roame.takehome.domain.ActiveFilter
import travel.roame.takehome.domain.DestinationMarker
import travel.roame.takehome.domain.FilterOptions
import travel.roame.takehome.domain.FlightFilter
import travel.roame.takehome.model.Flight

enum class CatalogStatus {
    /** No data yet; the first load is in progress. */
    LOADING,

    /** We have a catalog to show (possibly cached, possibly empty). */
    READY,

    /** We have no data at all and the last attempt to fetch failed. Show an error with retry. */
    ERROR,
}

data class SavedFlightItem(
    val flight: Flight,
    val savedAtEpochMs: Long,
    /** False when the flight is not in the latest catalog (it's still kept and shown). */
    val isInLatestCatalog: Boolean,
)

/** A one-off message for a snackbar / alert. Dismiss it with [FlightExplorer.dismissMessage]. */
data class UserMessage(val id: Long, val text: String)

/** Everything both apps render, computed in one place so the list, map and saved views always agree. */
data class ExplorerState(
    val status: CatalogStatus,
    val isRefreshing: Boolean,
    /** True when we're showing data, but the latest refresh failed (offline / stale). */
    val isStale: Boolean,
    /** Error to show: the full-screen error for [CatalogStatus.ERROR], or the stale-data banner. */
    val errorMessage: String?,
    /** When the data on screen was fetched, or null if never. */
    val lastUpdatedEpochMs: Long?,
    /** Size of the whole catalog. 0 with [CatalogStatus.READY] means the API successfully returned no flights. */
    val totalFlightCount: Int,
    val filter: FlightFilter,
    val options: FilterOptions,
    /** Flights matching [filter], in display order. */
    val results: List<Flight>,
    /** [results] grouped by destination airport. */
    val markers: List<DestinationMarker>,
    val activeFilters: List<ActiveFilter>,
    val selectedFlight: Flight?,
    val saved: List<SavedFlightItem>,
    val savedIds: Set<String>,
    /** Flights with a save/remove in flight; disable their buttons. */
    val pendingSaveIds: Set<String>,
    val savedError: String?,
    val message: UserMessage?,
) {
    val resultCount: Int get() = results.size
    val hasActiveFilters: Boolean get() = activeFilters.isNotEmpty()

    fun isSaved(flightId: String): Boolean = flightId in savedIds
    fun isPending(flightId: String): Boolean = flightId in pendingSaveIds

    companion object {
        val INITIAL = ExplorerState(
            status = CatalogStatus.LOADING,
            isRefreshing = false,
            isStale = false,
            errorMessage = null,
            lastUpdatedEpochMs = null,
            totalFlightCount = 0,
            filter = FlightFilter(),
            options = FilterOptions.EMPTY,
            results = emptyList(),
            markers = emptyList(),
            activeFilters = emptyList(),
            selectedFlight = null,
            saved = emptyList(),
            savedIds = emptySet(),
            pendingSaveIds = emptySet(),
            savedError = null,
            message = null,
        )
    }
}
