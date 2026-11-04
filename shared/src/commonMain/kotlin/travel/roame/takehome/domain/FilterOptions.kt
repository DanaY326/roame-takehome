package travel.roame.takehome.domain

import travel.roame.takehome.model.Flight

/** The choices the filter UI should offer, derived from the full (unfiltered) catalog. */
data class FilterOptions(
    val cabins: List<String>,
    val programs: List<String>,
    val minDate: String?,
    val maxDate: String?,
    val minPoints: Int,
    val maxPoints: Int,
) {
    /** Slider step for the points filter. */
    val pointsStep: Int get() = POINTS_STEP

    companion object {
        const val POINTS_STEP = 500
        val EMPTY = FilterOptions(emptyList(), emptyList(), null, null, 0, 0)

        private val cabinRank = listOf("economy", "premium economy", "business", "first")

        fun from(flights: List<Flight>): FilterOptions {
            if (flights.isEmpty()) return EMPTY
            val cabins = flights.map { it.cabin }.distinct().sortedWith(
                compareBy<String> { cabinRank.indexOf(it.lowercase()).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    .thenBy { it },
            )
            return FilterOptions(
                cabins = cabins,
                programs = flights.map { it.program }.distinct().sorted(),
                minDate = flights.minOf { it.departureDate },
                maxDate = flights.maxOf { it.departureDate },
                minPoints = flights.minOf { it.points },
                maxPoints = flights.maxOf { it.points },
            )
        }
    }
}

enum class FilterKind { ORIGIN, DESTINATION, DATES, CABIN, MAX_POINTS, PROGRAMS }

/** A filter that currently narrows results, with a human-readable label for a removable chip. */
data class ActiveFilter(val kind: FilterKind, val label: String)

object ActiveFilters {
    fun describe(filter: FlightFilter, options: FilterOptions): List<ActiveFilter> = buildList {
        filter.originQuery.trim().takeIf { it.isNotEmpty() }?.let { add(ActiveFilter(FilterKind.ORIGIN, "From: $it")) }
        filter.destinationQuery.trim().takeIf { it.isNotEmpty() }?.let { add(ActiveFilter(FilterKind.DESTINATION, "To: $it")) }
        if (isDateRangeActive(filter, options)) {
            val start = filter.startDate ?: options.minDate
            val end = filter.endDate ?: options.maxDate
            val label = when {
                start != null && end != null -> FlightDates.formatRange(start, end)
                start != null -> "From ${FlightDates.formatShort(start)}"
                end != null -> "Until ${FlightDates.formatShort(end)}"
                else -> "Dates"
            }
            add(ActiveFilter(FilterKind.DATES, label))
        }
        filter.cabin?.let { add(ActiveFilter(FilterKind.CABIN, it)) }
        filter.maxPoints?.let { add(ActiveFilter(FilterKind.MAX_POINTS, "≤ ${Formatting.points(it)}")) }
        if (filter.programs.isNotEmpty()) {
            val label = if (filter.programs.size <= 2) filter.programs.sorted().joinToString(", ") else "${filter.programs.size} programs"
            add(ActiveFilter(FilterKind.PROGRAMS, label))
        }
    }

    /** A date range equal to (or wider than) the full catalog range is not narrowing anything. */
    fun isDateRangeActive(filter: FlightFilter, options: FilterOptions): Boolean {
        val startActive = filter.startDate != null && (options.minDate == null || filter.startDate > options.minDate)
        val endActive = filter.endDate != null && (options.maxDate == null || filter.endDate < options.maxDate)
        return startActive || endActive
    }
}
