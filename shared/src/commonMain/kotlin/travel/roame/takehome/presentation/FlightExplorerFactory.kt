package travel.roame.takehome.presentation

import kotlinx.io.files.Path
import kotlinx.serialization.builtins.ListSerializer
import travel.roame.takehome.data.FileDocumentStore
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.data.KtorFlightApi
import travel.roame.takehome.model.CachedFlights
import travel.roame.takehome.model.SavedFlight
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Wires the production object graph. Each app calls this once and keeps the instance for the app's lifetime. */
object FlightExplorerFactory {
    /**
     * @param storageDirectory an app-private directory for the cache and saved flights.
     * @param baseUrl the local API, e.g. `http://localhost:8080` (iOS simulator) or `http://10.0.2.2:8080` (Android emulator).
     */
    @OptIn(ExperimentalTime::class)
    fun create(storageDirectory: String, baseUrl: String): FlightExplorer {
        val dir = Path(storageDirectory)
        val repository = FlightRepository(
            api = KtorFlightApi(baseUrl),
            cacheStore = FileDocumentStore(Path(dir, "flights_cache.json"), CachedFlights.serializer()),
            savedStore = FileDocumentStore(Path(dir, "saved_flights.json"), ListSerializer(SavedFlight.serializer())),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
        return FlightExplorer(repository)
    }
}
