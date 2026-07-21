plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("de.teutonstudio.zentralbank.simulation.SimulationMainKt")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.register<JavaExec>("massentest") {
    group = "verification"
    description = "Führt 1.000 deterministische Headless-Partien aus und exportiert Episoden."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(
        "--spiele", "1000",
        "--seed", "42",
        "--max-entscheidungen", "500",
        "--spieler", "zufall",
        "--ausgabe", layout.buildDirectory.dir("massentest").get().asFile.absolutePath,
    )
}
