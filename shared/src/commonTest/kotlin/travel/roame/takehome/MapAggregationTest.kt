package travel.roame.takehome

import travel.roame.takehome.domain.MapAggregation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapAggregationTest {
    @Test
    fun groupsByDestinationWithCountAndLowestPoints() {
        val flights = listOf(
            flight("1", origin = "BOS", destination = "LHR", points = 40_000),
            flight("2", origin = "JFK", destination = "LHR", points = 32_500),
            flight("3", origin = "LAX", destination = "CDG", points = 70_000),
            flight("4", origin = "BOS", destination = "LHR", points = 55_000),
        )
        val markers = MapAggregation.byDestination(flights)

        assertEquals(listOf("CDG", "LHR"), markers.map { it.airportCode })
        val lhr = markers.single { it.airportCode == "LHR" }
        assertEquals(3, lhr.flightCount)
        assertEquals(32_500, lhr.lowestPoints)
        assertEquals("London", lhr.city)
        assertEquals(listOf("1", "2", "4"), lhr.flights.map { it.id })
        assertEquals(1, markers.single { it.airportCode == "CDG" }.flightCount)
    }

    @Test
    fun originsDoNotCreateMarkers() {
        val markers = MapAggregation.byDestination(listOf(flight("1", origin = "BOS", destination = "HND")))
        assertEquals(listOf("HND"), markers.map { it.airportCode })
    }

    @Test
    fun emptyResultsProduceNoMarkers() {
        assertTrue(MapAggregation.byDestination(emptyList()).isEmpty())
    }
}
