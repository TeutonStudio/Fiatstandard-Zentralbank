package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.enthaelt

/**
 * Vollstaendige Beschreibung eines dreieckig tesselierten Spielbretts.
 *
 * [hexagon] beschreibt die gespeicherte Kartenform mit genau 6 * radius² Dreiecken. Der Baumodus
 * kann darüber ein unbegrenzt nachgeführtes Bearbeitungsraster anzeigen, ohne Wasser zu rendern.
 */
@Immutable
data class Spielbrett3DModell(
    val hexagon: KartenHexagon,
    val auflagen: List<DreieckAuflage> = emptyList(),
    val eckObjekte: List<EckObjektAuflage> = emptyList(),
    val kantenObjekte: List<KantenObjektAuflage> = emptyList(),
    val feldObjekte: List<FeldObjektAuflage> = emptyList(),
    val zeigeBearbeitungsRaster: Boolean = false,
    val zeigeWasserFlaeche: Boolean = true,
    val unbegrenztesBearbeitungsRaster: Boolean = false,
) {
    init {
        auflagen.forEach { auflage ->
            require(hexagon.enthaelt(auflage.position.zuKartenFeld())) {
                "Auflage ${auflage.position} liegt außerhalb des Kartenhexagons."
            }
        }

        val doppeltBelegteFelder = auflagen
            .groupingBy { auflage -> auflage.position to auflage.ebene }
            .eachCount()
            .filterValues { anzahl -> anzahl > 1 }
            .keys
        require(doppeltBelegteFelder.isEmpty()) {
            "Pro Grunddreieck und Ebene ist höchstens eine Auflage erlaubt: $doppeltBelegteFelder"
        }
    }
}

/** Position eines Dreiecks innerhalb der Tesselation. */
@Immutable
data class DreieckPosition(
    val zeile: Int,
    val spalte: Int,
    val ausrichtung: DreieckAusrichtung,
)

/** Ausrichtung in der Draufsicht entlang der vertikalen Brettachse. */
enum class DreieckAusrichtung {
    OBEN,
    UNTEN,
}

/**
 * Frei definierbarer Typ einer Auflage. Unterschiedliche Typen erhalten eigene
 * Filament-Materialinstanzen.
 */
@Immutable
data class DreieckTyp(
    val name: String,
    val farbe: Color,
    val metallisch: Float = 0.05f,
    val rauheit: Float = 0.62f,
    val relief: DreieckRelief = DreieckRelief.FLACH,
) {
    init {
        require(name.isNotBlank()) { "Der Name eines DreieckTyps darf nicht leer sein." }
        require(metallisch in 0f..1f) { "metallisch muss zwischen 0 und 1 liegen." }
        require(rauheit in 0f..1f) { "rauheit muss zwischen 0 und 1 liegen." }
    }
}

enum class DreieckRelief {
    FLACH,
    GEBIRGE,
}

/** Ein typisiertes Dreieck, das auf einem Grunddreieck des Bretts liegt. */
@Immutable
data class DreieckAuflage(
    val position: DreieckPosition,
    val typ: DreieckTyp,
    val ebene: AuflagenEbene = AuflagenEbene.LAND,
)

@Immutable
data class SpielObjektTyp(
    val name: String,
    val farbe: Color,
    val form: SpielObjektForm,
    val zustand: ObjektDarstellungsZustand = ObjektDarstellungsZustand.INTAKT,
    val istVerwaltungsstandort: Boolean = false,
    val infos: List<SpielObjektInfoEintrag> = emptyList(),
    val spieler: Set<String> = emptySet(),
)

@Immutable
data class SpielObjektInfoEintrag(
    val bezeichnung: String,
    val wert: String,
)

enum class SpielObjektForm {
    TEICH,
    HAUPTBAHNHOF,
    BAHNHOF,
    GROSSBAHNHOF,
    HAFEN,
    GROSSHAFEN,
    SCHIENE,
    ABBAUEINHEIT,
    GESCHAEFTSBANK,
    FRACHTSCHIFF,
    PANZER,
    KRIEGSSCHIFF,
    MARKIERUNG,
}

enum class ObjektDarstellungsZustand {
    INTAKT,
    BELAGERT,
    ZERSTOERT,
    VERLASSEN,
    AUSGEWAEHLT,
}

@Immutable
data class EckObjektAuflage(
    val position: KartenEcke,
    val typ: SpielObjektTyp,
)

@Immutable
data class KantenObjektAuflage(
    val position: KartenKante,
    val typ: SpielObjektTyp,
    val objektId: String? = null,
    val objektIds: List<String> = emptyList(),
    val bewegungsRoute: List<KartenKante> = emptyList(),
    val routenStart: KartenEcke? = null,
)

@Immutable
data class FeldObjektAuflage(
    val position: DreieckPosition,
    val typ: SpielObjektTyp,
)

enum class AuflagenEbene {
    LAND,
    SPEZIAL,
}

data class DreieckTreffer(
    val position: DreieckPosition,
    val naechsteEcke: Int,
    val naechsteKante: Int = 0,
    val abstandZurNaechstenEcke: Float = 0f,
    val abstandZurNaechstenKante: Float = 0f,
)
