package de.teutonstudio.zentralbank.fachlogik.aktion

import de.teutonstudio.zentralbank.fachlogik.ereignis.AussenhandelsArt
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId
import kotlinx.serialization.Serializable

/**
 * Eine Absicht des Benutzers oder eines Agenten. Erst die [SpielEngine] entscheidet, ob daraus
 * akzeptierte [SpielEreignis]-Objekte entstehen. Aktionen werden deshalb nie als Verlauf
 * gespeichert.
 */
@Serializable
sealed interface SpielAktion {
    @Serializable
    data class ProzugBeginnen(val zugId: Long) : SpielAktion

    @Serializable
    data class VerarbeitungAusfuehren(
        val zugId: Long,
        val feld: KartenFeld,
        val laeufe: Int = 1,
    ) : SpielAktion

    @Serializable
    data class VerwaltungsstandortVersorgen(
        val zugId: Long,
        val ecke: KartenEcke,
    ) : SpielAktion

    @Serializable
    data class VerbindlichkeitBegleichen(
        val zugId: Long,
        val verbindlichkeit: VerbindlichkeitId,
    ) : SpielAktion

    @Serializable
    data class ProzugAbschliessen(val zugId: Long) : SpielAktion

    @Serializable
    data object ZugBeenden : SpielAktion

    @Serializable
    data class WarenkorbAendern(val warenkorb: Map<Rohstoff, Int>) : SpielAktion

    @Serializable
    data class RohstoffHandeln(
        val kaeufer: SpielerId,
        val verkaeufer: SpielerId,
        val rohstoff: Rohstoff,
        val menge: Int,
        val preis: Geld,
    ) : SpielAktion

    @Serializable
    data class MitAuslandHandeln(
        val spieler: SpielerId,
        val rohstoff: Rohstoff,
        val menge: Int,
        val preis: Geld,
        val art: AussenhandelsArt,
    ) : SpielAktion

    @Serializable
    data class KriegErklaeren(
        val aggressor: SpielerId,
        val verteidiger: SpielerId,
    ) : SpielAktion

    @Serializable
    data class FriedenSchliessen(
        val spielerA: SpielerId,
        val spielerB: SpielerId,
    ) : SpielAktion
}
