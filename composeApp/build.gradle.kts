import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.gradle.internal.classpath.Instrumented.getenv
import org.gradle.internal.component.external.descriptor.MavenScope
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.hot.reload)
}

kotlin {
    jvmToolchain(21)

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "kotlin-app-wasm-js.js"
                devServer =
                    (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static =
                            (static ?: mutableListOf()).apply {
                                add(rootDirPath)
                                add(projectDirPath)
                            }
                    }
            }
        }
        binaries.executable()
    }
    js {
        binaries.executable()
        browser {
            outputModuleName = "composeApp"
            commonWebpackConfig {
                outputFileName = "kotlin-app-js.js"
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("web") {
                withJs()
                withWasmJs()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(ktorLibs.client.core)
            implementation(ktorLibs.client.auth)
            implementation(ktorLibs.serialization.kotlinx.json)
            implementation(ktorLibs.client.contentNegotiation)
            implementation(ktorLibs.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.androidx.navigation.compose)
            // https://youtrack.jetbrains.com/issue/CMP-8519
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
            implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
            implementation(project(":shared"))
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(ktorLibs.client.android)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.security.crypto)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(ktorLibs.server.cio)
                implementation(project(":ktor-openid"))
                implementation(ktorLibs.server.auth)
                implementation(ktorLibs.client.cio)
                implementation(libs.kotlinx.html)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        val webMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.4")
            }
        }
    }
}

private fun property(name: String): String? =
    System.getenv(name) ?: System.getProperty(name) ?: gradleLocalProperties(projectDir, providers).getProperty(name)

android {
    namespace = "org.jetbrains"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.jetbrains"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "GOOGLE_CLIENT_ID",
            "\"${property("GOOGLE_CLIENT_ID") ?: "<missing-google-client-id>"}\""
        )
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${property("API_BASE_URL") ?: "http://10.0.2.2:8080"}\""
        )
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "org.jetbrains.demo.MainKt"
        jvmArgs += listOf(
            "-DGOOGLE_CLIENT_ID=${property("GOOGLE_CLIENT_ID") ?: "<missing-google-client-id>"}",
            "-DAPI_BASE_URL=${property("API_BASE_URL") ?: "http://localhost:8080"}"
        )
    }
}

tasks {
    val cleanWebApp by registering(Delete::class) {
        delete(file("$rootDir/server/src/main/resources/web"))
    }

    val buildWebApp by registering(Copy::class) {
        val wasmDist = "wasmJsBrowserDistribution"
        val jsDist = "jsBrowserDistribution"
        dependsOn(cleanWebApp, wasmDist, jsDist)

        from(named(jsDist).get().outputs.files)
        from(named(wasmDist).get().outputs.files)
        into(layout.buildDirectory.dir("webApp"))
        into(file("$rootDir/server/src/main/resources/web"))
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
