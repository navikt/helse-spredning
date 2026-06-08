plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "no.nav.spredning"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.1.3"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("org.apache.pdfbox:pdfbox:2.0.31")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("ch.qos.logback:logback-classic:1.5.33")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
}

application {
    mainClass.set("no.nav.spredning.MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "no.nav.spredning.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
