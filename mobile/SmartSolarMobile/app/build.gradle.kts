import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Public deployment configuration only. Never put credentials into a BuildConfig field.
val releaseApiUrl = providers.gradleProperty("smartSolarApiBaseUrl").orElse("").get()
if (releaseApiUrl.isNotEmpty()) {
    val uri = URI(releaseApiUrl)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.query == null && uri.fragment == null &&
        uri.path.endsWith("/api/v1/")) {
        "smartSolarApiBaseUrl must be an HTTPS URL ending in /api/v1/ without credentials, query or fragment."
    }
}

val mapsProperties = Properties()
val mapsFile = rootProject.file("secrets.properties")
if (mapsFile.exists()) mapsFile.inputStream().use { mapsProperties.load(it) }
val mapsApiKey = mapsProperties.getProperty("MAPS_API_KEY", "").trim()

android {
    namespace = "com.smartsolar.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartsolar.mobile"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "MAPS_CONFIGURED", mapsApiKey.isNotEmpty().toString())
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://localhost:5000/api/v1/\"")
        }
        release {
            // An unconfigured release shows a configuration message and makes no network requests.
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiUrl}\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.google.maps)
    implementation(libs.google.location)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
