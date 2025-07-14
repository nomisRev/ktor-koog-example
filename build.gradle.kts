plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.testcontainers:testcontainers:1.21.3")
}

kotlin {
    jvmToolchain(17)
    compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    compilerOptions.freeCompilerArgs.add("-Xannotation-default-target=param-property")
}
