import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.ExcludeTransitiveDependenciesFilter
import com.github.jk1.license.render.JsonReportRenderer
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date

val APP_NAME = "Kreate"
val KRUXX_APP_NAME = "KruXx"
// KruXx has its own release line. Keep versionCode strictly increasing forever: Android uses
// it (not versionName) to decide whether an APK is an update. 1_000_000 is deliberately above
// every distributed 2.2.3-kruxx.x build (latest: 14_107).
val KRUXX_VERSION_NAME = "1.0.2"
val KRUXX_VERSION_CODE = 1_000_002
val KRUXX_REPOSITORY_OWNER = "Massefehler"
val KRUXX_REPOSITORY_NAME = "KruXx"
val KRUXX_SIGNING_CERT_SHA256 = "5dc08df341c5d5b56aa9fe9ebc58eb02e0a25bc4a27b48d83a4fbe31ccbdd673"

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance( "SHA-256" )
    val hashBytes = digest.digest( this.toByteArray() )

    return hashBytes.joinToString("") { b -> "%02x".format(b) }
}

// Please DO NOT change this, it's intended to differentiate between
// knighthat/Kreate's build env and others' build env.
// Only official build env has passwords and keystore to sign the APK
// Other build environments can have unsigned version instead
val officialBuildPhrase: String? = System.getenv( "OFFICIAL_BUILD_PASSPHRASE" )
val isOfficialBuildEnv = !officialBuildPhrase.isNullOrBlank() && officialBuildPhrase.sha256() == "b2c778240e03b2005d23899aa02e51de049223a54d549d082e89dc20e51dd545"

plugins {
    // Multiplatform
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)

    // Android
    alias(libs.plugins.android.application)
    alias(libs.plugins.room)

    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias( libs.plugins.license.report )
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    compilerOptions {
        freeCompilerArgs.add( "-Xexpect-actual-classes" )
    }

    jvm()

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }

        jvmMain.dependencies {
            implementation(compose.components.resources)
            implementation(compose.desktop.currentOs)

            implementation(libs.material.icons.desktop.ext)
            implementation(libs.vlcj)
        }
        androidMain.dependencies {
            implementation( projects.metrolistInnertube )
            implementation( libs.innertubex )

            implementation(libs.kotlinx.coroutines.guava)
            implementation(libs.androidx.webkit)

            implementation( libs.androidx.glance.widgets )
            implementation( libs.androidx.constraintlayout )

            implementation( libs.androidx.appcompat )
            implementation( libs.androidx.appcompat.resources )
            implementation( libs.androidx.palette )

            implementation( libs.monetcompat )
            implementation(libs.androidmaterial)

            // Player implementations
            implementation( libs.media3.exoplayer )
            implementation(libs.media3.session)
            implementation( libs.media3.datasource.okhttp )
            implementation( libs.androidyoutubeplayer )

            implementation( libs.toasty )

            // Dependency injection
            implementation( libs.koin.android )

            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.process)
        }
        androidUnitTest.dependencies {
            implementation( libs.junit4 )
            implementation( libs.robolectric )
            implementation( libs.androidx.test )
        }
        commonMain.dependencies {
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(projects.innertube)
            implementation(projects.oldtube)
            implementation(projects.kugou)
            implementation(projects.lrclib)
            implementation( projects.discord )

            // Room KMP
            implementation( libs.room.runtime )
            implementation( libs.sqlite.bundled )

            implementation(libs.navigation.kmp)

            //coil3 mp
            implementation( libs.coil3.compose )
            implementation( libs.coil3.network.ktor )

            implementation(libs.translator)

            implementation( libs.bundles.compose.kmp )

            implementation ( libs.hypnoticcanvas )
            implementation ( libs.hypnoticcanvas.shaders )

            implementation( libs.kotlin.csv )

            implementation( libs.bundles.ktor )
            implementation( libs.okhttp3.logging.interceptor )
            implementation( libs.okhttp3.dns.over.https )

            implementation( libs.math3 )

            implementation( libs.material.icons.kmp )

            // Dependency injection
            implementation( libs.koin.core )
            implementation( libs.koin.navigation )

            // Logging
            implementation( libs.kermit )
            implementation( libs.kermit.io )
        }
        commonTest.dependencies {
            implementation( libs.kotlin.test )
        }
    }
}

android {
    lint {
        // Freeze the inherited lint debt so release checks fail only for new findings.
        // Regenerate deliberately with :composeApp:updateLintBaselineKruxxUniversalProdRelease.
        baseline = file("lint-baseline.xml")
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "me.knighthat.kreate"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()

        /*
                UNIVERSAL VARIABLES
         */
        buildConfigField( "String", "APP_NAME", "\"$APP_NAME\"" )
        buildConfigField( "String", "REPO_OWNER", "\"knighthat\"" )
        buildConfigField( "String", "REPO_NAME", "\"$APP_NAME\"" )
        buildConfigField( "boolean", "INDEPENDENT_FORK", "false" )
        buildConfigField( "boolean", "SELF_UPDATE_ENABLED", "false" )
        buildConfigField( "String", "EXPECTED_SIGNING_CERT_SHA256", "\"\"" )
        buildConfigField( "boolean", "UPSTREAM_CRASH_REPORTING_ENABLED", "true" )
        buildConfigField( "boolean", "START_ON_QUICK_PICKS_BY_DEFAULT", "false" )
        buildConfigField( "boolean", "ON_DEVICE_VOICE_SEARCH_ENABLED", "false" )
        buildConfigField( "int", "HEADER_LOGO_WIDTH_DP", "100" )
        buildConfigField( "int", "HEADER_LOGO_HEIGHT_DP", "36" )
    }

    namespace = "app.kreate.android"

    signingConfigs {
        create( "production" ) {
            storeFile = file("$rootDir/.ignore.d/keystores/production.jks")
            keyAlias = "kreate"
            storePassword = System.getenv( "STORE_PASSWORD" )
            keyPassword = System.getenv( "KEY_PASSWORD" )
        }
        create( "nightly" ) {
            storeFile = file("$rootDir/.ignore.d/keystores/nightly.jks")
            keyAlias = "nightly"
            storePassword = System.getenv( "STORE_PASSWORD" )
            keyPassword = System.getenv( "KEY_PASSWORD" )
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "$APP_NAME-debug"
        }

        release {
            isDefault = true

            // Package optimization
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create( "uncompressed" ) {
            // App's properties
            versionNameSuffix = "-f"
        }
    }

    flavorDimensions += listOf( "platform", "arch", "env" )
    //noinspection ChromeOsAbiSupport
    productFlavors {
        val vCode = libs.versions.versionCode.get().toInt()

        //<editor-fold desc="Platforms">
        create("github") {
            dimension = "platform"

            isDefault = true
        }
        create( "fdroid" ) {
            dimension = "platform"

            // App's properties
            versionNameSuffix = "-fdroid"
        }
        create( "izzy" ) {
            dimension = "platform"

            // App's properties
            versionNameSuffix = "-izzy"
        }
        // Independent fork "KruXx": own application id, release line, repository and updater.
        create( "kruxx" ) {
            dimension = "platform"

            applicationId = "de.kruxx.music"
            buildConfigField( "String", "APP_NAME", "\"$KRUXX_APP_NAME\"" )
            buildConfigField( "String", "REPO_OWNER", "\"$KRUXX_REPOSITORY_OWNER\"" )
            buildConfigField( "String", "REPO_NAME", "\"$KRUXX_REPOSITORY_NAME\"" )
            buildConfigField( "boolean", "INDEPENDENT_FORK", "true" )
            buildConfigField( "boolean", "SELF_UPDATE_ENABLED", "true" )
            buildConfigField( "String", "EXPECTED_SIGNING_CERT_SHA256", "\"$KRUXX_SIGNING_CERT_SHA256\"" )
            // KruXx is a fork: never offer to send its crash reports to Kreate's issue tracker.
            buildConfigField( "boolean", "UPSTREAM_CRASH_REPORTING_ENABLED", "false" )
            buildConfigField( "boolean", "START_ON_QUICK_PICKS_BY_DEFAULT", "true" )
            // Privacy-first voice input: use Android's on-device recognizer only.
            buildConfigField( "boolean", "ON_DEVICE_VOICE_SEARCH_ENABLED", "true" )
            // Larger single-line wordmark: "KruXx" is more prominent than the tagline.
            buildConfigField( "int", "HEADER_LOGO_WIDTH_DP", "205" )
            buildConfigField( "int", "HEADER_LOGO_HEIGHT_DP", "42" )
            manifestPlaceholders["appName"] = KRUXX_APP_NAME
            // "platform" is the first (highest-priority) flavor dimension, so these values win
            // over the upstream values supplied by the env flavor below.
            versionCode = KRUXX_VERSION_CODE
            versionName = KRUXX_VERSION_NAME
        }
        //</editor-fold>
        //<editor-fold desc="Architectures">
        create("universal") {
            dimension = "arch"

            isDefault = true
        }
        create("arm32") {
            dimension = "arch"

            // App's properties
            versionCode = (vCode * 10) + 1

            // Build architecture
            ndk { abiFilters += "armeabi-v7a" }
        }
        create("arm64") {
            dimension = "arch"

            // App's properties
            versionCode = (vCode * 10) + 2

            // Build architecture
            ndk { abiFilters += "arm64-v8a" }
        }
        create("x86") {
            dimension = "arch"

            // App's properties
            versionCode = (vCode * 10) + 3

            // Build architecture
            ndk { abiFilters += "x86" }
        }
        create("x86_64") {
            dimension = "arch"

            // App's properties
            versionCode = (vCode * 10) + 4

            // Build architecture
            ndk { abiFilters += "x86_64" }
        }
        //</editor-fold>
        //<editor-fold desc="Environment">
        create( "nightly" ) {
            dimension = "env"

            // Signing config
            signingConfig = signingConfigs.getByName( "nightly" )

            val longFormat = SimpleDateFormat("yyyy.MM.dd")
            val shortFormat = SimpleDateFormat("yyMMdd")

            // App's properties
            applicationIdSuffix = ".nightly"
            versionName = longFormat.format (Date() )
            manifestPlaceholders["appName"] = "Nightly"
            // The idea is to combine build date and current version code together
            versionCode = "${shortFormat.format( Date() )}$vCode".toInt()
        }
        create( "prod" ) {
            dimension = "env"

            isDefault = true

            if( isOfficialBuildEnv )
                // Singing config
                signingConfig = signingConfigs.getByName( "production" )

            // App's properties
            versionName = libs.versions.versionName.get()
            manifestPlaceholders["appName"] = APP_NAME
            versionCode = vCode
        }
        //</editor-fold>
    }

    applicationVariants.all {
        outputs.map { it as BaseVariantOutputImpl }
               .forEach {
                   val suffix = if( "izzy" in flavorName )
                       "izzy"
                   else if( "Nightly" in flavorName )
                       "nightly"
                   // The next 4 conditions set the APK name to the architect
                   // if it's intended for release build
                   else if( "Arm64" in flavorName && buildType.name == "release" )
                       "arm64-v8a"
                   else if( "Arm32" in flavorName && buildType.name == "release" )
                       "armeabi-v7a"
                   else if( "X86_64" in flavorName && buildType.name == "release" )
                       "x86_64"
                   else if( "X86" in flavorName && buildType.name == "release" )
                       "x86"
                   // Or just append build type at the end of the APK file name
                   else
                       buildType.name

                   val appName = if( productFlavors.any { f -> f.name == "kruxx" } ) KRUXX_APP_NAME else APP_NAME
                   it.outputFileName = "$appName-${suffix}.apk"
               }

        val isKruxx = productFlavors.any { flavor -> flavor.name == "kruxx" }

        if( buildType.name != "debug" && !isKruxx ) {
            preBuildProvider.get().dependsOn( copyReleaseNote )
        }

        // Every KruXx variant uses the flavor-specific notes. Keeping the copy task in the debug
        // graph as well declares the generated resource dependency explicitly and prevents stale
        // or ordering-dependent notes when Gradle schedules tests/resources in parallel.
        if( isKruxx )
            preBuildProvider.get().dependsOn( copyKruxxReleaseNote )
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// The launcher label comes from the manifest placeholder "appName", which build types
// (debug) and the env flavors (prod/nightly) also set. Placeholders of the platform flavor
// don't reliably win that merge, so pin the label for every "kruxx" variant here.
androidComponents {
    onVariants( selector().withFlavor( "platform" to "kruxx" ) ) { variant ->
        val label = if( variant.buildType == "debug" ) "$KRUXX_APP_NAME-debug" else KRUXX_APP_NAME
        variant.manifestPlaceholders.put( "appName", label )
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

compose.desktop {
    application {

        mainClass = "MainKt"

        //conveyor
        version = "0.0.1"
        group = "me.knighthat.kreate"

        //jpackage
        nativeDistributions {
            //conveyor
            vendor = "RiMusic.DesktopApp"
            description = "RiMusic Desktop Music Player"

            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "RiMusic.DesktopApp"
            packageVersion = "0.0.1"
        }
    }
}

compose.resources {
    publicResClass = true
    generateResClass = always
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Android ships org.json on the boot classpath (/apex/com.android.art/javalib/core-libart.jar).
// MetrolistExtractor drags the standalone org.json:json artefact in through
// modules/metrolist/innertube, so R8 treats those classes as program code and rewrites call
// sites against its own optimized signatures (JSONObject.put ends up as put(Object, String)V).
// At runtime the platform class always wins, so the rewritten method does not exist: the
// embedded YouTube player died in IFramePlayerOptions.addInt with a NoSuchMethodError as soon
// as a video was opened. Dropping the artefact keeps every org.json reference on the platform
// class. Metrolist excludes it the same way in modules/metrolist/app/build.gradle.kts.
configurations.configureEach {
    exclude( group = "org.json", module = "json" )
}

dependencies {
    // Room
    add( "kspAndroid", libs.room.compiler )
    add( "kspJvm", libs.room.compiler )

    coreLibraryDesugaring(libs.desugaring.nio)
}

// Use `gradlew dependencies` to get report in composeApp/build/reports/dependency-license
licenseReport {
    // Select projects to examine for dependencies.
    // Defaults to current project and all its subprojects
    projects = arrayOf( project )

    // Adjust the configurations to fetch dependencies. Default is 'runtimeClasspath'
    // For Android projects use 'releaseRuntimeClasspath' or 'yourFlavorNameReleaseRuntimeClasspath'
    // Use 'ALL' to dynamically resolve all configurations:
    // configurations = ALL
    configurations = arrayOf( "kruxxUniversalProdReleaseRuntimeClasspath" )

    // Don't include artifacts of project's own group into the report
    excludeOwnGroup = true

    // Don't exclude bom dependencies.
    // If set to true, then all BOMs will be excluded from the report
    excludeBoms = true

    // Set custom report renderer, implementing ReportRenderer.
    // Yes, you can write your own to support any format necessary.
    renderers = arrayOf( JsonReportRenderer() )

    filters = arrayOf<DependencyFilter>( ExcludeTransitiveDependenciesFilter() )
}

val copyReleaseNote = tasks.register<Copy>("copyReleaseNote" ) {
    description = "Copy release note that matches current versionCode to raw folder"
    group = JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME

    from( "$rootDir/fastlane/metadata/android/en-US/changelogs" )

    val fileName = "${libs.versions.versionCode.get()}.txt"
    setIncludes( listOf( fileName ) )

    into( "$rootDir/composeApp/src/androidMain/res/raw" )

    rename {
        if( it == fileName ) "release_notes.txt" else it
    }
}

val copyKruxxReleaseNote = tasks.register<Copy>( "copyKruxxReleaseNote" ) {
    description = "Copy the KruXx release note of the current KruXx version to the kruxx flavor's raw folder"
    group = JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME

    val sourceDir = "$rootDir/docs/changelogs/kruxx"
    val fileName = "$KRUXX_VERSION_NAME.txt"

    // Declared as a task input so Gradle fails with a clear message when the note for the
    // current KruXx version is missing (a doFirst check would break the configuration cache).
    inputs.file( "$sourceDir/$fileName" ).withPropertyName( "kruxxReleaseNote" )

    from( sourceDir )
    setIncludes( listOf( fileName ) )

    into( "$rootDir/composeApp/src/androidKruxx/res/raw" )

    rename {
        if( it == fileName ) "release_notes.txt" else it
    }
}
