rootProject.name = "koog-sample"

dependencyResolutionManagement {
    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.2.0")
        }
    }
    repositories { mavenCentral() }
}
