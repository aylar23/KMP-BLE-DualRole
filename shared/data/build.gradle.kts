import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("bleduo-kmp")
    id("bleduo-android-lib")
    id("bleduo-detekt")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.aylar.bledualrole.data"
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
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(projects.shared.domain)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

sqldelight {
    databases {
        create("BleDatabase") {
            packageName.set("com.aylar.bledualrole.data.db")
        }
    }
}