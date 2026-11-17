import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // Permite probar con una captura real: ./gradlew :core:test -Dreal.image=... -Dreal.ocr=...
    listOf("real.image", "real.ocr", "real.payslip").forEach { k -> System.getProperty(k)?.let { systemProperty(k, it) } }
    testLogging { showStandardStreams = true }
}
