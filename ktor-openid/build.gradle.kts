plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "org.jetbrains.demo"
version = "1.0.0"

dependencies {
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.sessions)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.client.contentNegotiation)
    implementation(ktorLibs.client.cio)

    testImplementation(ktorLibs.server.cio)
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mock.oauth2.server)
}

