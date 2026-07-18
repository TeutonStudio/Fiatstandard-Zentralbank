package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@Serializable
sealed interface ZugPhase {
    @Serializable
    data object Prozug : ZugPhase

    @Serializable
    data object Epizug : ZugPhase
}

@Serializable
data class ZugStatus(
    val zugId: Long,
    val spieler: SpielerId,
    val phase: ZugPhase,
    val prozug: ProzugStatus = ProzugStatus(),
)

@Serializable
data class VerwaltungsVerpflichtungId(
    val zugId: Long,
    val ecke: KartenEcke,
)

@Serializable
data class ProduktionsStandortId(
    val feld: KartenFeld,
)

@Serializable
enum class VerbindlichkeitArt {
    UNVERMOEGEN,
    RUECKKAUF,
}

@Serializable
data class VerbindlichkeitId(
    val anleihe: AnleiheId,
    val zugId: Long,
    val art: VerbindlichkeitArt,
)

@Serializable
data class VerwaltungsVerpflichtung(
    val id: VerwaltungsVerpflichtungId,
    val typ: EckGebaeudeTyp,
    val bedarf: Map<Rohstoff, Int>,
)

@Serializable
data class FaelligeVerbindlichkeit(
    val id: VerbindlichkeitId,
    val schuldner: SpielerId,
    val empfaenger: KontoId,
    val betrag: Geld,
)

@Serializable
data class ProduktionsBuchung(
    val standort: ProduktionsStandortId,
    val laeufe: Int,
)

@Serializable
data class ProzugStatus(
    val begonnen: Boolean = false,
    val abbauErtraege: Map<Rohstoff, Int> = emptyMap(),
    val verwaltungsVerpflichtungen: List<VerwaltungsVerpflichtung> = emptyList(),
    val verbindlichkeiten: List<FaelligeVerbindlichkeit> = emptyList(),
    val produktionsBuchungen: List<ProduktionsBuchung> = emptyList(),
    val versorgteStandorte: Set<VerwaltungsVerpflichtungId> = emptySet(),
    val beglicheneVerbindlichkeiten: Set<VerbindlichkeitId> = emptySet(),
    val erfolgreichAbgeschlossen: Boolean = false,
)
