package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import de.teutonstudio.zentralbank.fachlogik.modell.kuerzesterWasserweg

private val GelaendeDarstellung = mapOf(
    GelaendeTyp.EBENE to DreieckTyp("Ebene", Color(0xFF8DBB61), rauheit = 0.92f),
    GelaendeTyp.WALD to DreieckTyp("Wald", Color(0xFF2E7D32), rauheit = 0.98f),
    GelaendeTyp.GEBIRGE to DreieckTyp(
        "Gebirge",
        Color(0xFF757575),
        metallisch = 0.12f,
        rauheit = 0.78f,
        relief = DreieckRelief.GEBIRGE,
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
private val TeichFarbe = Color(0xFF1565A8)
private val RoutenFarbe = Color(0xFF00E5FF)

private data class TruppenStapelSchluessel(
    val position: KartenKante,
    val typ: KriegsEinheitTyp,
    val besitzer: SpielerId,
)

fun KartenVorlage.zu3DModell(
    zeigeBearbeitungsRaster: Boolean = false,
): Spielbrett3DModell = alsSpielkarte().zu3DModell(
    zeigeBearbeitungsRaster = zeigeBearbeitungsRaster,
)

fun Spielkarte.zu3DModell(
    zeigeBearbeitungsRaster: Boolean = false,
    spielerReihenfolge: List<SpielerId> = emptyList(),
    hervorhebung: KartenOrt? = null,
    routenHervorhebung: Set<KartenKante> = emptySet(),
    konflikte: Set<Konflikt> = emptySet(),
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
        eckObjekte = spezialfelder.map { spezialfeld ->
            EckObjektAuflage(
                position = spezialfeld.mittelpunkt,
                typ = SpielObjektTyp(
                    name = "Teich",
                    farbe = TeichFarbe,
                    form = SpielObjektForm.TEICH,
                ),
            )
        } + belegung.ecken.map { eintrag ->
            val zustand = eintrag.zustand.zuDarstellungsZustand()
            val name = eintrag.typ.anzeigeName()
            EckObjektAuflage(
                position = eintrag.position,
                typ = SpielObjektTyp(
                    name = name,
                    farbe = when (zustand) {
                        ObjektDarstellungsZustand.ZERSTOERT -> ZerstoertFarbe
                        ObjektDarstellungsZustand.BELAGERT ->
                            spielerFarbe(eintrag.besitzer).alsBelagert()
                        else -> spielerFarbe(eintrag.besitzer)
                    },
                    form = eintrag.typ.zuForm(),
                    zustand = zustand,
                    istVerwaltungsstandort = zustand != ObjektDarstellungsZustand.ZERSTOERT,
                    infos = bauwerkInfos(
                        spieler = eintrag.besitzer?.wert ?: "neutral",
                        gebaeude = name,
                        bauteil = eintrag.typ.zuBauteilTyp(),
                        gebautInRunde = eintrag.gebautInRunde,
                        zustand = zustand,
                    ),
                    spieler = setOfNotNull(eintrag.besitzer?.wert),
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
            val gewalthaber = KartenAuswertung.gewalthaber(this, eintrag.position)
            val verbundeneSpieler = KartenAuswertung.verbundeneSpieler(this, eintrag.position)
                .mapTo(mutableSetOf(), SpielerId::wert)
            KantenObjektAuflage(
                position = eintrag.position,
                typ = SpielObjektTyp(
                    name = "Handelslinie",
                    farbe = if (zustand == ObjektDarstellungsZustand.ZERSTOERT) {
                        ZerstoertFarbe
                    } else {
                        gewalthaber?.let(::spielerFarbe) ?: NeutralFarbe
                    },
                    form = SpielObjektForm.SCHIENE,
                    zustand = zustand,
                    infos = bauwerkInfos(
                        spieler = gewalthaber?.wert ?: "neutral",
                        gebaeude = "Handelslinie",
                        bauteil = BauteilTyp.EISENBAHNLINIE,
                        gebautInRunde = eintrag.gebautInRunde,
                        zustand = zustand,
                        weitere = listOf(
                            SpielObjektInfoEintrag(
                                "Verbunden",
                                verbundeneSpieler.anzeigeTextOderNeutral(),
                            ),
                        ),
                    ),
                    spieler = verbundeneSpieler,
                ),
            )
        } + belegung.seewege.mapNotNull { seeweg ->
            val route = kuerzesterWasserweg(seeweg.hafenA, seeweg.hafenB)
                ?: return@mapNotNull null
            val gerichteteRoute = when (seeweg.richtung) {
                de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung.A_NACH_B -> route
                de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung.B_NACH_A ->
                    route.asReversed()
            }
            KantenObjektAuflage(
                position = gerichteteRoute.first(),
                typ = SpielObjektTyp(
                    name = "Frachtschiff ${seeweg.id}",
                    farbe = NeutralFarbe,
                    form = SpielObjektForm.FRACHTSCHIFF,
                    infos = listOf(
                        SpielObjektInfoEintrag("Spieler", seeweg.besitzer.wert),
                        SpielObjektInfoEintrag("Route", "${route.size} Kanten"),
                        SpielObjektInfoEintrag("Betrieb", "pendelt zwischen seinen Häfen"),
                    ),
                    spieler = setOf(seeweg.besitzer.wert),
                ),
                objektId = seeweg.id,
                bewegungsRoute = gerichteteRoute,
                routenStart = when (seeweg.richtung) {
                    de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung.A_NACH_B ->
                        seeweg.hafenA
                    de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung.B_NACH_A ->
                        seeweg.hafenB
                },
            )
        } + belegung.kriegseinheiten
            .groupBy { einheit ->
                TruppenStapelSchluessel(einheit.position, einheit.typ, einheit.besitzer)
            }
            .entries
            .sortedWith(
                compareBy<Map.Entry<TruppenStapelSchluessel, *>> { eintrag ->
                    eintrag.key.position.anfang
                }
                    .thenBy { eintrag -> eintrag.key.position.ende }
                    .thenBy { eintrag -> eintrag.key.besitzer.wert }
                    .thenBy { eintrag -> eintrag.key.typ.ordinal },
            )
            .map { (schluessel, einheiten) ->
                val ids = einheiten.map { einheit -> einheit.id }.sorted()
                KantenObjektAuflage(
                    position = schluessel.position,
                    typ = SpielObjektTyp(
                        name = if (ids.size == 1) {
                            "${schluessel.typ.anzeigeName()} ${ids.single()}"
                        } else {
                            "${ids.size} × ${schluessel.typ.anzeigeName()}"
                        },
                        farbe = spielerFarbe(schluessel.besitzer),
                        form = when (schluessel.typ) {
                            KriegsEinheitTyp.PANZER -> SpielObjektForm.PANZER
                            KriegsEinheitTyp.KRIEGSSCHIFF -> SpielObjektForm.KRIEGSSCHIFF
                        },
                        infos = listOf(
                            SpielObjektInfoEintrag("Spieler", schluessel.besitzer.wert),
                            SpielObjektInfoEintrag("Truppen", ids.size.toString()),
                            SpielObjektInfoEintrag(
                                "Bewegung",
                                "1 ${schluessel.typ.bewegungsRohstoff.anzeigeName()} " +
                                    "je Truppe und Kante",
                            ),
                        ),
                        spieler = setOf(schluessel.besitzer.wert),
                    ),
                    objektIds = ids,
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
        }.let(::listOfNotNull) + routenHervorhebung.map { kante ->
            KantenObjektAuflage(
                position = kante,
                typ = SpielObjektTyp(
                    name = "Frachtschiffroute",
                    farbe = RoutenFarbe,
                    form = SpielObjektForm.MARKIERUNG,
                    zustand = ObjektDarstellungsZustand.AUSGEWAEHLT,
                ),
            )
        },
        feldObjekte = belegung.felder.map { eintrag ->
            val effektiv = KartenAuswertung.effektiverZustand(this, eintrag, konflikte)
            val angeschlosseneSpieler = KartenAuswertung.anschlussStaerke(
                this,
                eintrag.position,
                konflikte,
            )
                .keys
                .mapTo(mutableSetOf(), SpielerId::wert)
            val zustand = when (effektiv) {
                AnlagenZustand.AKTIV -> ObjektDarstellungsZustand.INTAKT
                AnlagenZustand.VERLASSEN -> ObjektDarstellungsZustand.VERLASSEN
                AnlagenZustand.ZERSTOERT -> ObjektDarstellungsZustand.ZERSTOERT
            }
            val name = when (val anlage = eintrag.anlage) {
                FeldAnlage.Geschaeftsbank -> "Geschäftsbank"
                is FeldAnlage.Abbaueinheit -> "Abbaueinheit ${anlage.rohstoff.anzeigeName()}"
                is FeldAnlage.Wirtschaftsregion ->
                    anlage.bauteil.text.replaceFirstChar(Char::uppercase)
            }
            val bauteil = when (val anlage = eintrag.anlage) {
                FeldAnlage.Geschaeftsbank -> BauteilTyp.GESCHAEFTSBANK
                is FeldAnlage.Abbaueinheit -> null
                is FeldAnlage.Wirtschaftsregion -> anlage.bauteil
            }
            val abbauErtrag = (eintrag.anlage as? FeldAnlage.Abbaueinheit)
                ?.let { anlage -> mapOf(anlage.rohstoff to 1) }
                .orEmpty()
            FeldObjektAuflage(
                position = eintrag.position.zu3DPosition(),
                typ = SpielObjektTyp(
                    name = name,
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
                    zustand = zustand,
                    infos = bauwerkInfos(
                        spieler = angeschlosseneSpieler.anzeigeTextOderNeutral(),
                        gebaeude = name,
                        bauteil = bauteil,
                        ertrag = bauteil?.ertrag ?: abbauErtrag,
                        gebautInRunde = eintrag.gebautInRunde,
                        zustand = zustand,
                    ),
                    spieler = angeschlosseneSpieler,
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

private fun KriegsEinheitTyp.anzeigeName(): String = when (this) {
    KriegsEinheitTyp.PANZER -> "Panzer"
    KriegsEinheitTyp.KRIEGSSCHIFF -> "Kriegsschiff"
}

private fun EckGebaeudeTyp.zuForm(): SpielObjektForm = when (this) {
    EckGebaeudeTyp.HAUPTBAHNHOF -> SpielObjektForm.HAUPTBAHNHOF
    EckGebaeudeTyp.BAHNHOF -> SpielObjektForm.BAHNHOF
    EckGebaeudeTyp.GROSSBAHNHOF -> SpielObjektForm.GROSSBAHNHOF
    EckGebaeudeTyp.HAFEN -> SpielObjektForm.HAFEN
    EckGebaeudeTyp.GROSSHAFEN -> SpielObjektForm.GROSSHAFEN
}

private fun EckGebaeudeTyp.zuBauteilTyp(): BauteilTyp = when (this) {
    EckGebaeudeTyp.HAUPTBAHNHOF -> BauteilTyp.HAUPTBAHNHOF
    EckGebaeudeTyp.BAHNHOF -> BauteilTyp.BAHNHOF
    EckGebaeudeTyp.GROSSBAHNHOF -> BauteilTyp.GROSSBAHNHOF
    EckGebaeudeTyp.HAFEN -> BauteilTyp.HAFEN
    EckGebaeudeTyp.GROSSHAFEN -> BauteilTyp.GROSSHAFEN
}

private fun bauwerkInfos(
    spieler: String,
    gebaeude: String,
    bauteil: BauteilTyp?,
    ertrag: Map<Rohstoff, Int> = bauteil?.ertrag.orEmpty(),
    gebautInRunde: Int?,
    zustand: ObjektDarstellungsZustand,
    weitere: List<SpielObjektInfoEintrag> = emptyList(),
): List<SpielObjektInfoEintrag> = listOf(
    SpielObjektInfoEintrag("Spieler", spieler),
    SpielObjektInfoEintrag("Gebäude", gebaeude),
    SpielObjektInfoEintrag("Kosten", bauteil?.kosten.alsRohstoffText()),
    SpielObjektInfoEintrag("Erträge", ertrag.alsRohstoffText()),
    SpielObjektInfoEintrag("Verbrauch", bauteil?.verbrauch.alsRohstoffText()),
    SpielObjektInfoEintrag("Gebaut in Runde", gebautInRunde?.toString() ?: "nicht erfasst"),
    SpielObjektInfoEintrag("Zustand", zustand.anzeigeName()),
) + weitere

private fun Map<Rohstoff, Int>?.alsRohstoffText(): String = this
    ?.filterValues { menge -> menge != 0 }
    ?.entries
    ?.sortedBy { (rohstoff, _) -> rohstoff.ordinal }
    ?.joinToString { (rohstoff, menge) -> "$menge × ${rohstoff.anzeigeName()}" }
    .orEmpty()
    .ifBlank { "–" }

private fun Set<String>.anzeigeTextOderNeutral(): String =
    sorted().joinToString().ifBlank { "neutral" }

private fun Rohstoff.anzeigeName(): String = when (this) {
    Rohstoff.NAHRUNG -> "Nahrung"
    Rohstoff.LEHM -> "Lehm"
    Rohstoff.ZIEGEL -> "Ziegel"
    Rohstoff.HOLZ -> "Holz"
    Rohstoff.ROHOEL -> "Rohöl"
    Rohstoff.SCHWEROEL -> "Schweröl"
    Rohstoff.DIESEL -> "Diesel"
    Rohstoff.KOHLE -> "Kohle"
    Rohstoff.STAHL -> "Stahl"
    Rohstoff.EISEN -> "Eisen"
}

private fun BauwerkZustand.zuDarstellungsZustand(): ObjektDarstellungsZustand = when (this) {
    BauwerkZustand.INTAKT -> ObjektDarstellungsZustand.INTAKT
    BauwerkZustand.BELAGERT -> ObjektDarstellungsZustand.BELAGERT
    BauwerkZustand.ZERSTOERT -> ObjektDarstellungsZustand.ZERSTOERT
}

private fun ObjektDarstellungsZustand.anzeigeName(): String = when (this) {
    ObjektDarstellungsZustand.INTAKT -> "intakt"
    ObjektDarstellungsZustand.BELAGERT -> "belagert"
    ObjektDarstellungsZustand.ZERSTOERT -> "zerstört"
    ObjektDarstellungsZustand.VERLASSEN -> "verlassen"
    ObjektDarstellungsZustand.AUSGEWAEHLT -> "ausgewählt"
}

private fun Color.alsBelagert(): Color = Color(
    red = (red * 0.45f + 0.55f).coerceAtMost(1f),
    green = green * 0.38f,
    blue = blue * 0.38f,
    alpha = alpha,
)
