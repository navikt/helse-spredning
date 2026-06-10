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

import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.util.Base64

private val logger = LoggerFactory.getLogger("spredning.Server")
private val TEAM_LOG = MarkerFactory.getMarker("TEAM_LOGS")

private val objectMapper = jacksonObjectMapper()

private fun navIdentFraToken(authHeader: String): String {
    val payload = authHeader.removePrefix("Bearer ").split(".")[1]
    val json = String(Base64.getUrlDecoder().decode(payload))
    return objectMapper.readTree(json).get("NAVident")!!.asText()
}

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

                val authorizationHeader = call.request.header("Authorization")!!
                val navIdent = navIdentFraToken(authorizationHeader)
                logger.debug("Forbereder brevsending til ${mottakere.size} mottaker(e), NAVIdent: $navIdent")

                var antallSendt = 0
                val feil = mutableListOf<String>()

                for (mottaker in mottakere) {
                    try {
                        val pdfBytes = PdfGenerator.generer(mottaker, req.tittel, req.melding)
                        logger.debug(TEAM_LOG, "Journalfører dokument for ${mottaker.fnr}")
                        val journalpostId = dokarkivKlient.journalfør(mottaker, pdfBytes, req.tittel, req.brevkode)
                        logger.debug(TEAM_LOG, "Ber om distribusjon av dokument for ${mottaker.fnr}")
                        val bestillingsId = dokdistKlient.distribuer(journalpostId)
                        antallSendt++
                        logger.info(TEAM_LOG, "Sendt brev til {} av {} — journalpostId={} bestillingsId={}. $antallSendt av ${mottakere.size} sendt.", mottaker.fnr, navIdent, journalpostId, bestillingsId)
                    } catch (e: MottakerErDødException) {
                        logger.warn(TEAM_LOG, "Feilet sending til {} av {} — mottaker er registrert død: {}", mottaker.fnr, navIdent, e.message)
                        feil.add("${mottaker.fnr}: mottaker er registrert død")
                    } catch (e: Exception) {
                        logger.error("Sending av brev feilet, se team logs for detaljer")
                        logger.error(TEAM_LOG, "Sending av brev til fødselsnummer ${mottaker.fnr} feilet: ${e.message}", e)
                        feil.add("${mottaker.fnr}: ${e.message}")
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
