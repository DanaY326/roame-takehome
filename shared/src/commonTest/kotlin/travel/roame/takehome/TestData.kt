package travel.roame.takehome

import kotlinx.coroutines.CompletableDeferred
import travel.roame.takehome.data.DocumentStore
import travel.roame.takehome.data.FlightApi
import travel.roame.takehome.model.Flight

fun flight(
    id: String,
    origin: String = "BOS",
    destination: String = "CDG",
    date: String = "2027-02-10",
    program: String = "Avios",
    points: Int = 25_000,
    cabin: String = "Economy",
    originCity: String = cityFor(origin),
    destinationCity: String = cityFor(destination),
) = Flight(
    id = id, origin = origin, destination = destination, departureDate = date, program = program,
    points = points, cabin = cabin, originCity = originCity, destinationCity = destinationCity,
    originLatitude = 42.36, originLongitude = -71.0, destinationLatitude = 49.0, destinationLongitude = 2.55,
)

private fun cityFor(code: String) = when (code) {
    "BOS" -> "Boston"
    "JFK" -> "New York"
    "LAX" -> "Los Angeles"
    "CDG" -> "Paris"
    "LHR" -> "London"
    "HND" -> "Tokyo"
    "NRT" -> "Tokyo"
    else -> code
}

/** Scriptable API: returns [flights] or throws [error]; can optionally wait on [gate]. */
class FakeApi(var flights: List<Flight> = emptyList()) : FlightApi {
    var error: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0

    override suspend fun fetchFlights(): List<Flight> {
        calls++
        gate?.await()
        error?.let { throw it }
        return flights
    }
}

/** In-memory store whose reads and writes can be made to fail. Survives "app restarts" by being reused. */
class FakeStore<T>(var value: T? = null) : DocumentStore<T> {
    var failReads = false
    var failWrites = false
    var writes = 0

    override suspend fun read(): T? {
        if (failReads) throw IllegalStateException("disk read failed")
        return value
    }

    override suspend fun write(value: T) {
        if (failWrites) throw IllegalStateException("disk full")
        writes++
        this.value = value
    }
}
