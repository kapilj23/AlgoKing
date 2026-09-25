// Explicit, because inside `android { }` the `java` extension shadows the
// `java.*` package and `java.util.Properties` will not resolve.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.algorithms.algoking"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.algorithms.algoking"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * **Debug-only entitlement override, for testing the Free and Pro paths.**
     *
     * A sideloaded build is not the Play-signed app, so `queryPurchasesAsync`
     * reports nothing and entitlement sits at `Unknown` forever — which is
     * neither of the two states worth testing. This lets a debug build pretend,
     * and it is read from **`local.properties`**, which is git-ignored and
     * untracked: no tracked file ever holds the value, so there is nothing to
     * remember to revert.
     *
     * ```properties
     * # local.properties — not in version control
     * algoking.debug.entitlement=FREE    # or PRO, or STORE
     * ```
     *
     * `STORE` is the default and means *no override at all* — a fresh clone
     * behaves exactly like production.
     *
     * The constant is emitted **only for the debug build type**, so it does not
     * exist in release, and the code that reads it lives only in
     * `src/debug/`. A release build therefore cannot use it, cannot reference
     * it, and would not compile if it tried. That is the structural answer to
     * ADR-041's objection to a debug Pro flag — *"one merge away from
     * shipping"* — which a runtime `if (BuildConfig.DEBUG)` in shared code
     * would not be.
     */
    val debugEntitlement: String = run {
        val properties = Properties()
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { stream -> properties.load(stream) }
        }
        properties.getProperty("algoking.debug.entitlement", "STORE").trim().uppercase()
    }

    /**
     * Release signing.
     *
     * Every value is read from **machine-local** Gradle properties — normally
     * `~/.gradle/gradle.properties`, which is outside this repository and outside
     * version control. Nothing here contains a password, a path to a key, or any
     * other secret, and `keystore/` is git-ignored.
     *
     * On a machine without those properties (a fresh clone, CI without secrets)
     * `signingConfigs` is simply absent and `assembleDebug`/`test` still work —
     * only `bundleRelease` needs the key, and it fails loudly rather than
     * silently producing an unsigned or debug-signed artifact.
     */
    val releaseStore = providers.gradleProperty("ALGOKING_KEYSTORE").orNull
    val releaseAlias = providers.gradleProperty("ALGOKING_KEY_ALIAS").orNull
    val releaseStorePassword = providers.gradleProperty("ALGOKING_STORE_PASSWORD").orNull
    val releaseKeyPassword = providers.gradleProperty("ALGOKING_KEY_PASSWORD").orNull
    val canSignRelease = releaseStore != null && file(releaseStore).exists() &&
        releaseAlias != null && releaseStorePassword != null && releaseKeyPassword != null

    if (canSignRelease) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
                // Both schemes: v1 for older devices, v2 for the ones that verify
                // the whole APK. Play re-signs for v3/v4 where it applies.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8: shrink, optimise and obfuscate. ARCHITECTURE.md §12 has always
            // required this before a store build; the scaffold shipped with it off.
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // PRODUCTION AdMob app id. Its matching interstitial unit is
            // `AdUnits.PRODUCTION_INTERSTITIAL` — the two share a publisher id and
            // must always be changed together (docs/ads.md).
            manifestPlaceholders["admobAppId"] = "ca-app-pub-2478174291729626~9594340402"
        }
        debug {
            // What a debug build pretends to own. `STORE` means it does not
            // pretend at all. Emitted for this build type only — see the note
            // above `debugEntitlement`, and `src/debug/.../DebugEntitlement.kt`.
            buildConfigField("String", "DEBUG_ENTITLEMENT", "\"$debugEntitlement\"")
            // Google's TEST app id. A debug build never touches the production
            // account: impressions and clicks from a developer's own device are
            // invalid traffic, and AdMob suspends accounts for it. The unit id is
            // switched to match at runtime, off the debuggable flag.
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // For DEBUG_ENTITLEMENT below. Nothing else uses BuildConfig.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.billing)
    implementation(libs.play.services.ads)
    implementation(libs.play.review)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}