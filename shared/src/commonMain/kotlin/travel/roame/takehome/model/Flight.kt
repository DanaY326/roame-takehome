package travel.roame.takehome.model

import kotlinx.serialization.Serializable

/** A single dated award flight, exactly as delivered by `GET /flights`. */
@Serializable
data class Flight(
    val id: String,
    val origin: String,
    val destination: String,
    /** ISO-8601 calendar date (`yyyy-MM-dd`); ISO dates sort and compare correctly as strings. */
    val departureDate: String,
    val program: String,
    val points: Int,
    val cabin: String,
    val originCity: String,
    val destinationCity: String,
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
)

/** The JSON envelope returned by the API: `{ "flights": [...] }`. */
@Serializable
data class FlightsEnvelope(val flights: List<Flight>)

/** A flight the user saved, stored as a full snapshot so it survives independently of the API. */
@Serializable
data class SavedFlight(
    val flight: Flight,
    val savedAtEpochMs: Long,
)

/** The last successful API response, persisted for offline use. */
@Serializable
data class CachedFlights(
    val fetchedAtEpochMs: Long,
    val flights: List<Flight>,
)
