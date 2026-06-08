package no.nav.spredning

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val objectMapper = jacksonObjectMapper()

data class SendRequest(
    val tittel: String,
    val brevkode: String,
    val melding: String,
    val csv: String,
) {
    fun validate() {
        if (tittel.isBlank() || melding.isBlank() || csv.isBlank()) {
            throw IllegalArgumentException("tittel, melding og csv er påkrevd")
        }
    }

    fun parseMottakere(): List<Mottaker> {
        val mottakere = CsvLeser.les(csv)
        if (mottakere.isEmpty()) {
            throw IllegalArgumentException("CSV inneholder ingen mottakere")
        }
        return mottakere
    }
}

data class SendResultat(
    val antallSendt: Int,
    val antallFeilet: Int,
    val feil: List<String>,
)

fun startServer(port: Int = 8080) {
    val tokenKlient = NaisTokenKlient()
    val dokarkivKlient = DokarkivKlient(tokenKlient = tokenKlient)
    val dokdistKlient = DokdistFordelingKlient(tokenKlient = tokenKlient)

    embeddedServer(CIO, port = port) {
        install(ContentNegotiation) { jackson() }
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, cause.message ?: "Ugyldig forespørsel")
            }
        }

        routing {
            get("/isAlive") { call.respond(HttpStatusCode.OK, "OK") }
            get("/isReady") { call.respond(HttpStatusCode.OK, "OK") }

            get("/") {
                val html = this::class.java.getResourceAsStream("/static/index.html")!!.readBytes()
                call.respondBytes(html, ContentType.Text.Html)
            }

            post("/forhåndsvis") {
                val req = call.receive<SendRequest>()
                req.validate()
                val mottakere = req.parseMottakere()
                val pdfBytes = PdfGenerator.generer(mottakere.first(), req.tittel, req.melding)
                call.respondBytes(pdfBytes, ContentType.Application.Pdf)
            }

            post("/send") {
                val req = call.receive<SendRequest>()
                req.validate()
                val mottakere = req.parseMottakere()

                var antallSendt = 0
                val feil = mutableListOf<String>()

                for (mottaker in mottakere) {
                    val maskertFnr = mottaker.fnr.take(6) + "*****"
                    try {
                        val pdfBytes = PdfGenerator.generer(mottaker, req.tittel, req.melding)
                        val journalpostId = dokarkivKlient.journalfør(mottaker, pdfBytes, req.tittel, req.brevkode)
                        val bestillingsId = dokdistKlient.distribuer(journalpostId)
                        antallSendt++
                        log.info("Sendt brev til {} — journalpostId={} bestillingsId={}", maskertFnr, journalpostId, bestillingsId)
                    } catch (e: MottakerErDødException) {
                        log.warn("Hopper over {} — mottaker er registrert død: {}", maskertFnr, e.message)
                        feil.add("$maskertFnr: mottaker er registrert død")
                    } catch (e: Exception) {
                        log.error("Feil ved sending til {}: {}", maskertFnr, e.message, e)
                        feil.add("$maskertFnr: ${e.message}")
                    }
                }

                val resultat = SendResultat(
                    antallSendt = antallSendt,
                    antallFeilet = feil.size,
                    feil = feil,
                )
                call.respond(HttpStatusCode.OK, objectMapper.writeValueAsString(resultat))
            }
        }
    }.start(wait = true)
}
