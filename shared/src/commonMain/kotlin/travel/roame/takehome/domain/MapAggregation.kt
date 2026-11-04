package travel.roame.takehome.domain

import travel.roame.takehome.model.Flight

/** One map marker: all matching flights into a single destination airport. */
data class DestinationMarker(
    val airportCode: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val flightCount: Int,
    val lowestPoints: Int,
    /** The flights behind this marker, in the same order as the filtered list. */
    val flights: List<Flight>,
)

object MapAggregation {
    /** Groups [flights] by destination airport. Markers are ordered by airport code. */
    fun byDestination(flights: List<Flight>): List<DestinationMarker> =
        flights.groupBy { it.destination }
            .map { (code, group) ->
                val first = group.first()
                DestinationMarker(
                    airportCode = code,
                    city = first.destinationCity,
                    latitude = first.destinationLatitude,
                    longitude = first.destinationLongitude,
                    flightCount = group.size,
                    lowestPoints = group.minOf { it.points },
                    flights = group,
                )
            }
            .sortedBy { it.airportCode }
}
