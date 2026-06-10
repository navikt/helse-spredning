package no.nav.spredning

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*

private val objectMapper = jacksonObjectMapper()

data class OpprettJournalpostRequest(
    val journalposttype: String = "UTGAAENDE",
    val tema: String = "SYK",
    val tittel: String,
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val sak: Sak = Sak(),
    val dokumenter: List<Dokument>,
    val eksternReferanseId: String,
)

data class AvsenderMottaker(
    val id: String,
    val idType: String = "FNR",
)

data class Bruker(
    val id: String,
    val idType: String = "FNR",
)

data class Sak(
    val sakstype: String = "GENERELL_SAK",
)

data class Dokument(
    val tittel: String,
    val brevkode: String,
    val dokumentKategori: String = "IB", // https://github.com/navikt/dokarkiv/blob/3b7c8c116429d13c2b1293796e504129193646f4/core/src/main/java/no/nav/dokarkiv/core/domain/codes/DokumentKategoriCode.java#L51
    val dokumentvarianter: List<DokumentVariant>,
)

data class DokumentVariant(
    val filtype: String = "PDFA",
    val variantformat: String = "ARKIV",
    val fysiskDokument: String, // base64-encoded PDF
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpprettJournalpostResponse(
    val journalpostId: String,
    val journalpostferdigstilt: Boolean,
)

class DokarkivKlient(
    private val dokarkivUrl: String = requireEnv("DOKARKIV_URL"),
    private val dokarkivTarget: String = requireEnv("DOKARKIV_TARGET"),
    private val tokenKlient: NaisTokenKlient,
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun journalfør(mottaker: Mottaker, pdfBytes: ByteArray, tittel: String, brevkode: String): String {
        val base64Pdf = Base64.getEncoder().encodeToString(pdfBytes)

        val request = OpprettJournalpostRequest(
            avsenderMottaker = AvsenderMottaker(id = mottaker.fnr),
            bruker = Bruker(id = mottaker.fnr),
            tittel = tittel,
            eksternReferanseId = "spredning-${mottaker.fnr}",
            dokumenter = listOf(
                Dokument(
                    tittel = tittel,
                    brevkode = brevkode,
                    dokumentvarianter = listOf(
                        DokumentVariant(fysiskDokument = base64Pdf),
                    ),
                ),
            ),
        )

        val token = tokenKlient.hentToken(dokarkivTarget)
        val body = objectMapper.writeValueAsString(request)

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$dokarkivUrl/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Journalføring feilet, statuskode${response.statusCode()}, respons:\n${response.body()}"
        }
        val respBody = objectMapper.readValue<OpprettJournalpostResponse>(response.body())
        check(respBody.journalpostferdigstilt) {
            "Journalpost ${respBody.journalpostId} ble ikke ferdigstilt — kan ikke distribueres"
        }
        return respBody.journalpostId
    }
}
