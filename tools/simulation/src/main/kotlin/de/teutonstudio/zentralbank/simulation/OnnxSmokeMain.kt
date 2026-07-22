package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.engine.SeedZufallsquelle
import java.nio.file.Path

fun main(args: Array<String>) {
    val modell = Path.of(args.getOrElse(0) { "../ai-python/build/model/spieler-ki-v1.onnx" })
    val manifest = Path.of(args.getOrElse(1) { "../ai-python/build/model/manifest.json" })
    val umgebung = StandardTrainingsUmgebung()
    val punkt = umgebung.reset(KleineWirtschaftsBaseline(), 2_000_000_042L)
    OnnxModellAgent(modell, manifest).use { agent ->
        check(agent.status.modellGeladen) { agent.status.fallbackGrund ?: "ONNX wurde nicht geladen." }
        val aktion = agent.waehleAktion(punkt, SeedZufallsquelle(42))
        check(aktion in punkt.aktionsRaum.aktionen) { "ONNX wählte keine legale Aktion." }
        val uebergang = umgebung.step(aktion)
        println(
            "ONNX geladen; legale Aktion=${aktion::class.simpleName}; " +
                "Kandidaten=${punkt.aktionsRaum.aktionen.size}; " +
                "terminated=${uebergang.terminated}; truncated=${uebergang.truncated}",
        )
    }
}
