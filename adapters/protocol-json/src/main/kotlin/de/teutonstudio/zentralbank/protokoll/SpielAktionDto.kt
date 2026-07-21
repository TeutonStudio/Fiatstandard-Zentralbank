package de.teutonstudio.zentralbank.protokoll

import kotlinx.serialization.Serializable

@Serializable
sealed interface SpielAktionDto {
    @Serializable
    data class Aufgeben(val spielerId: String) : SpielAktionDto

    @Serializable
    data class ProzugBeginnen(val zugId: Long) : SpielAktionDto

    @Serializable
    data class VerarbeitungAusfuehren(
        val zugId: Long,
        val feld: KartenFeldDto,
        val laeufe: Int = 1,
    ) : SpielAktionDto

    @Serializable
    data class VerwaltungsstandortVersorgen(
        val zugId: Long,
        val ecke: KartenEckeDto,
    ) : SpielAktionDto

    @Serializable
    data class VerbindlichkeitBegleichen(
        val zugId: Long,
        val verbindlichkeit: VerbindlichkeitIdDto,
    ) : SpielAktionDto

    @Serializable
    data class ProzugAbschliessen(val zugId: Long) : SpielAktionDto

    @Serializable
    data object ZugBeenden : SpielAktionDto

    /**
     * Versionierter Übergang für neue Domain-Aktionen, bis sie als eigene komfortable
     * Browser-DTOs benötigt werden. Der Server dekodiert weiterhin strikt über den
     * serialisierbaren `SpielAktion`-Serializer und validiert mit derselben Engine.
     */
    @Serializable
    data class ErweiterteAktion(val kodierung: String) : SpielAktionDto
}

@Serializable
data class KartenFeldDto(
    val zeile: Int,
    val spalte: Int,
    val haelfte: String,
)

@Serializable
data class KartenEckeDto(
    val x: Int,
    val y: Int,
)

@Serializable
data class VerbindlichkeitIdDto(
    val anleiheId: String,
    val zugId: Long,
    val art: String,
)
