import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

val localProperties = Properties()
rootProject.file("local.properties").let { file ->
    if (file.exists()) FileInputStream(file).use { localProperties.load(it) }
}

android {
    namespace = "com.theblankstate.preamble"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.theblankstate.preamble"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        // AI config from local.properties (gitignored, safe).
        // Default provider/model — can be overridden live via Firebase Remote Config.
        buildConfigField("String", "AI_PROVIDER", "\"${localProperties.getProperty("AI_PROVIDER", "MISTRAL")}\"")
        buildConfigField("String", "AI_API_KEY", "\"${localProperties.getProperty("AI_API_KEY", "")}\"")
        buildConfigField("String", "AI_MODEL", "\"${localProperties.getProperty("AI_MODEL", "")}\"")

        // Cheap-tier provider for extraction/planner/summarizer.
        buildConfigField("String", "AI_MEMORY_PROVIDER", "\"${localProperties.getProperty("AI_MEMORY_PROVIDER", "GEMINI")}\"")
        buildConfigField("String", "AI_MEMORY_API_KEY", "\"${localProperties.getProperty("AI_MEMORY_API_KEY", "")}\"")
        buildConfigField("String", "AI_MEMORY_MODEL", "\"${localProperties.getProperty("AI_MEMORY_MODEL", "")}\"")

        // Per-provider keys bundled at build time. Remote Config picks which one is active.
        // Leave a key blank if you don't have an account for that provider.
        buildConfigField("String", "AI_KEY_MISTRAL", "\"${localProperties.getProperty("AI_KEY_MISTRAL", localProperties.getProperty("AI_API_KEY", ""))}\"")
        buildConfigField("String", "AI_KEY_GEMINI", "\"${localProperties.getProperty("AI_KEY_GEMINI", localProperties.getProperty("AI_MEMORY_API_KEY", ""))}\"")
        buildConfigField("String", "AI_KEY_OPENAI", "\"${localProperties.getProperty("AI_KEY_OPENAI", "")}\"")
        buildConfigField("String", "AI_KEY_CLAUDE", "\"${localProperties.getProperty("AI_KEY_CLAUDE", "")}\"")

        // PostHog analytics
        buildConfigField("String", "POSTHOG_API_KEY", "\"${localProperties.getProperty("POSTHOG_API_KEY", "")}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${localProperties.getProperty("POSTHOG_HOST", "https://us.i.posthog.com")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all {
            // Run JVM unit tests on the JUnit Platform so jqwik properties and
            // JUnit 5 (Jupiter) tests execute. The junit-vintage-engine keeps the
            // existing JUnit 4 tests running on the same platform.
            it.useJUnitPlatform()
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.graphics:graphics-shapes:1.0.0-rc01")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Google Calendar API
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.calendar)
    implementation(libs.google.api.services.tasks)
    implementation("com.google.http-client:google-http-client-gson:2.1.0")


    // Glance (Home Screen Widget)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    ksp(libs.androidx.room.compiler)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Network (for AI providers)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Coil for SVG loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")

    // Vico Charts (Jetpack Compose + Material3)
    implementation("com.patrykandpatrick.vico:compose-m3:3.1.0") {
        exclude(group = "androidx.compose.material3", module = "material3")
    }

    // PostHog analytics
    implementation("com.posthog:posthog-android:3.7.4")

    // Google AdMob — rewarded ads for AI credits
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Konfetti — confetti celebration animations
    implementation("nl.dionsegijn:konfetti-compose:2.0.5")
    // Play In-App Updates
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)

    // Play In-App Reviews
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)

    constraints {
        implementation("io.grpc:grpc-api:1.62.2") {
            version { strictly("1.62.2") }
            because("Firestore runtime currently resolves grpc-core 1.62.2; grpc-api 1.70 causes NoClassDefFoundError at runtime.")
        }
        implementation("io.grpc:grpc-context:1.62.2") {
            version { strictly("1.62.2") }
            because("Keep grpc modules aligned with Firestore's grpc-core/grpc-android versions.")
        }
    }

    testImplementation(libs.junit)
    // JUnit 5 (Jupiter) + jqwik for JVM property-based and unit tests of pure logic.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jqwik)
    // Allow the existing JUnit 4 tests to run on the JUnit Platform.
    testRuntimeOnly(libs.junit.vintage.engine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.profileinstaller)
}
