// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val architekturPruefen by tasks.registering {
    group = "verification"
    description = "Prueft die erlaubten Import- und Abhaengigkeitsrichtungen."

    doLast {
        val fehler = mutableListOf<String>()

        listOf("core/domain/src", "core/application/src").forEach { quellWurzel ->
            fileTree(quellWurzel) { include("**/*.kt") }.files.sorted().forEach { datei ->
                datei.readLines().forEachIndexed { index, zeile ->
                    val import = zeile.trim()
                    if (
                        import.startsWith("import android.") ||
                        import.startsWith("import androidx.")
                    ) {
                        fehler += "${datei.relativeTo(projectDir)}:${index + 1}: Android-/Room-/Compose-Import in core"
                    }
                }
            }
        }

        fileTree(projectDir) {
            include("**/*.kt")
            exclude("adapters/persistence-room/**", "**/build/**", ".gradle/**")
        }.files.sorted().forEach { datei ->
            datei.readLines().forEachIndexed { index, zeile ->
                if (zeile.trim().startsWith("import androidx.room")) {
                    fehler += "${datei.relativeTo(projectDir)}:${index + 1}: Room-Import ausserhalb persistence-room"
                }
            }
        }

        fileTree("core") { include("**/build.gradle.kts") }.files.sorted().forEach { datei ->
            datei.readLines().forEachIndexed { index, zeile ->
                if (Regex("project\\(\\\":(?:apps|adapters):").containsMatchIn(zeile)) {
                    fehler += "${datei.relativeTo(projectDir)}:${index + 1}: core darf nicht von apps oder adapters abhaengen"
                }
            }
        }

        check(fehler.isEmpty()) {
            "Verletzte Architekturregeln:\n${fehler.joinToString("\n")}"
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" || it.name == "test" }.configureEach {
        dependsOn(rootProject.tasks.named("architekturPruefen"))
    }
}
