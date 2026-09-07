// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

group = "de.teutonstudio"
version = "2.0.0"


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
                        import.startsWith("import androidx.") ||
                        import.startsWith("import java.") ||
                        import.startsWith("import javax.")
                    ) {
                        fehler += "${datei.relativeTo(projectDir)}:${index + 1}: Plattformimport in gemeinsamem Core"
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


val releaseArtifacts by tasks.registering {
    group = "distribution"
    description = "Erzeugt APK, itch-ZIP, Manifest und SHA-256-Prüfsummen für Version 2.0.0."
    dependsOn(":apps:web:itchZip", ":apps:android:assembleDebug")

    val ausgabeVerzeichnis = layout.buildDirectory.dir("releases")
    outputs.dir(ausgabeVerzeichnis)

    doLast {
        val ausgabe = ausgabeVerzeichnis.get().asFile
        ausgabe.deleteRecursively()
        check(ausgabe.mkdirs() || ausgabe.isDirectory)

        val webZip = project(":apps:web").layout.buildDirectory
            .file("releases/fiatstandard-itch-v2.0.0.zip")
            .get().asFile
        check(webZip.isFile) { "itch-ZIP wurde nicht erzeugt: $webZip" }
        webZip.copyTo(ausgabe.resolve(webZip.name), overwrite = true)

        val apkVerzeichnis = project(":apps:android").layout.buildDirectory
            .dir("outputs/apk/debug")
            .get().asFile
        val apk = apkVerzeichnis.walkTopDown()
            .firstOrNull { it.isFile && it.extension == "apk" }
            ?: error("Android-Debug-APK wurde nicht erzeugt: $apkVerzeichnis")
        val zielApk = ausgabe.resolve("fiatstandard-android-v2.0.0-debug.apk")
        apk.copyTo(zielApk, overwrite = true)

        fun sha256(datei: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            datei.inputStream().use { eingabe ->
                val puffer = ByteArray(64 * 1024)
                while (true) {
                    val gelesen = eingabe.read(puffer)
                    if (gelesen < 0) break
                    digest.update(puffer, 0, gelesen)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }

        val artefakte = listOf(ausgabe.resolve(webZip.name), zielApk)
        ausgabe.resolve("checksums.sha256").writeText(
            artefakte.joinToString(separator = "\n", postfix = "\n") { datei ->
                "${sha256(datei)}  ${datei.name}"
            },
        )
        ausgabe.resolve("build-manifest.json").writeText(
            """{
              "version": "2.0.0",
              "regelwerk": "core/domain",
              "artefakte": [
                "${webZip.name}",
                "${zielApk.name}"
              ]
            }
            """.trimIndent(),
        )
    }
}
