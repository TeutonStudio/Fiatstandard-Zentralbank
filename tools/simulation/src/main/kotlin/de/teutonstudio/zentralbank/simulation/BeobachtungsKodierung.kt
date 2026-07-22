package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.AKTUELLE_AKTIONS_SCHEMA_VERSION
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsAuswertung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.AKTUELLE_BEOBACHTUNGS_VERSION
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil
import kotlinx.serialization.Serializable

const val AKTUELLE_KODIERUNGS_VERSION = 2

@Serializable
data class KodierteAktion(
    val aktionsSchemaVersion: Int = AKTUELLE_AKTIONS_SCHEMA_VERSION,
    /** Vollständige, kanonische und damit kollisionsfreie Identität. */
    val kanonischeSerialisierung: String,
    val typIndex: Int,
    val numerischeMerkmale: FloatArray,
) {
    override fun equals(other: Any?): Boolean = other is KodierteAktion &&
        aktionsSchemaVersion == other.aktionsSchemaVersion &&
        kanonischeSerialisierung == other.kanonischeSerialisierung &&
        typIndex == other.typIndex && numerischeMerkmale.contentEquals(other.numerischeMerkmale)

    override fun hashCode(): Int = 31 * kanonischeSerialisierung.hashCode() + typIndex
}

@Serializable
data class ModellEingabe(
    val kodierungsVersion: Int = AKTUELLE_KODIERUNGS_VERSION,
    val beobachtungsVersion: Int = AKTUELLE_BEOBACHTUNGS_VERSION,
    val aktionsSchemaVersion: Int = AKTUELLE_AKTIONS_SCHEMA_VERSION,
    val spielstil: SpielerStil,
    val globaleMerkmale: FloatArray,
    val spielerMerkmale: Array<FloatArray>,
    val feldMerkmale: Array<FloatArray>,
    val eckMerkmale: Array<FloatArray>,
    val kantenMerkmale: Array<FloatArray>,
    val kandidaten: List<KodierteAktion>,
) {
    override fun equals(other: Any?): Boolean = other is ModellEingabe &&
        kodierungsVersion == other.kodierungsVersion &&
        beobachtungsVersion == other.beobachtungsVersion &&
        aktionsSchemaVersion == other.aktionsSchemaVersion &&
        spielstil == other.spielstil &&
        globaleMerkmale.contentEquals(other.globaleMerkmale) &&
        spielerMerkmale.contentDeepEquals(other.spielerMerkmale) &&
        feldMerkmale.contentDeepEquals(other.feldMerkmale) &&
        eckMerkmale.contentDeepEquals(other.eckMerkmale) &&
        kantenMerkmale.contentDeepEquals(other.kantenMerkmale) &&
        kandidaten == other.kandidaten

    override fun hashCode(): Int = 31 * kandidaten.hashCode() + globaleMerkmale.contentHashCode()
}

object BeobachtungsKodierung {
    fun kodiere(punkt: Entscheidungspunkt): ModellEingabe {
        val beobachtung = punkt.beobachtung
        require(beobachtung.beobachtungsVersion == AKTUELLE_BEOBACHTUNGS_VERSION)
        require(punkt.aktionsRaum.aktionsSchemaVersion == AKTUELLE_AKTIONS_SCHEMA_VERSION)
        val spieler = beobachtung.spieler.map { eintrag ->
            floatArrayOf(
                if (eintrag.id == beobachtung.betrachtenderSpieler) 1f else 0f,
                if (eintrag.amZug) 1f else 0f,
                if (eintrag.ausgeschieden) 1f else 0f,
                normiereGeld(eintrag.geld.cent),
                normiereGeld(eintrag.marktwert.cent),
                normiereAnzahl(eintrag.rohstoffe.sumOf { it.menge }),
                normiereAnzahl(eintrag.offeneEigeneAnleihen.size),
                normiereAnzahl(eintrag.einheiten.size),
            )
        }.toTypedArray()
        val felder = beobachtung.karte?.gelaendefelder.orEmpty().map { feld ->
            floatArrayOf(
                feld.position.zeile / 100f,
                feld.position.spalte / 100f,
                if (feld.position.haelfte.name == "OBEN") 1f else -1f,
                feld.gelaende.ordinal.toFloat(),
            )
        }.toTypedArray()
        val ecken = beobachtung.karte?.eckBauwerke.orEmpty().map { ecke ->
            floatArrayOf(
                ecke.position.x / 200f,
                ecke.position.y / 200f,
                ecke.typ.ordinal.toFloat(),
                beobachtung.spieler.indexOfFirst { it.id == ecke.besitzer }.toFloat(),
                ecke.zustand.ordinal.toFloat(),
            )
        }.toTypedArray()
        val kanten = beobachtung.karte?.handelslinien.orEmpty().map { kante ->
            floatArrayOf(
                kante.position.anfang.x / 200f,
                kante.position.anfang.y / 200f,
                kante.position.ende.x / 200f,
                kante.position.ende.y / 200f,
                beobachtung.spieler.indexOfFirst { it.id == kante.erbautVon }.toFloat(),
                kante.zustand.ordinal.toFloat(),
            )
        }.toTypedArray()
        val kandidaten = punkt.aktionsRaum.aktionen.map { aktion ->
            KodierteAktion(
                kanonischeSerialisierung = AktionsAuswertung.aktionsSchluessel(aktion),
                typIndex = aktionsTypIndex(aktion),
                numerischeMerkmale = numerischeMerkmale(aktion),
            )
        }
        check(kandidaten.map { it.kanonischeSerialisierung }.distinct().size == kandidaten.size) {
            "Die kanonische Aktionskodierung ist nicht injektiv."
        }
        return ModellEingabe(
            spielstil = beobachtung.eigeneWirtschaft.spielstil,
            globaleMerkmale = floatArrayOf(
                beobachtung.runde / 100f,
                beobachtung.markt.leitzinsBasispunkte / 2_000f,
                beobachtung.kriege.size.toFloat(),
                beobachtung.belagerungen.size.toFloat(),
                if (beobachtung.ergebnis != null) 1f else 0f,
            ),
            spielerMerkmale = spieler,
            feldMerkmale = felder,
            eckMerkmale = ecken,
            kantenMerkmale = kanten,
            kandidaten = kandidaten,
        )
    }

    private fun aktionsTypIndex(aktion: SpielAktion): Int = when (aktion) {
        is SpielAktion.HauptbahnhofPlatzieren -> 0
        is SpielAktion.EckGebaeudeBauen -> 1
        is SpielAktion.EckGebaeudeAufwerten -> 2
        is SpielAktion.SchieneBauen -> 3
        is SpielAktion.AnlageErrichten -> 4
        is SpielAktion.BelegungAbreissen -> 5
        is SpielAktion.SeewegEinrichten -> 6
        is SpielAktion.SeewegEntfernen -> 7
        is SpielAktion.KriegsEinheitBauen -> 8
        is SpielAktion.KriegsEinheitEinsetzen -> 9
        is SpielAktion.KriegsEinheitBewegen -> 10
        is SpielAktion.KriegsEinheitenBewegen -> 11
        is SpielAktion.VerwaltungsruineReparieren -> 12
        is SpielAktion.VerwaltungsruineAbreissen -> 13
        is SpielAktion.AnleiheEmittieren -> 14
        is SpielAktion.AnleiheFreiwilligZurueckkaufen -> 15
        is SpielAktion.AnleiheAufstocken -> 16
        is SpielAktion.SchuldenstrichDurchfuehren -> 17
        is SpielAktion.HandelsangebotErstellen -> 18
        is SpielAktion.HandelsangebotAnnehmen -> 19
        is SpielAktion.HandelsangebotAblehnen -> 20
        is SpielAktion.HandelsangebotZurueckziehen -> 21
        is SpielAktion.AnleihenangebotErstellen -> 22
        is SpielAktion.AnleihenangebotAnnehmen -> 23
        is SpielAktion.AnleihenangebotAblehnen -> 24
        is SpielAktion.AnleihenangebotZurueckziehen -> 25
        is SpielAktion.ProzugBeginnen -> 26
        is SpielAktion.VerarbeitungAusfuehren -> 27
        is SpielAktion.VerwaltungsstandortVersorgen -> 28
        is SpielAktion.VerbindlichkeitBegleichen -> 29
        is SpielAktion.ProzugAbschliessen -> 30
        is SpielAktion.ZahlungsunfaehigkeitFeststellen -> 31
        SpielAktion.ZugBeenden -> 32
        is SpielAktion.WarenkorbAendern -> 33
        is SpielAktion.RohstoffHandeln -> 34
        is SpielAktion.MitAuslandHandeln -> 35
        is SpielAktion.KriegErklaeren -> 36
        is SpielAktion.KriegsAllianzBeitreten -> 37
        is SpielAktion.WaffenstillstandAnbieten -> 38
        is SpielAktion.WaffenstillstandAnnehmen -> 39
        is SpielAktion.KriegKapitulieren -> 40
        is SpielAktion.FriedensvertragVorschlagen -> 41
        is SpielAktion.FriedensvertragAnnehmen -> 42
        is SpielAktion.UnabhaengigenFriedenSchliessen -> 43
        is SpielAktion.RessourcenUebertragen -> 44
    }

    private fun numerischeMerkmale(aktion: SpielAktion): FloatArray = when (aktion) {
        is SpielAktion.AnleiheEmittieren -> floatArrayOf(
            normiereGeld(aktion.nennwert.cent),
            aktion.zinsBasispunkte / 10_000f,
            aktion.laufzeitRunden / 20f,
        )
        is SpielAktion.AnleiheAufstocken -> floatArrayOf(
            normiereGeld(aktion.neuerNennwert.cent),
            aktion.zinsBasispunkte / 10_000f,
            aktion.laufzeitRunden / 20f,
        )
        is SpielAktion.RohstoffHandeln -> floatArrayOf(
            aktion.menge / 100f,
            normiereGeld(aktion.preis.cent),
        )
        is SpielAktion.MitAuslandHandeln -> floatArrayOf(
            aktion.menge / 100f,
            normiereGeld(aktion.preis.cent),
        )
        else -> FloatArray(3)
    }

    private fun normiereGeld(cent: Long): Float = (cent / 100_000f).coerceIn(-10f, 10f)
    private fun normiereAnzahl(anzahl: Int): Float = (anzahl / 100f).coerceIn(0f, 10f)
}
