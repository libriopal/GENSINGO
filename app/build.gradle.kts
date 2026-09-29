import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing (PRD §9). The keystore lives OUTSIDE version control: this reads
// keystore.properties from the repo root if present, else the equivalent environment
// variables, else falls back to unsigned so a clean checkout still builds.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun secret(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

val releaseStorePath = secret("storeFile", "GENSINGO_STORE_FILE")
val hasReleaseSigning = releaseStorePath != null && rootProject.file(releaseStorePath).exists()

android {
    namespace = "com.ginsengo.steward"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ginsengo.steward"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = secret("storePassword", "GENSINGO_STORE_PASSWORD")
                keyAlias = secret("keyAlias", "GENSINGO_KEY_ALIAS")
                keyPassword = secret("keyPassword", "GENSINGO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Left off deliberately for v1: Room, kotlinx-serialization and the ONNX JNI
            // bridge all resolve reflectively, and a shrink misconfiguration fails at
            // RUNTIME rather than at build time. Shipping a verifiable APK outranks a
            // smaller one. Recorded as open in EINCOL_REPORT.md §Open.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        // A shrunk APK you can sideload without the release keystore: R8 on, debug-signed,
        // one ABI (arm64 unless -Pgensingo.abis=x86_64,... says otherwise). The universal
        // debug APK is ~80 MB, most of it unshrunk dex. Installs as its own app (".field").
        create("field") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".field"
            versionNameSuffix = "-field"
            matchingFallbacks += listOf("release")
            ndk {
                abiFilters += (project.findProperty("gensingo.abis") as String? ?: "arm64-v8a").split(",")
            }
        }
    }
    // -Pgensingo.testBuildType=field runs the on-device tests against the shrunk build.
    testBuildType = project.findProperty("gensingo.testBuildType") as String? ?: "debug"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ABI splits disabled for single debug APK output (app/build/outputs/apk/debug/app-debug.apk)
    splits {
        abi {
            isEnable = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            // Jackson and the JSON-schema generator (pulled in by the Anthropic SDK) each ship
            // these; Android packages one copy of each or none.
            excludes += "/META-INF/versions/9/module-info.class"
            excludes += "/META-INF/{LICENSE,LICENSE.txt,NOTICE,NOTICE.txt,INDEX.LIST}"
        }
    }

    // MBTiles and the ONNX graph are read byte-exact from assets; compressing them is
    // fine for size but SQLite-backed MBTiles must be opened from a real file, so the
    // app copies it out to filesDir on first run rather than relying on no-compress.
    androidResources { noCompress += listOf("onnx", "bin") }

    // Room writes each schema version here; MigrationSqlTest reads it to prove the hand-written
    // migration SQL is exactly what Room itself generates.
    sourceSets["test"].assets.srcDir("$projectDir/schemas")

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric (Room migration test) needs merged resources and assets.
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.play.services.location)

    implementation(libs.maplibre.android.sdk)

    implementation(libs.anthropic.java)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
