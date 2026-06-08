package no.nav.spredning

data class Mottaker(val fnr: String, val flettefelt: List<String>)

object CsvLeser {
    fun les(csvTekst: String): List<Mottaker> {
        return csvTekst.lines()
            .mapIndexed { index, linje -> index + 1 to linje }
            .filter { (_, linje) -> linje.isNotBlank() && !linje.startsWith("#") }
            .map { (linjenummer, linje) ->
                val deler = linje.split(",").map { it.trim() }
                require(deler.isNotEmpty()) { "Linje $linjenummer er tom" }
                val fnr = deler[0]
                require(fnr.length == 11 && fnr.all { it.isDigit() }) {
                    "Linje $linjenummer: ugyldig fødselsnummer '$fnr' (må være 11 siffer)"
                }
                Mottaker(fnr = fnr, flettefelt = deler.drop(1))
            }
    }
}
