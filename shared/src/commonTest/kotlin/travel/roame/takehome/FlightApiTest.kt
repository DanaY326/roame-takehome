package travel.roame.takehome

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.FlightApiException
import travel.roame.takehome.data.KtorFlightApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlightApiTest {
    private fun api(status: HttpStatusCode, body: String, onPath: (String) -> Unit = {}) = KtorFlightApi(
        baseUrl = "http://localhost:8080/",
        client = HttpClient(MockEngine { request ->
            onPath(request.url.encodedPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
    )

    private val oneFlight = """
        {"flights":[{"id":"fx-1","origin":"BOS","destination":"CDG","departureDate":"2027-02-01","program":"Avios",
        "points":25000,"cabin":"Economy","originCity":"Boston","destinationCity":"Paris","originLatitude":42.3,
        "originLongitude":-71.0,"destinationLatitude":49.0,"destinationLongitude":2.5,"futureField":true}]}
    """.trimIndent()

    @Test
    fun decodesEnvelopeFromFlightsEndpoint() = runTest {
        var path = ""
        val flights = api(HttpStatusCode.OK, oneFlight) { path = it }.fetchFlights()
        assertEquals("/flights", path)
        assertEquals("fx-1", flights.single().id)
        assertEquals(25_000, flights.single().points)
    }

    @Test
    fun emptyEnvelopeIsASuccessfulEmptyList() = runTest {
        assertTrue(api(HttpStatusCode.OK, """{"flights":[]}""").fetchFlights().isEmpty())
    }

    @Test
    fun httpErrorsFail() = runTest {
        assertFailsWith<FlightApiException> { api(HttpStatusCode.InternalServerError, "oops").fetchFlights() }
    }

    @Test
    fun malformedBodiesFail() = runTest {
        assertFailsWith<FlightApiException> { api(HttpStatusCode.OK, "<html>").fetchFlights() }
        assertFailsWith<FlightApiException> { api(HttpStatusCode.OK, """{"items":[]}""").fetchFlights() }
    }
}
