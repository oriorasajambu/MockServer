import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.minion.cashflowmanager.buildlogic"

// The convention plugins run inside the Gradle daemon, so they target the daemon's JVM (21 per
// gradle/gradle-daemon-jvm.properties) rather than the Java 11 the Android modules compile to.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // compileOnly, not implementation: these plugins are supplied by the consuming build's
    // classpath at execution time. Leaking them as runtime dependencies causes duplicate-class
    // conflicts when the main build applies the same plugins.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}
