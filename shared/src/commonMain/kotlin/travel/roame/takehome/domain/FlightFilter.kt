package travel.roame.takehome.domain

import travel.roame.takehome.model.Flight

/**
 * The user's discovery criteria. Every field uses "empty means everything":
 * blank queries, null bounds, a null cabin and an empty program set all match all flights.
 */
data class FlightFilter(
    val originQuery: String = "",
    val destinationQuery: String = "",
    /** Inclusive ISO start date, or null for no lower bound. */
    val startDate: String? = null,
    /** Inclusive ISO end date, or null for no upper bound. */
    val endDate: String? = null,
    val cabin: String? = null,
    /** Inclusive points ceiling, or null for no limit. */
    val maxPoints: Int? = null,
    /** A flight matches if its program is any of these. */
    val programs: Set<String> = emptySet(),
)

object FlightFiltering {

    /**
     * Applies [filter] to [flights], preserving input order. Different criteria are combined with AND;
     * the program set is matched with OR.
     */
    fun apply(flights: List<Flight>, filter: FlightFilter): List<Flight> {
        val origin = normalize(filter.originQuery)
        val destination = normalize(filter.destinationQuery)
        val cabin = filter.cabin?.let(::normalize)?.takeIf { it.isNotEmpty() }
        return flights.filter { flight ->
            matchesAirport(origin, flight.origin, flight.originCity) &&
                matchesAirport(destination, flight.destination, flight.destinationCity) &&
                (filter.startDate == null || flight.departureDate >= filter.startDate) &&
                (filter.endDate == null || flight.departureDate <= filter.endDate) &&
                (cabin == null || normalize(flight.cabin) == cabin) &&
                (filter.maxPoints == null || flight.points <= filter.maxPoints) &&
                (filter.programs.isEmpty() || flight.program in filter.programs)
        }
    }

    /**
     * Matches a search query against an airport. Case and surrounding whitespace are ignored.
     *
     * A query matches when the airport code starts with it ("bo" → BOS), the city starts with it
     * ("new y" → New York), or any word of the city starts with it ("york" → New York).
     * Prefix matching keeps short queries predictable: "la" finds LAX but not "Atlanta".
     */
    fun matchesAirport(query: String, code: String, city: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        val c = normalize(city)
        return normalize(code).startsWith(q) ||
            c.startsWith(q) ||
            c.split(' ', '-').any { it.startsWith(q) }
    }

    internal fun normalize(value: String): String = value.trim().lowercase()

    /** Departure date, then cheapest first, then id — a stable, deterministic order for both apps. */
    val defaultOrder: Comparator<Flight> =
        compareBy<Flight> { it.departureDate }.thenBy { it.points }.thenBy { it.id }
}
