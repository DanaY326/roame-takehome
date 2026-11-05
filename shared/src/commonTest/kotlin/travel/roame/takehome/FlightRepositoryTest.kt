package travel.roame.takehome

import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.FlightApiException
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.model.CachedFlights
import travel.roame.takehome.model.SavedFlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlightRepositoryTest {
    private val api = FakeApi(listOf(flight("b", date = "2027-03-01"), flight("a", date = "2027-02-01")))
    private val cache = FakeStore<CachedFlights>()
    private val savedStore = FakeStore<List<SavedFlight>>()
    private var clock = 1_000L

    private fun repository() = FlightRepository(api, cache, savedStore, now = { clock })

    @Test
    fun successfulRefreshIsSortedAndCached() = runTest {
        val repo = repository()
        assertTrue(repo.refresh())
        assertEquals(listOf("a", "b"), repo.catalog.value.flights?.map { it.id })
        assertEquals(listOf("a", "b"), cache.value?.flights?.map { it.id })
        assertEquals(1_000L, repo.catalog.value.fetchedAtEpochMs)
        assertNull(repo.catalog.value.refreshError)
    }

    @Test
    fun cacheSurvivesRestartAndFailedRefreshKeepsIt() = runTest {
        repository().refresh()

        // "Reopen the app" with the API down.
        api.error = IllegalStateException("connection refused")
        val reopened = repository()
        reopened.loadLocal()
        assertEquals(listOf("a", "b"), reopened.catalog.value.flights?.map { it.id })

        assertFalse(reopened.refresh())
        val state = reopened.catalog.value
        assertEquals(listOf("a", "b"), state.flights?.map { it.id }, "existing data is preserved")
        assertNotNull(state.refreshError, "stale indication")
        assertFalse(state.isRefreshing)
        assertEquals(1_000L, state.fetchedAtEpochMs)
    }

    @Test
    fun failureWithoutCacheHasNoData() = runTest {
        api.error = FlightApiException("The flight server returned an error (HTTP 500).")
        val repo = repository()
        repo.loadLocal()
        repo.refresh()
        assertNull(repo.catalog.value.flights)
        assertEquals("The flight server returned an error (HTTP 500).", repo.catalog.value.refreshError)
        assertTrue(repo.catalog.value.hasCompletedInitialLoad)
    }

    @Test
    fun emptySuccessIsDistinctFromFailure() = runTest {
        api.flights = emptyList()
        val repo = repository()
        assertTrue(repo.refresh())
        assertEquals(emptyList(), repo.catalog.value.flights)
        assertNull(repo.catalog.value.refreshError)
    }

    @Test
    fun cacheWriteFailureStillShowsFreshData() = runTest {
        cache.failWrites = true
        val repo = repository()
        assertTrue(repo.refresh())
        assertEquals(2, repo.catalog.value.flights?.size)
    }

    @Test
    fun unreadableCacheIsTreatedAsEmpty() = runTest {
        cache.failReads = true
        val repo = repository()
        repo.loadLocal()
        assertNull(repo.catalog.value.flights)
        assertTrue(repo.refresh())
        assertEquals(2, repo.catalog.value.flights?.size)
    }

    @Test
    fun savingTheSameFlightAgainUpdatesWithoutDuplicates() = runTest {
        val repo = repository()
        repo.loadLocal()
        repo.save(flight("a", points = 30_000))
        clock = 2_000L
        repo.save(flight("b"))
        clock = 3_000L
        repo.save(flight("a", points = 20_000))

        val saved = repo.saved.value
        assertEquals(listOf("b", "a"), saved.map { it.flight.id })
        assertEquals(20_000, saved.single { it.flight.id == "a" }.flight.points)
        assertEquals(1_000L, saved.single { it.flight.id == "a" }.savedAtEpochMs, "keeps original save time")
        assertEquals(saved, savedStore.value)
    }

    @Test
    fun savedFlightsSurviveRestartAndRemainWhenAbsentFromApi() = runTest {
        val repo = repository()
        repo.loadLocal()
        repo.save(flight("gone"))

        api.flights = listOf(flight("other"))
        val reopened = repository()
        reopened.loadLocal()
        reopened.refresh()
        assertEquals(listOf("gone"), reopened.saved.value.map { it.flight.id })
    }

    @Test
    fun failedSaveDoesNotLookSuccessful() = runTest {
        val repo = repository()
        repo.loadLocal()
        savedStore.failWrites = true
        assertFailsWith<IllegalStateException> { repo.save(flight("a")) }
        assertTrue(repo.saved.value.isEmpty())
    }

    @Test
    fun failedRemoveKeepsTheFlight() = runTest {
        val repo = repository()
        repo.loadLocal()
        repo.save(flight("a"))
        savedStore.failWrites = true
        assertFailsWith<IllegalStateException> { repo.remove("a") }
        assertEquals(listOf("a"), repo.saved.value.map { it.flight.id })
    }

    @Test
    fun removeWorksOffline() = runTest {
        val repo = repository()
        repo.loadLocal()
        repo.save(flight("a"))
        api.error = IllegalStateException("offline")
        repo.refresh()
        repo.remove("a")
        assertTrue(repo.saved.value.isEmpty())
        assertEquals(emptyList(), savedStore.value)
    }

    @Test
    fun saveNeverOverwritesSavedFlightsThatCouldNotBeRead() = runTest {
        savedStore.value = listOf(SavedFlight(flight("existing"), 1L))
        savedStore.failReads = true
        val repo = repository()
        repo.loadLocal()
        assertNotNull(repo.savedLoadError.value)

        assertFailsWith<IllegalStateException> { repo.save(flight("new")) }
        assertEquals(listOf("existing"), savedStore.value?.map { it.flight.id }, "nothing was clobbered")

        // Once the disk recovers, the next save reads first and keeps both.
        savedStore.failReads = false
        repo.save(flight("new"))
        assertEquals(setOf("existing", "new"), repo.saved.value.map { it.flight.id }.toSet())
        assertNull(repo.savedLoadError.value)
    }
}
