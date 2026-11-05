package travel.roame.takehome

import travel.roame.takehome.domain.FlightFilter
import travel.roame.takehome.domain.FlightFiltering
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlightFilteringTest {
    private val flights = listOf(
        flight("a", origin = "BOS", destination = "CDG", date = "2027-02-01", program = "Avios", points = 25_000, cabin = "Economy"),
        flight("b", origin = "JFK", destination = "LHR", date = "2027-02-15", program = "Aeroplan", points = 60_000, cabin = "Business"),
        flight("c", origin = "LAX", destination = "HND", date = "2027-03-01", program = "Flying Blue", points = 60_001, cabin = "Business"),
        flight("d", origin = "BOS", destination = "LHR", date = "2027-04-01", program = "Avios", points = 9_000, cabin = "Premium Economy"),
    )

    private fun ids(filter: FlightFilter) = FlightFiltering.apply(flights, filter).map { it.id }

    @Test
    fun emptyFilterIncludesEverything() {
        assertEquals(listOf("a", "b", "c", "d"), ids(FlightFilter()))
    }

    @Test
    fun originMatchesCodeOrCityIgnoringCaseAndWhitespace() {
        assertEquals(listOf("a", "d"), ids(FlightFilter(originQuery = "  bos ")))
        assertEquals(listOf("a", "d"), ids(FlightFilter(originQuery = "BOSTON")))
        assertEquals(listOf("b"), ids(FlightFilter(originQuery = "york")))
        assertEquals(listOf("b"), ids(FlightFilter(originQuery = "New Y")))
        assertEquals(emptyList(), ids(FlightFilter(originQuery = "xyz")))
    }

    @Test
    fun whitespaceOnlyQueryIsTreatedAsEmpty() {
        assertEquals(4, ids(FlightFilter(originQuery = "   ", destinationQuery = "\t")).size)
    }

    @Test
    fun destinationSearchIsIndependentOfOrigin() {
        assertEquals(listOf("b", "d"), ids(FlightFilter(destinationQuery = "london")))
        assertEquals(listOf("d"), ids(FlightFilter(originQuery = "bos", destinationQuery = "lhr")))
    }

    @Test
    fun airportMatchingUsesPrefixes() {
        assertTrue(FlightFiltering.matchesAirport("la", "LAX", "Los Angeles"))
        assertTrue(FlightFiltering.matchesAirport("ange", "LAX", "Los Angeles"))
        assertFalse(FlightFiltering.matchesAirport("la", "ATL", "Atlanta"))
        assertFalse(FlightFiltering.matchesAirport("os", "BOS", "Boston"))
    }

    @Test
    fun dateRangeIncludesBothBoundaries() {
        assertEquals(listOf("a", "b"), ids(FlightFilter(startDate = "2027-02-01", endDate = "2027-02-15")))
        assertEquals(listOf("b", "c"), ids(FlightFilter(startDate = "2027-02-15", endDate = "2027-03-01")))
        assertEquals(listOf("d"), ids(FlightFilter(startDate = "2027-04-01", endDate = "2027-04-01")))
    }

    @Test
    fun openEndedDateRanges() {
        assertEquals(listOf("c", "d"), ids(FlightFilter(startDate = "2027-02-16")))
        assertEquals(listOf("a"), ids(FlightFilter(endDate = "2027-02-14")))
    }

    @Test
    fun maxPointsIncludesTheLimit() {
        assertEquals(listOf("a", "b", "d"), ids(FlightFilter(maxPoints = 60_000)))
        assertEquals(listOf("a", "b", "c", "d"), ids(FlightFilter(maxPoints = 60_001)))
        assertEquals(listOf("d"), ids(FlightFilter(maxPoints = 9_000)))
        assertEquals(emptyList(), ids(FlightFilter(maxPoints = 8_999)))
    }

    @Test
    fun cabinMatchesExactlyIgnoringCase() {
        assertEquals(listOf("b", "c"), ids(FlightFilter(cabin = "business")))
        // "Economy" must not match "Premium Economy".
        assertEquals(listOf("a"), ids(FlightFilter(cabin = "Economy")))
    }

    @Test
    fun programsMatchAnySelected() {
        assertEquals(listOf("a", "d"), ids(FlightFilter(programs = setOf("Avios"))))
        assertEquals(listOf("a", "b", "d"), ids(FlightFilter(programs = setOf("Avios", "Aeroplan"))))
    }

    @Test
    fun differentFiltersCombineWithAnd() {
        val filter = FlightFilter(
            originQuery = "bos",
            programs = setOf("Avios", "Aeroplan"),
            maxPoints = 30_000,
            startDate = "2027-03-01",
        )
        assertEquals(listOf("d"), ids(filter))
        assertEquals(emptyList(), ids(filter.copy(cabin = "Business")))
    }

    @Test
    fun defaultOrderIsDateThenPointsThenId() {
        val unordered = listOf(
            flight("z", date = "2027-02-02", points = 10),
            flight("y", date = "2027-02-01", points = 20),
            flight("x", date = "2027-02-01", points = 20),
            flight("w", date = "2027-02-01", points = 5),
        )
        assertEquals(listOf("w", "x", "y", "z"), unordered.sortedWith(FlightFiltering.defaultOrder).map { it.id })
    }
}
