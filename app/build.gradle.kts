plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val spotifyClientId: String = run {
    val secretsFile = rootProject.file("spotify.secrets.properties")
    if (secretsFile.exists()) {
        secretsFile.readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("#")) return@mapNotNull null
                val eq = trimmed.indexOf('=')
                if (eq > 0 && trimmed.substring(0, eq).trim() == "SPOTIFY_CLIENT_ID")
                    trimmed.substring(eq + 1).trim() else null
            }
            .firstOrNull().orEmpty()
    } else {
        (project.findProperty("SPOTIFY_CLIENT_ID") as? String)?.trim().orEmpty()
    }
}.also { id ->
    if (id.isEmpty()) {
        logger.warn("Spotify Client ID is empty. Create spotify.secrets.properties in the project root (same folder as build.gradle.kts) with: SPOTIFY_CLIENT_ID=your_id")
    }
}

android {
    namespace = "com.example.hearo"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.hearo"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.browser:browser:1.8.0")
}