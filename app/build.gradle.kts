import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kover)
}

// Maps key is read from local.properties so it never enters version control.
// Unlike google-services.json (which only identifies the project), an unrestricted
// Maps key is billable by anyone who finds it.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: ""

android {
    namespace = "com.example.kulapro"
    compileSdk = 35

    // Pinned explicitly: an earlier failed install left an empty build-tools/35.0.0
    // directory, which made AGP re-download a corrupt archive on every sync.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        // TODO(phase-0b): rename to digital.apeiro.kulapro once that package (and a
        // .debug variant) is registered in the Firebase console. "com.example.*" cannot
        // be published to Play, but changing it here before registering breaks the build.
        applicationId = "com.example.kulapro"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        debug {
            // No applicationIdSuffix: it would need its own Firebase client registration.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose - the BOM governs every version below it
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // DI
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Firebase - the BOM governs every version below it
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // Google Sign-In
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)

    // Images
    implementation(libs.coil.compose)

    // Coroutines interop for Firebase Task.await()
    implementation(libs.kotlinx.coroutines.play.services)

    // Serialization (type-safe navigation routes)
    implementation(libs.kotlinx.serialization.json)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Coverage gate: 90% line coverage over the layers that carry logic.
//
// The gate deliberately does NOT measure every class in the module. Generated Hilt and
// serialization code, BuildConfig, R, and theme definitions would dominate the denominator,
// and "covering" a @Composable by rendering it asserts almost nothing. Composable bodies are
// covered by Compose UI tests in the nightly instrumented job instead.
//
// Every exclusion below carries its reason. Add to this list only with one.
kover {
    reports {
        filters {
            // Measure the layers that carry logic. Repositories and ViewModels join this
            // list in Phase 2, when their emulator-backed and StateFlow tests land; they are
            // named here as a comment rather than silently omitted:
            //   com.example.kulapro.data.repository.*
            //   com.example.kulapro.feature.**.*ViewModel
            // data.model is intentionally absent: those classes are property declarations,
            // and their only behaviour (Reservation.statusEnum, ReservationStatus
            // .occupiesCapacity) is exercised through the AvailabilityCalculator tests.
            includes {
                classes(
                    "com.example.kulapro.domain.*",
                    "com.example.kulapro.util.*",
                )
            }
            excludes {
                classes(
                    // Generated by Hilt/Dagger - no authored logic
                    "*_Factory", "*_Factory\$*", "*_HiltModules*", "*_GeneratedInjector",
                    "*_MembersInjector", "hilt_aggregated_deps.*", "dagger.hilt.*",
                    "*_ComponentTreeDeps*", "*Hilt_*",
                    // Generated by kotlinx.serialization
                    "*\$\$serializer",
                    // Generated by the Android build
                    "*.BuildConfig", "*.R", "*.R\$*",
                    // Compose compiler artifacts - not authored code
                    "*ComposableSingletons*",
                    // Design tokens: declarations only, no branches to cover
                    "com.example.kulapro.ui.theme.*",
                )
            }
        }
        verify {
            rule("Logic layers must hold 90% line coverage") {
                bound {
                    minValue = 90
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                }
            }
        }
    }
}
