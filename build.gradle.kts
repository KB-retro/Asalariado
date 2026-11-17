buildscript {
    repositories {
        google()
        mavenCentral()
    }
    // Permite ejecutar los tests de :core sin el repositorio de Google (SKIP_ANDROID=1).
    if (System.getenv("SKIP_ANDROID") == null) {
        dependencies { classpath("com.android.tools.build:gradle:8.7.3") }
    }
}

plugins {
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
