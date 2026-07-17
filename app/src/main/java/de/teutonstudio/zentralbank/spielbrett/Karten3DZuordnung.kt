package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten

private val GelaendeDarstellung = mapOf(
    GelaendeTyp.EBENE to DreieckTyp("Ebene", Color(0xFF8DBB61), rauheit = 0.92f),
    GelaendeTyp.WALD to DreieckTyp("Wald", Color(0xFF2E7D32), rauheit = 0.98f),
    GelaendeTyp.GEBIRGE to DreieckTyp(
        "Gebirge",
        Color(0xFF757575),
        metallisch = 0.12f,
        rauheit = 0.78f,
    ),
    GelaendeTyp.WUESTE to DreieckTyp("Wüste", Color(0xFFD8B56A), rauheit = 0.96f),
    GelaendeTyp.SUMPF to DreieckTyp("Sumpf", Color(0xFF607D3B), rauheit = 0.72f),
)

private val SpielerPalette = listOf(
    Color(0xFFA85D00),
    Color(0xFF827000),
    Color(0xFF367A3D),
    Color(0xFF007B83),
    Color(0xFF3F6FAF),
    Color(0xFF7659AD),
    Color(0xFFA94F85),
)

private val NeutralFarbe = Color(0xFF37474F)
private val ZerstoertFarbe = Color(0xFF616161)
private val AuswahlFarbe = Color(0xFFFFD600)

fun KartenVorlage.zu3DModell(
    zeigeBearbeitungsRaster: Boolean = false,
): Spielbrett3DModell = alsSpielkarte().zu3DModell(
    zeigeBearbeitungsRaster = zeigeBearbeitungsRaster,
)

fun Spielkarte.zu3DModell(
    zeigeBearbeitungsRaster: Boolean = false,
    spielerReihenfolge: List<SpielerId> = emptyList(),
    hervorhebung: KartenOrt? = null,
): Spielbrett3DModell {
    val farben = spielerReihenfolge.mapIndexed { index, spieler ->
        spieler to SpielerPalette.getOrElse(index) { Color(0xFF707070) }
    }.toMap()
    fun spielerFarbe(spieler: SpielerId?): Color = farben[spieler]
        ?: spieler?.let { SpielerPalette[Math.floorMod(it.wert.hashCode(), SpielerPalette.size)] }
        ?: NeutralFarbe

    return Spielbrett3DModell(
        hexagon = hexagon,
        zeigeBearbeitungsRaster = zeigeBearbeitungsRaster,
        zeigeWasserFlaeche = !zeigeBearbeitungsRaster,
        unbegrenztesBearbeitungsRaster = zeigeBearbeitungsRaster,
        auflagen = gelaendefelder.map { landfeld ->
            DreieckAuflage(
                position = landfeld.position.zu3DPosition(),
                typ = GelaendeDarstellung.getValue(landfeld.gelaende),
                ebene = AuflagenEbene.LAND,
            )
        },
        eckObjekte = belegung.ecken.map { eintrag ->
            val zustand = eintrag.zustand.zuDarstellungsZustand()
            EckObjektAuflage(
                position = eintrag.position,
                typ = SpielObjektTyp(
                    name = eintrag.typ.anzeigeName(),
                    farbe = when (zustand) {
                        ObjektDarstellungsZustand.ZERSTOERT -> ZerstoertFarbe
                        ObjektDarstellungsZustand.BELAGERT ->
                            spielerFarbe(eintrag.besitzer).alsBelagert()
                        else -> spielerFarbe(eintrag.besitzer)
                    },
                    form = eintrag.typ.zuForm(),
                    zustand = zustand,
                ),
            )
        } + (hervorhebung as? KartenOrt.Ecke)?.let { markierung ->
            EckObjektAuflage(
                markierung.position,
                SpielObjektTyp(
                    "Ausgewählte Ecke",
                    AuswahlFarbe,
                    SpielObjektForm.MARKIERUNG,
                    ObjektDarstellungsZustand.AUSGEWAEHLT,
                ),
            )
        }.let(::listOfNotNull),
        kantenObjekte = belegung.kanten.map { eintrag ->
            val zustand = eintrag.zustand.zuDarstellungsZustand()
            KantenObjektAuflage(
                position = eintrag.position,
                typ = SpielObjektTyp(
                    name = "Handelslinie",
                    farbe = if (zustand == ObjektDarstellungsZustand.ZERSTOERT) {
                        ZerstoertFarbe
                    } else NeutralFarbe,
                    form = SpielObjektForm.SCHIENE,
                    zustand = zustand,
                ),
            )
        } + belegung.seewege.map { seeweg ->
            KantenObjektAuflage(
                position = KartenKante.zwischen(seeweg.hafenA, seeweg.hafenB),
                typ = SpielObjektTyp(
                    name = "Frachtschiff ${seeweg.id}",
                    farbe = NeutralFarbe,
                    form = SpielObjektForm.FRACHTSCHIFF,
                ),
            )
        } + (hervorhebung as? KartenOrt.Kante)?.let { markierung ->
            KantenObjektAuflage(
                markierung.position,
                SpielObjektTyp(
                    "Ausgewählte Kante",
                    AuswahlFarbe,
                    SpielObjektForm.MARKIERUNG,
                    ObjektDarstellungsZustand.AUSGEWAEHLT,
                ),
            )
        }.let(::listOfNotNull),
        feldObjekte = belegung.felder.map { eintrag ->
            val effektiv = KartenAuswertung.effektiverZustand(this, eintrag)
            FeldObjektAuflage(
                position = eintrag.position.zu3DPosition(),
                typ = SpielObjektTyp(
                    name = when (val anlage = eintrag.anlage) {
                        FeldAnlage.Geschaeftsbank -> "Geschäftsbank"
                        is FeldAnlage.Abbaueinheit -> "Abbaueinheit ${anlage.rohstoff.name}"
                        is FeldAnlage.Wirtschaftsregion ->
                            anlage.bauteil.text.replaceFirstChar(Char::uppercase)
                    },
                    farbe = when (effektiv) {
                        AnlagenZustand.AKTIV,
                        AnlagenZustand.VERLASSEN -> NeutralFarbe
                        AnlagenZustand.ZERSTOERT -> ZerstoertFarbe
                    },
                    form = when (eintrag.anlage) {
                        FeldAnlage.Geschaeftsbank -> SpielObjektForm.GESCHAEFTSBANK
                        is FeldAnlage.Abbaueinheit -> SpielObjektForm.ABBAUEINHEIT
                        is FeldAnlage.Wirtschaftsregion -> SpielObjektForm.ABBAUEINHEIT
                    },
                    zustand = when (effektiv) {
                        AnlagenZustand.AKTIV -> ObjektDarstellungsZustand.INTAKT
                        AnlagenZustand.VERLASSEN -> ObjektDarstellungsZustand.VERLASSEN
                        AnlagenZustand.ZERSTOERT -> ObjektDarstellungsZustand.ZERSTOERT
                    },
                ),
            )
        } + belegung.kriegseinheiten.mapNotNull { einheit ->
            val ort = einheit.ort as? KartenOrt.Feld ?: return@mapNotNull null
            FeldObjektAuflage(
                position = ort.position.zu3DPosition(),
                typ = SpielObjektTyp(
                    name = "${einheit.typ.name.lowercase()} ${einheit.id}",
                    farbe = spielerFarbe(einheit.besitzer),
                    form = when (einheit.typ) {
                        KriegsEinheitTyp.PANZER -> SpielObjektForm.PANZER
                        KriegsEinheitTyp.KRIEGSSCHIFF -> SpielObjektForm.KRIEGSSCHIFF
                    },
                ),
            )
        } + (hervorhebung as? KartenOrt.Feld)?.let { markierung ->
            FeldObjektAuflage(
                markierung.position.zu3DPosition(),
                SpielObjektTyp(
                    "Ausgewähltes Feld",
                    AuswahlFarbe,
                    SpielObjektForm.MARKIERUNG,
                    ObjektDarstellungsZustand.AUSGEWAEHLT,
                ),
            )
        }.let(::listOfNotNull),
    )
}

internal fun KartenFeld.zu3DPosition(): DreieckPosition = DreieckPosition(
    zeile = zeile,
    spalte = spalte,
    ausrichtung = when (haelfte) {
        DreieckHaelfte.OBEN -> DreieckAusrichtung.OBEN
        DreieckHaelfte.UNTEN -> DreieckAusrichtung.UNTEN
    },
)

internal fun DreieckPosition.zuKartenFeld(): KartenFeld = KartenFeld(
    zeile = zeile,
    spalte = spalte,
    haelfte = when (ausrichtung) {
        DreieckAusrichtung.OBEN -> DreieckHaelfte.OBEN
        DreieckAusrichtung.UNTEN -> DreieckHaelfte.UNTEN
    },
)

internal fun DreieckPosition.zuKartenDreieck(): KartenFeld = zuKartenFeld()

internal fun DreieckTreffer.zuKartenOrt(modus: KartenZielModus): KartenOrt? {
    val feld = position.zuKartenFeld()
    return when (modus) {
        KartenZielModus.FELD -> KartenOrt.Feld(feld)
        KartenZielModus.ECKE -> if (abstandZurNaechstenEcke <= ECK_TREFFER_RADIUS) {
            KartenOrt.Ecke(feld.ecken()[naechsteEcke])
        } else {
            null
        }
        KartenZielModus.KANTE -> if (abstandZurNaechstenKante <= KANTEN_TREFFER_RADIUS) {
            KartenOrt.Kante(feld.kanten()[naechsteKante])
        } else {
            null
        }
    }
}

private const val ECK_TREFFER_RADIUS = 0.70f
private const val KANTEN_TREFFER_RADIUS = 0.40f

enum class KartenZielModus {
    ECKE,
    KANTE,
    FELD,
}

private fun EckGebaeudeTyp.anzeigeName(): String = when (this) {
    EckGebaeudeTyp.HAUPTBAHNHOF -> "Hauptbahnhof"
    EckGebaeudeTyp.BAHNHOF -> "Bahnhof"
    EckGebaeudeTyp.GROSSBAHNHOF -> "Großbahnhof"
    EckGebaeudeTyp.HAFEN -> "Hafen"
    EckGebaeudeTyp.GROSSHAFEN -> "Großhafen"
}

private fun EckGebaeudeTyp.zuForm(): SpielObjektForm = when (this) {
    EckGebaeudeTyp.HAUPTBAHNHOF -> SpielObjektForm.HAUPTBAHNHOF
    EckGebaeudeTyp.BAHNHOF -> SpielObjektForm.BAHNHOF
    EckGebaeudeTyp.GROSSBAHNHOF -> SpielObjektForm.GROSSBAHNHOF
    EckGebaeudeTyp.HAFEN -> SpielObjektForm.HAFEN
    EckGebaeudeTyp.GROSSHAFEN -> SpielObjektForm.GROSSHAFEN
}

private fun BauwerkZustand.zuDarstellungsZustand(): ObjektDarstellungsZustand = when (this) {
    BauwerkZustand.INTAKT -> ObjektDarstellungsZustand.INTAKT
    BauwerkZustand.BELAGERT -> ObjektDarstellungsZustand.BELAGERT
    BauwerkZustand.ZERSTOERT -> ObjektDarstellungsZustand.ZERSTOERT
}

private fun Color.alsBelagert(): Color = Color(
    red = (red * 0.45f + 0.55f).coerceAtMost(1f),
    green = green * 0.38f,
    blue = blue * 0.38f,
    alpha = alpha,
)
