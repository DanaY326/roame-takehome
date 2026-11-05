package travel.roame.takehome

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.KtorFlightApi
import travel.roame.takehome.domain.FilterOptions
import travel.roame.takehome.domain.FlightDates
import travel.roame.takehome.domain.FlightFilter
import travel.roame.takehome.domain.FlightFiltering
import travel.roame.takehome.domain.MapAggregation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the real fixture through the real API decoder and the shared rules. */
class FixtureIntegrationTest {
    private val fixture = File("../fixtures/flights.json").readText()

    @Test
    fun fullFixtureDecodesAndAggregates() = runTest {
        val api = KtorFlightApi("http://localhost:8080", HttpClient(MockEngine { respond(fixture, HttpStatusCode.OK) }))
        val flights = api.fetchFlights()

        assertEquals(1000, flights.size)
        assertEquals(1000, flights.map { it.id }.toSet().size, "ids are unique")
        assertTrue(flights.all { FlightDates.isValid(it.departureDate) })

        val options = FilterOptions.from(flights)
        assertEquals("2027-02-01", options.minDate)
        assertEquals("2027-04-01", options.maxDate)
        assertEquals(listOf("Economy", "Premium Economy", "Business"), options.cabins)
        assertEquals(5, options.programs.size)

        // Default filter shows everything immediately.
        assertEquals(1000, FlightFiltering.apply(flights, FlightFilter()).size)

        val markers = MapAggregation.byDestination(flights)
        assertEquals(flights.map { it.destination }.toSet().size, markers.size)
        assertEquals(1000, markers.sumOf { it.flightCount })

        // Boundary dates are inclusive against real data.
        val firstDay = FlightFiltering.apply(flights, FlightFilter(startDate = "2027-02-01", endDate = "2027-02-01"))
        assertEquals(flights.count { it.departureDate == "2027-02-01" }, firstDay.size)
        assertTrue(firstDay.isNotEmpty())
    }
}
