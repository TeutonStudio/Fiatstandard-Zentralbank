package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@Serializable
enum class SchrittTyp(val pflicht: Boolean) {
    ROHSTOFF_EINNAHMEN(true),
    ROHSTOFF_AUSGABEN(true),
    FINANZ_AUSGABEN(true),
    ANLEIHEN_HANDEL(false),
    ROHSTOFF_HANDEL(false),
    EXPANSION(false),
    KRIEG(false),
}

@Serializable
sealed interface Phase {
    @Serializable
    data object Einnahmen : Phase

    @Serializable
    data object Ausgaben : Phase

    @Serializable
    data object Aktionen : Phase
}

@Serializable
data class ZugStatus(
    val spieler: SpielerId,
    val phase: Phase,
    val erledigteSchritte: Set<SchrittTyp> = emptySet(),
)

enum class SchrittZustand {
    ERLEDIGT,
    VERFUEGBAR,
    GESPERRT,
}

data class SchrittInfo(
    val typ: SchrittTyp,
    val zustand: SchrittZustand,
    val begruendung: String? = null,
)
