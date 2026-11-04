import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
}

val coroutinesVersion = "1.11.0"
val ktorVersion = "3.6.1"

kotlin {
    android {
        namespace = "travel.roame.takehome.shared"
        compileSdk { version = release(37) { minorApiLevel = 0 } }
        minSdk = 23
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // Plain JVM target so the shared rules can be unit tested on any machine
    // (`./gradlew :shared:jvmTest`) without an Android SDK or iOS simulator.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }

    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.2")
            implementation("io.ktor:ktor-client-core:$ktorVersion")
        }
        androidMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            implementation("io.ktor:ktor-client-mock:$ktorVersion")
        }
    }
}
