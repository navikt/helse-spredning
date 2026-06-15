package no.nav.spredning

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

object PdfGenerator {
    fun generer(mottaker: Mottaker, tittel: String, melding: String): ByteArray {
        val meldingMedFlettefelt = substituerFlettefelt(melding, mottaker.flettefelt)
        val html = lagHtml(tittel, meldingMedFlettefelt)
        val outputStream = ByteArrayOutputStream()
        PdfRendererBuilder()
            .withHtmlContent(html, null)
            .toStream(outputStream)
            .run()
        return outputStream.toByteArray()
    }

    private fun substituerFlettefelt(melding: String, flettefelt: List<String>): String {
        var resultat = melding
        flettefelt.forEachIndexed { index, verdi ->
            resultat = resultat.replace("\${${index + 1}}", verdi)
        }
        return resultat
    }

    private fun lagHtml(tittel: String, melding: String): String = lesHtmlTemplate()
        .replace("\$tittel", tittel)
        .replace("\$melding", melding)
        .replace("\$logo", logo)

    private fun lesHtmlTemplate(): String {
        val localFile = Path.of("src/main/resources/static/pdf.htm")
        return if (Files.exists(localFile)) localFile.toFile().readText()
        else javaClass.getResource("/static/pdf.htm")!!.readText()
    }

    private val logo: String = javaClass.getResource("/assets/Nav-logo.png")!!.readBytes()
        .let { Base64.getEncoder().encodeToString(it) }
}
