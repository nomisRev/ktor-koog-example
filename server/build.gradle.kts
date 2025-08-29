import io.ktor.plugin.features.DockerImageRegistry.Companion.googleContainerRegistry

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
}

application {
    mainClass.set("org.jetbrains.demo.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

group = "org.jetbrains.demo"
version = "1.0.0"

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        freeCompilerArgs.addAll("-Xcontext-sensitive-resolution", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(project(":ktor-openid"))
    implementation(project(":shared"))
    implementation(libs.logback)

    implementation(libs.koog.agents)
    implementation(libs.koog.ktor)
    implementation(libs.tool.schema)

    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)

    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)

    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.flyway.postgresql)

    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlin.test.junit)
}

ktor {
    docker {
        localImageName = "ktor-ai-example"
        imageTag = project.version.toString()
        externalRegistry =
            googleContainerRegistry(
                projectName = provider { "Droidcon Bangladesh" },
                appName = providers.environmentVariable("GCLOUD_APPNAME"),
                username = providers.environmentVariable("GCLOUD_USERNAME"),
                password = providers.environmentVariable("GCLOUD_REGISTRY_PASSWORD"),
            )
    }
    fatJar {
        allowZip64 = true
        archiveFileName.set("dc-bangladesh.jar")
    }
}
