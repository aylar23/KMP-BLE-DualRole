import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("bleduo-kmp")
    id("bleduo-android-lib")
    id("bleduo-detekt")
}

android {
    namespace = "com.aylar.bledualrole.presentation"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(projects.shared.domain)
        }
        iosMain.dependencies {
            // NSDate for currentTimeMs expect/actual
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}