import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("bleduo-kmp")
    id("bleduo-android-lib")
    id("bleduo-detekt")
}

android {
    namespace = "com.aylar.bledualrole.crypto"
}

kotlin {
    jvm()
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
            implementation(libs.cryptography.core)
        }
        androidMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
        }
        iosMain.dependencies {
            implementation(libs.cryptography.provider.apple)
        }
        jvmMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}