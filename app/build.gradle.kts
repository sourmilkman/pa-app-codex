import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val releaseVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val signingReady = listOf("PA_KEYSTORE_PATH", "PA_STORE_PASSWORD", "PA_KEY_ALIAS", "PA_KEY_PASSWORD")
    .all { !System.getenv(it).isNullOrBlank() }
android {
    namespace = "uk.co.tommulliner.pa"
    compileSdk = 37
    defaultConfig {
        applicationId = "uk.co.tommulliner.pa"
        minSdk = 29
        targetSdk = 37
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = releaseVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (signingReady) create("release") {
            storeFile = file(System.getenv("PA_KEYSTORE_PATH"))
            storePassword = System.getenv("PA_STORE_PASSWORD")
            keyAlias = System.getenv("PA_KEY_ALIAS")
            keyPassword = System.getenv("PA_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = false
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
