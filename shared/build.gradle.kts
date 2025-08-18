import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    js { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.core)
            api("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
            // https://youtrack.jetbrains.com/issue/CMP-8519
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
            implementation(libs.koog.tools)
        }
    }
}
