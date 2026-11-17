plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.asalariado.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.asalariado.app"
        minSdk = 26
        targetSdk = 35
        // Cada compilación de GitHub Actions sube la versión para poder instalarla encima.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Las capturas de los tests se guardan aquí y Gradle las copia al terminar.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }

    // Clave de firma fija (secretos de GitHub) para que cada versión se instale encima
    // de la anterior sin borrar los datos. Sin secretos se usa la clave de depuración.
    val keystoreFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.services:storage:1.5.0")
    androidTestUtil("androidx.test.services:test-services:1.5.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // OCR en el propio móvil (modelo incluido, funciona sin conexión).
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Leer nóminas en PDF (también con contraseña) sin OCR.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Guardar la contraseña de las nóminas cifrada.
    implementation("androidx.security:security-crypto:1.0.0")
    // Bloqueo con huella o PIN del móvil.
    implementation("androidx.biometric:biometric:1.1.0")
    // Avisos y copias automáticas.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Widget de la pantalla de inicio.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
}
