plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("io.ktor.plugin") version "3.2.0"
}

group = "com.example"
version = "0.0.1"

application.mainClass = "com.example.ApplicationKt"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.koog)
    implementation(libs.mcp)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.sse)
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.testcontainers:testcontainers:1.21.3")
}

kotlin {
    jvmToolchain(17)
}
