group = "no.nav.spredning"

plugins {
    alias(libs.plugins.sykepenger.deployable)
}

sykepengerDeployable {
    mainClass = "no.nav.spredning.MainKt"
}

dependencies {
    implementation(libs.openhtmltopdf.pdfbox)
    implementation(libs.pdfbox)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.bundles.ktor.server)
}
