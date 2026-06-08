package no.nav.spredning

internal fun requireEnv(name: String): String =
    System.getenv(name) ?: throw IllegalStateException("Påkrevd miljøvariabel mangler: $name")
