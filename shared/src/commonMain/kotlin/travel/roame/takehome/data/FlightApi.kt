package travel.roame.takehome.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightsEnvelope

interface FlightApi {
    /** Fetches the full flight catalog. Throws on any transport, HTTP or decoding failure. */
    suspend fun fetchFlights(): List<Flight>
}

/** The server answered, but not with a usable flight list. */
class FlightApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal val SharedJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

class KtorFlightApi(
    private val baseUrl: String,
    private val client: HttpClient = defaultHttpClient(),
) : FlightApi {

    override suspend fun fetchFlights(): List<Flight> {
        val response = client.get("${baseUrl.trimEnd('/')}/flights")
        if (!response.status.isSuccess()) {
            throw FlightApiException("The flight server returned an error (HTTP ${response.status.value}).")
        }
        val body = response.bodyAsText()
        return try {
            SharedJson.decodeFromString(FlightsEnvelope.serializer(), body).flights
        } catch (e: SerializationException) {
            throw FlightApiException("The flight server sent data we couldn't read.", e)
        } catch (e: IllegalArgumentException) {
            throw FlightApiException("The flight server sent data we couldn't read.", e)
        }
    }

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }
}
