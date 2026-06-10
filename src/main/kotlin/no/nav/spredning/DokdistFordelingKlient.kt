package no.nav.spredning

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val objectMapper = jacksonObjectMapper()

data class DistribuerJournalpostRequest(
    val journalpostId: String,
    val bestillendeFagsystem: String = "SPEIL",
    val dokumentProdApp: String = "spredning",
    val distribusjonstype: String = "ANNET",
    val distribusjonstidspunkt: String = "UMIDDELBART",
)

data class DistribuerJournalpostResponse(
    val bestillingsId: String,
)

class DokdistFordelingKlient(
    private val dokdistUrl: String = requireEnv("DOKDISTFORDELING_URL"),
    private val dokdistTarget: String = requireEnv("DOKDISTFORDELING_TARGET"),
    private val tokenKlient: NaisTokenKlient,
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun distribuer(journalpostId: String): String {
        val token = tokenKlient.hentToken(dokdistTarget)
        val body = objectMapper.writeValueAsString(
            DistribuerJournalpostRequest(journalpostId = journalpostId)
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$dokdistUrl/rest/v1/distribuerjournalpost"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return when (response.statusCode()) {
            200, 409 -> objectMapper.readValue<DistribuerJournalpostResponse>(response.body()).bestillingsId
            410 -> throw MottakerErDødException("Journalpost $journalpostId kan ikke distribueres (mottaker registrert død)")
            else -> error("Distribusjon feilet: ${response.statusCode()} ${response.body()}")
        }
    }
}

class MottakerErDødException(message: String) : RuntimeException(message)
