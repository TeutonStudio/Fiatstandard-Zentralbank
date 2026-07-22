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
    implementation(libs.onnxruntime)
    testImplementation(libs.junit)
}

tasks.register<JavaExec>("massentest") {
    group = "verification"
    description = "Führt den schnellen lokalen deterministischen Headless-Massentest aus."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(
        "--spiele", "1000",
        "--seed", "42",
        "--max-entscheidungen", "500",
        "--spieler", "zufall",
        "--parallel", "4",
        "--ausgabe", layout.buildDirectory.dir("massentest").get().asFile.absolutePath,
    )
}

fun registriereVollstaendigenMassentest(name: String, szenario: String, seed: String) =
    tasks.register<JavaExec>(name) {
        group = "verification"
        description = "Führt 10.000 reproduzierbare Partien für $szenario aus."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("de.teutonstudio.zentralbank.simulation.MassensimulationMainKt")
        args(
            "10000",
            seed,
            szenario,
            layout.buildDirectory.dir(name).get().asFile.absolutePath,
            "240",
            "12",
        )
    }

registriereVollstaendigenMassentest("massentestFriedlich10000", "generiert-wirtschaft-3", "2000000000")
registriereVollstaendigenMassentest("massentestKrieg10000", "generiert-vollstaendig-3", "2100000000")

tasks.register<JavaExec>("massentestKrieg500") {
    group = "verification"
    description = "Zeitlich begrenzter, aussagekräftiger Land-/Seekriegslauf mit 500 Partien."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.teutonstudio.zentralbank.simulation.MassensimulationMainKt")
    args(
        "500",
        "2100000000",
        "generiert-vollstaendig-3",
        layout.buildDirectory.dir("massentestKrieg500").get().asFile.absolutePath,
        "60",
        "12",
    )
}

tasks.register<JavaExec>("worker") {
    group = "application"
    description = "Startet den dauerhaft laufenden NDJSON-Trainingsworker."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.teutonstudio.zentralbank.simulation.TrainingsWorkerLauncherKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("liga") {
    group = "verification"
    description = "Führt die reproduzierbare Agentenliga aus."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.teutonstudio.zentralbank.simulation.AgentenLigaMainKt")
}

tasks.register<JavaExec>("onnxSmoke") {
    group = "verification"
    description = "Lädt das exportierte ONNX-Modell und wählt eine zentrale legale Aktion."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.teutonstudio.zentralbank.simulation.OnnxSmokeMainKt")
    args(
        "../ai-python/build/model/spieler-ki-v1.onnx",
        "../ai-python/build/model/manifest.json",
    )
}
