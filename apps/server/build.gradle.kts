plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("de.teutonstudio.zentralbank.server.ServerMainKt")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    implementation(project(":adapters:persistence-json"))
    implementation(project(":adapters:protocol-json"))
    implementation(project(":tools:simulation"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
