package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenDreieck
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.SpezialfeldTyp

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

private val SpezialDarstellung = mapOf(
    SpezialfeldTyp.HEXAGON to DreieckTyp(
        "Spezial-Hexagon",
        Color(0xFF7E57C2),
        metallisch = 0.22f,
    ),
    SpezialfeldTyp.STADT to DreieckTyp(
        "Stadt",
        Color(0xFFE0E0E0),
        metallisch = 0.48f,
        rauheit = 0.55f,
    ),
    SpezialfeldTyp.HAFEN to DreieckTyp(
        "Hafen",
        Color(0xFFFFA726),
        metallisch = 0.18f,
        rauheit = 0.68f,
    ),
)

fun Spielkarte.zu3DModell(
    zeigeBearbeitungsRaster: Boolean = false,
): Spielbrett3DModell = Spielbrett3DModell(
    zeilen = zeilen,
    spalten = spalten,
    startZeile = startZeile,
    startSpalte = startSpalte,
    zeigeBearbeitungsRaster = zeigeBearbeitungsRaster,
    auflagen = buildList {
        landfelder.forEach { landfeld ->
            add(
                DreieckAuflage(
                    position = landfeld.position.zu3DPosition(),
                    typ = GelaendeDarstellung.getValue(landfeld.gelaende),
                    ebene = AuflagenEbene.LAND,
                ),
            )
        }
        spezialfelder.forEach { spezialfeld ->
            spezialfeld.positionen.forEach { position ->
                add(
                    DreieckAuflage(
                        position = position.zu3DPosition(),
                        typ = SpezialDarstellung.getValue(spezialfeld.typ),
                        ebene = AuflagenEbene.SPEZIAL,
                    ),
                )
            }
        }
    },
)

internal fun KartenDreieck.zu3DPosition(): DreieckPosition = DreieckPosition(
    zeile = zeile,
    spalte = spalte,
    ausrichtung = when (haelfte) {
        DreieckHaelfte.OBEN -> DreieckAusrichtung.OBEN
        DreieckHaelfte.UNTEN -> DreieckAusrichtung.UNTEN
    },
)

internal fun DreieckPosition.zuKartenDreieck(): KartenDreieck = KartenDreieck(
    zeile = zeile,
    spalte = spalte,
    haelfte = when (ausrichtung) {
        DreieckAusrichtung.OBEN -> DreieckHaelfte.OBEN
        DreieckAusrichtung.UNTEN -> DreieckHaelfte.UNTEN
    },
)

internal fun SpielbrettGeometrie.kartenHexagonUm(
    treffer: DreieckTreffer,
): List<KartenDreieck> = hexagonUm(treffer).map(DreieckPosition::zuKartenDreieck)
