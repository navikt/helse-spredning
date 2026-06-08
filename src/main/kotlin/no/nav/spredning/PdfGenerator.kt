package no.nav.spredning

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import java.io.ByteArrayOutputStream

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

    private fun lagHtml(tittel: String, melding: String): String = """
        <!DOCTYPE html>
        <html lang="no">
        <head>
          <meta charset="UTF-8"/>
          <style>
            body { font-family: Arial, sans-serif; font-size: 12pt; margin: 40px; color: #222; }
            h1 { font-size: 16pt; }
            p { line-height: 1.6; }
          </style>
        </head>
        <body>
          <h1>$tittel</h1>
          $melding
        </body>
        </html>
    """.trimIndent()
}
