import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("bleduo-kmp")
    id("bleduo-android-lib")
    id("bleduo-detekt")
}

android {
    namespace = "com.aylar.bledualrole.protocol"
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}