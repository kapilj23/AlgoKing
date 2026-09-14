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
            optimization {
                enable = false
            }
            // PRODUCTION AdMob app id. Its matching interstitial unit is
            // `AdUnits.PRODUCTION_INTERSTITIAL` — the two share a publisher id and
            // must always be changed together (docs/ads.md).
            manifestPlaceholders["admobAppId"] = "ca-app-pub-2478174291729626~9594340402"
        }
        debug {
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}