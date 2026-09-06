enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            // Kotlin 2.4 requires R8 >= 9.1.29; AGP 8.13.2 embeds R8 8.13.19.
            classpath("com.android.tools:r8:9.1.43")
        }
    }
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Kreate"
include(":composeApp")
// Projects from extensions
include(":oldtube")
project(":oldtube").projectDir = file("extensions/innertube")
include(":kugou")
project(":kugou").projectDir = file("extensions/kugou")
include(":lrclib")
project(":lrclib").projectDir = file("extensions/lrclib")
include(":discord")
project(":discord").projectDir = file("extensions/discord")
// Submodules
include(":innertube")
project(":innertube").projectDir = file("modules/innertube")
include(":kizzy")
project(":kizzy").projectDir = file("modules/kizzy/gateway")
include(":kizzyDomain")
project(":kizzyDomain").projectDir = file("modules/kizzy/domain")
include(":metrolistInnertube")
project(":metrolistInnertube").projectDir = file("modules/metrolist/innertube")
