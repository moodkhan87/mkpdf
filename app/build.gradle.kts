plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.mkpdf.app"
    compileSdk = 34
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "com.mkpdf.app"
        minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/rustJniLibs"))
    packaging { jniLibs.useLegacyPackaging = false }
    buildTypes { release { isMinifyEnabled = false } }
}
val buildRust by tasks.registering(Exec::class) {
    group = "build"
    description = "Cross-compile the pinned PdfCraft JNI crate for all four Android ABIs"
    workingDir(rootDir)
    // Python is also available on Windows as `python`; override with -Ppython=py if needed.
    doFirst { commandLine(providers.gradleProperty("python").orElse("python3").get(),
        "scripts/build_native.py", "--ndk", android.ndkDirectory.absolutePath,
        "--output", layout.buildDirectory.dir("generated/rustJniLibs").get().asFile.absolutePath) }
    // cargo performs its own dependency tracking; do not falsely mark vendor changes up-to-date.
}
tasks.named("preBuild").configure { dependsOn(buildRust) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
}
