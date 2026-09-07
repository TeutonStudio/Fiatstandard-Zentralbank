import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "fiatstandard.js"
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:application"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val produktionsVerzeichnis = layout.buildDirectory.dir("dist/js/productionExecutable")

val itchBundlePruefen by tasks.registering {
    group = "verification"
    description = "Prüft die eigenständige HTML-Distribution für itch.io."
    dependsOn("jsBrowserDistribution")
    inputs.dir(produktionsVerzeichnis)

    doLast {
        val verzeichnis = produktionsVerzeichnis.get().asFile
        check(verzeichnis.isDirectory) {
            "Web-Distribution fehlt: ${verzeichnis.relativeTo(projectDir)}"
        }
        check(verzeichnis.resolve("index.html").isFile) {
            "index.html muss im Wurzelverzeichnis der itch-Distribution liegen."
        }
        check(verzeichnis.resolve("fiatstandard.js").isFile) {
            "Das ausführbare JavaScript-Bundle fiatstandard.js fehlt."
        }

        val dateien = verzeichnis.walkTopDown().filter { it.isFile }.toList()
        check(dateien.size <= 1_000) {
            "Die itch-Distribution enthält ${dateien.size} Dateien; erlaubt sind höchstens 1.000."
        }
        val gesamtGroesse = dateien.sumOf { it.length() }
        check(gesamtGroesse <= 500L * 1024L * 1024L) {
            "Die itch-Distribution ist größer als 500 MB."
        }
        dateien.forEach { datei ->
            check(datei.length() <= 200L * 1024L * 1024L) {
                "${datei.relativeTo(verzeichnis)} ist größer als 200 MB."
            }
        }

        val textEndungen = setOf("html", "js", "css", "json", "map")
        val verboteneTexte = listOf("127.0.0.1", "localhost", "http://")
        dateien.filter { it.extension.lowercase() in textEndungen }.forEach { datei ->
            val text = datei.readText()
            verboteneTexte.forEach { verboten ->
                check(verboten !in text) {
                    "${datei.relativeTo(verzeichnis)} enthält den verbotenen Netzwerkverweis '$verboten'."
                }
            }
        }
    }
}

val itchZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Erzeugt die direkt auf itch.io hochladbare HTML-ZIP."
    dependsOn(itchBundlePruefen)
    archiveFileName.set("fiatstandard-itch-v2.0.0.zip")
    destinationDirectory.set(layout.buildDirectory.dir("releases"))
    from(produktionsVerzeichnis)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
