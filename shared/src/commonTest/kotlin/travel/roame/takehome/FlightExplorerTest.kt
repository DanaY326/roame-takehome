package travel.roame.takehome

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.domain.FilterKind
import travel.roame.takehome.model.CachedFlights
import travel.roame.takehome.model.SavedFlight
import travel.roame.takehome.presentation.CatalogStatus
import travel.roame.takehome.presentation.FlightExplorer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlightExplorerTest {
    private val api = FakeApi(
        listOf(
            flight("bos-cdg", origin = "BOS", destination = "CDG", date = "2027-02-01", points = 25_000, program = "Avios"),
            flight("jfk-lhr", origin = "JFK", destination = "LHR", date = "2027-03-01", points = 40_000, program = "Aeroplan"),
            flight("bos-lhr", origin = "BOS", destination = "LHR", date = "2027-04-01", points = 30_000, program = "Avios"),
        ),
    )
    private val cache = FakeStore<CachedFlights>()
    private val savedStore = FakeStore<List<SavedFlight>>()

    private fun TestScope.explorer(): FlightExplorer {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FlightRepository(api, cache, savedStore, now = { 42L })
        return FlightExplorer(repository, mainDispatcher = dispatcher, computeDispatcher = dispatcher)
    }

    @Test
    fun startsWithEverythingVisibleAndFullDateRange() = runTest {
        val explorer = explorer()
        assertEquals(CatalogStatus.LOADING, explorer.state.value.status)
        explorer.start()
        advanceUntilIdle()

        val state = explorer.state.value
        assertEquals(CatalogStatus.READY, state.status)
        assertEquals(3, state.resultCount)
        assertEquals("2027-02-01", state.options.minDate)
        assertEquals("2027-04-01", state.options.maxDate)
        assertFalse(state.hasActiveFilters)
        assertEquals(listOf("CDG" to 1, "LHR" to 2), state.markers.map { it.airportCode to it.flightCount })
        assertEquals(30_000, state.markers.last().lowestPoints)
        explorer.close()
    }

    @Test
    fun listAndMapReflectTheLatestFilter() = runTest {
        val explorer = explorer()
        explorer.start()
        advanceUntilIdle()

        explorer.setOriginQuery("b")
        explorer.setOriginQuery("bo")
        explorer.setOriginQuery("bos")
        explorer.toggleProgram("Avios")
        explorer.setMaxPoints(25_000)
        advanceUntilIdle()

        val state = explorer.state.value
        assertEquals(listOf("bos-cdg"), state.results.map { it.id })
        assertEquals(listOf("CDG"), state.markers.map { it.airportCode })
        assertEquals(
            listOf(FilterKind.ORIGIN, FilterKind.MAX_POINTS, FilterKind.PROGRAMS),
            state.activeFilters.map { it.kind },
        )

        explorer.resetFilters()
        advanceUntilIdle()
        assertEquals(3, explorer.state.value.resultCount)
        explorer.close()
    }

    @Test
    fun selectingTheFullRangeOrMaxPointsIsNotAFilter() = runTest {
        val explorer = explorer()
        explorer.start()
        advanceUntilIdle()

        explorer.setDateRange("2027-04-01", "2027-02-01") // reversed on purpose
        explorer.setMaxPoints(40_000)
        advanceUntilIdle()
        assertNull(explorer.state.value.filter.startDate)
        assertNull(explorer.state.value.filter.endDate)
        assertNull(explorer.state.value.filter.maxPoints)

        explorer.setDateRange("2027-03-01", "2027-02-01")
        advanceUntilIdle()
        assertEquals("2027-03-01", explorer.state.value.filter.endDate)
        assertEquals(listOf("bos-cdg", "jfk-lhr"), explorer.state.value.results.map { it.id })
        explorer.close()
    }

    @Test
    fun selectionIsPreservedAndClearedWhenFilteredOut() = runTest {
        val explorer = explorer()
        explorer.start()
        advanceUntilIdle()

        explorer.select("jfk-lhr")
        advanceUntilIdle()
        assertEquals("jfk-lhr", explorer.state.value.selectedFlight?.id)

        explorer.setDestinationQuery("london") // still included
        advanceUntilIdle()
        assertEquals("jfk-lhr", explorer.state.value.selectedFlight?.id)

        explorer.setOriginQuery("bos") // excluded
        advanceUntilIdle()
        assertNull(explorer.state.value.selectedFlight)

        explorer.setOriginQuery("") // relaxing the filter doesn't bring it back
        advanceUntilIdle()
        assertNull(explorer.state.value.selectedFlight)

        explorer.select("does-not-exist")
        advanceUntilIdle()
        assertNull(explorer.state.value.selectedFlight)
        explorer.close()
    }

    @Test
    fun noCacheAndFailedFetchIsAnErrorThatCanBeRetried() = runTest {
        api.error = IllegalStateException("offline")
        val explorer = explorer()
        explorer.start()
        advanceUntilIdle()
        assertEquals(CatalogStatus.ERROR, explorer.state.value.status)
        assertNotNull(explorer.state.value.errorMessage)

        api.error = null
        explorer.refresh()
        advanceUntilIdle()
        assertEquals(CatalogStatus.READY, explorer.state.value.status)
        assertFalse(explorer.state.value.isStale)
        assertNull(explorer.state.value.errorMessage)
        explorer.close()
    }

    @Test
    fun retryShowsLoadingWhileInFlight() = runTest {
        api.error = IllegalStateException("offline")
        val explorer = explorer()
        explorer.start()
        advanceUntilIdle()

        api.error = null
        api.gate = CompletableDeferred()
        explorer.refresh()
        advanceUntilIdle()
        assertEquals(CatalogStatus.LOADING, explorer.state.value.status)
        api.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(CatalogStatus.READY, explorer.state.value.status)
        explorer.close()
    }

    @Test
    fun failedRefreshWithCacheIsStale() = runTest {
        explorer().apply { start(); advanceUntilIdle(); close() }

        api.error = IllegalStateException("offline")
        val reopened = explorer()
        reopened.start()
        advanceUntilIdle()
        val state = reopened.state.value
        assertEquals(CatalogStatus.READY, state.status)
        assertTrue(state.isStale)
        assertEquals(3, state.resultCount)

        reopened.setOriginQuery("jfk") // searching cached results offline
        advanceUntilIdle()
        assertEquals(listOf("jfk-lhr"), reopened.state.value.results.map { it.id })
        reopened.close()
    }

    @Test
    fun emptyCatalogIsReadyNotError() = runTest {
        api.flights = emptyList()
        val explorer = explorer()
        explorer.start()
        advanceUntilIdle()
        assertEquals(CatalogStatus.READY, explorer.state.value.status)
        assertEquals(0, explorer.state.value.totalFlightCount)
        assertNull(explorer.state.value.errorMessage)
        explorer.close()
    }

    @Test
    fun savingIsReflectedEverywhereAndFailuresAreReported() = runTest {
        val explorer = explorer()
        explorer.start()
        advanceUntilIdle()

        val target = explorer.state.value.results.first()
        explorer.toggleSaved(target)
        advanceUntilIdle()
        assertTrue(explorer.state.value.isSaved(target.id))
        assertFalse(explorer.state.value.isPending(target.id))
        assertTrue(explorer.state.value.saved.single().isInLatestCatalog)

        savedStore.failWrites = true
        explorer.toggleSaved(target)
        advanceUntilIdle()
        val state = explorer.state.value
        assertTrue(state.isSaved(target.id), "failed remove leaves it saved")
        val message = assertNotNull(state.message)
        assertTrue(message.text.contains("remove"))

        explorer.dismissMessage(message.id)
        advanceUntilIdle()
        assertNull(explorer.state.value.message)

        // Saved view is independent of discovery filters.
        explorer.setOriginQuery("zzz")
        advanceUntilIdle()
        assertEquals(0, explorer.state.value.resultCount)
        assertEquals(1, explorer.state.value.saved.size)
        explorer.close()
    }

    @Test
    fun savedFlightMissingFromLatestCatalogIsFlagged() = runTest {
        savedStore.value = listOf(SavedFlight(flight("old"), 1L))
        val explorer = explorer()
        explorer.start()
        advanceUntilIdle()
        assertFalse(explorer.state.value.saved.single().isInLatestCatalog)
        explorer.close()
    }
}
