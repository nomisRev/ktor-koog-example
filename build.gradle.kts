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
    implementation("ai.koog:koog-agents:0.2.1")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-sse")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.testcontainers:testcontainers:1.21.3")
}
