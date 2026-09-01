plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

val offlineEnv = rootProject.file("offline.env").takeIf { it.exists() }?.readLines().orEmpty()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
    .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.expresstracker"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.expresstracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.3"
        buildConfigField("String", "ALAPI_TOKEN", quoted(offlineEnv["ALAPI_TOKEN"].orEmpty()))
        buildConfigField("String", "KDNIAO_EBUSINESS_ID", quoted(offlineEnv["KDNIAO_EBUSINESS_ID"].orEmpty()))
        buildConfigField("String", "KDNIAO_APP_KEY", quoted(offlineEnv["KDNIAO_APP_KEY"].orEmpty()))
        buildConfigField("String", "KDNIAO_API_URL", quoted(offlineEnv["KDNIAO_API_URL"] ?: "http://api.kdniao.com/api/dist/pickupcode"))
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("top.yukonga.miuix.kmp:miuix-android:0.2.9")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
}

// This module contains Kotlin sources only. Avoid an empty javac invocation on
// Windows, where scanning transformed dependency jars can be blocked by the host.
tasks.withType<JavaCompile>().configureEach {
    enabled = false
}
