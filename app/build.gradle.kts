plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.rrt.neonreader"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.rrt.neonreader"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.jsoup:jsoup:1.20.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation("junit:junit:4.13.2")
}
