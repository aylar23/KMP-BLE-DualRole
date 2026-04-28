plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.android.tools.build:gradle:8.11.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.10.3")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
    implementation("app.cash.sqldelight:gradle-plugin:2.1.0")
}