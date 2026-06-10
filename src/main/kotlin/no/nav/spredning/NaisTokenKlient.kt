package no.nav.spredning

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val objectMapper = jacksonObjectMapper()

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaisToken(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long,
)

class NaisTokenKlient(
    private val tokenEndpoint: String = requireEnv("NAIS_TOKEN_ENDPOINT"),
) {
    private val httpClient = HttpClient.newHttpClient()

    fun hentToken(target: String): NaisToken {
        val body = """{"identity_provider":"entra_id","target":"$target"}"""

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Nais token-forespørsel feilet: ${response.statusCode()} ${response.body()}"
        }
        return objectMapper.readValue(response.body())
    }
}
