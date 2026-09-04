import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// `:engine` is a pure JVM module on purpose (ARCHITECTURE.md §3): an
// `import androidx.compose.*` in here does not compile. The boundary is enforced
// by the build, not by a convention.
// Bytecode level matches `:app` so the Android toolchain can consume it directly.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
}
