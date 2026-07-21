package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@Serializable
data class SpielErgebnis(
    val gewinner: SpielerId?,
    val platzierungen: List<SpielerId>,
    val ausgeschiedeneSpieler: Set<SpielerId>,
    val grund: SpielEndeGrund,
    val endRunde: Int,
)

@Serializable
enum class SpielEndeGrund {
    /** Für eine künftig fachlich definierte reguläre Siegbedingung reserviert. */
    REGULAERER_SIEG,
    LETZTER_SPIELFAEHIGER_SPIELER,
    ALLE_AUSGESCHIEDEN,
    UNENTSCHIEDEN,
}

@Serializable
enum class AusscheidensGrund {
    AUFGABE,
    INSOLVENZ,
    KRIEGSFOLGE,
}
