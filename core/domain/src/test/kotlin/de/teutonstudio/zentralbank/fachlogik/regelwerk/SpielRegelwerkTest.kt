package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.auswertung.FinanzAuswertung
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FeldBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.Friedensvertrag
import de.teutonstudio.zentralbank.fachlogik.modell.FriedensvertragId
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.ProzugStatus
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielRegelwerkTest {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")
    private val feld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
    private val kante = feld.kanten().first()

    @Test
    fun nichtVersorgterBahnhofGibtErtragErstNachRestversorgungFrei() {
        val start = zustand(
            annaRohstoffe = mapOf(Rohstoff.KOHLE to 1),
            karte = karte(FeldAnlage.Abbaueinheit(Rohstoff.NAHRUNG)),
        )

        val begonnen = anwenden(start, SpielEreignis.ProzugBegonnen(1L))
        val doppelt = SpielRegelwerk.wendeAn(begonnen, SpielEreignis.ProzugBegonnen(1L))
        val bahnhofEcke = feld.ecken().last()
        val verpflichtung = begonnen.zugStatus!!.prozug.verwaltungsVerpflichtungen
            .first { it.id.ecke == bahnhofEcke }

        assertEquals(mapOf(Rohstoff.KOHLE to 1), begonnen.spieler.first().rohstoffe)
        assertEquals(mapOf(Rohstoff.KOHLE to 1), verpflichtung.bedarf)
        assertEquals(mapOf(Rohstoff.NAHRUNG to 1), verpflichtung.eigenversorgung)
        assertEquals(mapOf(Rohstoff.NAHRUNG to 1), verpflichtung.auszahlbarerErtrag)
        assertTrue(doppelt.isFailure)

        val versorgt = anwenden(
            begonnen,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, bahnhofEcke),
        )

        assertEquals(mapOf(Rohstoff.NAHRUNG to 1), versorgt.spieler.first().rohstoffe)
        assertEquals(
            mapOf(Rohstoff.NAHRUNG to 1),
            versorgt.zugStatus!!.prozug.abbauErtraege,
        )
    }

    @Test
    fun vollstaendigeEigenversorgungGeschiehtAutomatisch() {
        val bahnhofEcke = feld.ecken().last()
        val begonnen = anwenden(
            zustand(karte = karteMitEigenversorgtemBahnhof()),
            SpielEreignis.ProzugBegonnen(1L),
        )
        val verpflichtung = begonnen.zugStatus!!.prozug.verwaltungsVerpflichtungen
            .first { it.id.ecke == bahnhofEcke }

        assertTrue(verpflichtung.bedarf.isEmpty())
        assertEquals(
            mapOf(Rohstoff.NAHRUNG to 1, Rohstoff.KOHLE to 1),
            verpflichtung.eigenversorgung,
        )
        assertEquals(
            mapOf(Rohstoff.NAHRUNG to 1, Rohstoff.KOHLE to 1),
            verpflichtung.auszahlbarerErtrag,
        )
        assertTrue(verpflichtung.id in begonnen.zugStatus!!.prozug.versorgteStandorte)
        assertEquals(
            mapOf(Rohstoff.NAHRUNG to 1, Rohstoff.KOHLE to 1),
            begonnen.spieler.first().rohstoffe,
        )
        assertTrue(
            SpielRegelwerk.wendeAn(
                begonnen,
                SpielEreignis.VerwaltungsstandortVersorgt(1L, bahnhofEcke),
            ).isFailure,
        )
    }

    @Test
    fun blockierteHandelslinieVerhindertAbbauImProzug() {
        val grundkarte = karte(FeldAnlage.Abbaueinheit(Rohstoff.NAHRUNG))
        val blockierteLinie = KartenKante.zwischen(feld.ecken().last(), kante.anfang)
        val blockierteKarte = grundkarte.copy(
            belegung = grundkarte.belegung.copy(
                kanten = listOf(KantenBelegung(blockierteLinie)),
                kriegseinheiten = listOf(
                    KriegsEinheitBelegung(
                        id = "panzer-bert-blockade",
                        typ = KriegsEinheitTyp.PANZER,
                        besitzer = bert,
                        ort = KartenOrt.Kante(blockierteLinie),
                    ),
                ),
            ),
        )
        val start = zustand(karte = blockierteKarte).copy(
            konflikte = setOf(Konflikt(anna, bert)),
        )

        val begonnen = anwenden(start, SpielEreignis.ProzugBegonnen(1L))

        assertTrue(begonnen.spieler.first { it.id == anna }.rohstoffe.isEmpty())
        assertTrue(begonnen.zugStatus!!.prozug.abbauErtraege.isEmpty())
    }

    @Test
    fun verarbeitungBuchtEinsatzUndAlleProdukteAtomar() {
        val start = zustand(
            annaRohstoffe = mapOf(
                Rohstoff.NAHRUNG to 4,
                Rohstoff.KOHLE to 4,
                Rohstoff.ROHOEL to 4,
            ),
            karte = karte(FeldAnlage.Wirtschaftsregion(BauteilTyp.RAFFINERIE)),
        )
        val begonnen = anwenden(start, SpielEreignis.ProzugBegonnen(1L))
        assertTrue(
            SpielRegelwerk.wendeAn(
                begonnen,
                SpielEreignis.VerarbeitungAusgefuehrt(1L, feld, 1),
            ).isFailure,
        )
        val bahnhofVersorgt = anwenden(
            begonnen,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, feld.ecken().last()),
        )
        val verwaltungVersorgt = anwenden(
            bahnhofVersorgt,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, kante.anfang),
        )

        val verarbeitet = anwenden(
            verwaltungVersorgt,
            SpielEreignis.VerarbeitungAusgefuehrt(1L, feld, 1),
        )
        val annaDanach = verarbeitet.spieler.first()

        assertEquals(2, annaDanach.rohstoffe[Rohstoff.ROHOEL])
        assertEquals(2, annaDanach.rohstoffe[Rohstoff.DIESEL])
        assertEquals(1, annaDanach.rohstoffe[Rohstoff.SCHWEROEL])
        assertTrue(
            SpielRegelwerk.wendeAn(
                verarbeitet,
                SpielEreignis.VerarbeitungAusgefuehrt(1L, feld, 1),
            ).isFailure,
        )
        assertTrue(
            SpielRegelwerk.wendeAn(
                verwaltungVersorgt,
                SpielEreignis.VerarbeitungAusgefuehrt(1L, feld, 2),
            ).isFailure,
        )
    }

    @Test
    fun prozugKannOhneFreiwilligeVerarbeitungAbgeschlossenWerden() {
        val begonnen = anwenden(
            zustand(
                annaRohstoffe = mapOf(
                    Rohstoff.NAHRUNG to 4,
                    Rohstoff.KOHLE to 4,
                    Rohstoff.ROHOEL to 2,
                ),
                karte = karte(FeldAnlage.Wirtschaftsregion(BauteilTyp.RAFFINERIE)),
            ),
            SpielEreignis.ProzugBegonnen(1L),
        )
        val bahnhofVersorgt = anwenden(
            begonnen,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, feld.ecken().last()),
        )
        val hauptbahnhofVersorgt = anwenden(
            bahnhofVersorgt,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, kante.anfang),
        )

        val epizug = anwenden(
            hauptbahnhofVersorgt,
            SpielEreignis.ProzugErfolgreichAbgeschlossen(1L),
        )

        assertEquals(ZugPhase.Epizug, epizug.zugStatus?.phase)
        assertEquals(2, epizug.spieler.first().rohstoffe[Rohstoff.ROHOEL])
        assertTrue(epizug.zugStatus?.prozug?.produktionsBuchungen.orEmpty().isEmpty())
    }

    @Test
    fun fehlenderEinsatzLaesstZustandUnveraendert() {
        val begonnen = anwenden(
            zustand(
                annaRohstoffe = mapOf(
                    Rohstoff.NAHRUNG to 4,
                    Rohstoff.KOHLE to 4,
                    Rohstoff.ROHOEL to 1,
                ),
                karte = karte(FeldAnlage.Wirtschaftsregion(BauteilTyp.RAFFINERIE)),
            ),
            SpielEreignis.ProzugBegonnen(1L),
        )
        val bahnhofVersorgt = anwenden(
            begonnen,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, feld.ecken().last()),
        )
        val verwaltungVersorgt = anwenden(
            bahnhofVersorgt,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, kante.anfang),
        )

        val ergebnis = SpielRegelwerk.wendeAn(
            verwaltungVersorgt,
            SpielEreignis.VerarbeitungAusgefuehrt(1L, feld, 1),
        )

        assertTrue(ergebnis.isFailure)
        assertEquals(mapOf(Rohstoff.ROHOEL to 1), verwaltungVersorgt.spieler.first().rohstoffe)
    }

    @Test
    fun verwaltungsstandortMussMitVollstaendigemRezeptVersorgtWerden() {
        val begonnen = anwenden(
            zustand(
                annaRohstoffe = mapOf(Rohstoff.NAHRUNG to 1, Rohstoff.KOHLE to 1),
                karte = karte(FeldAnlage.Geschaeftsbank),
            ),
            SpielEreignis.ProzugBegonnen(1L),
        )
        val ecke = feld.ecken().last()

        val versorgt = anwenden(
            begonnen,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, ecke),
        )

        assertTrue(versorgt.spieler.first().rohstoffe.isEmpty())
        assertTrue(
            SpielRegelwerk.wendeAn(
                versorgt,
                SpielEreignis.VerwaltungsstandortVersorgt(1L, ecke),
            ).isFailure,
        )
    }

    @Test
    fun faelligeZahlungHatFestgeschriebenenEmpfaengerUndIstSummenneutral() {
        val anleiheId = AnleiheId("a-1")
        val start = zustand(
            annaGeld = Geld.mark(10),
            bertGeld = Geld.mark(2),
        ).copy(
            rundenzähler = 1,
            anleihen = mapOf(
                anleiheId to Anleihe(
                    id = anleiheId,
                    emittent = anna,
                    nennwert = Geld.mark(20),
                    zinsBasispunkte = 500,
                    laufzeitRunden = 3,
                    zinsbetrag = Geld.mark(1),
                    emissionsRunde = 0,
                ),
            ),
            spieler = zustand(Geld.mark(10), Geld.mark(2)).spieler.map { spieler ->
                if (spieler.id == bert) spieler.copy(anleihen = listOf(anleiheId)) else spieler
            },
        )
        val begonnen = anwenden(start, SpielEreignis.ProzugBegonnen(1L))
        val verbindlichkeit = begonnen.zugStatus!!.prozug.verbindlichkeiten.single()

        val bezahlt = anwenden(
            begonnen,
            SpielEreignis.VerbindlichkeitBeglichen(1L, verbindlichkeit.id),
        )

        assertEquals(FinanzAuswertung.geldsumme(begonnen), FinanzAuswertung.geldsumme(bezahlt))
        assertEquals(Geld.mark(9), bezahlt.spieler.first { it.id == anna }.geldkonto)
        assertEquals(Geld.mark(3), bezahlt.spieler.first { it.id == bert }.geldkonto)
        assertTrue(
            SpielRegelwerk.wendeAn(
                bezahlt,
                SpielEreignis.VerbindlichkeitBeglichen(1L, verbindlichkeit.id),
            ).isFailure,
        )
    }

    @Test
    fun prozugMitOffenemPostenKannNichtAbgeschlossenWerden() {
        val begonnen = anwenden(
            zustand(
                annaRohstoffe = mapOf(Rohstoff.NAHRUNG to 4, Rohstoff.KOHLE to 4),
                karte = karte(FeldAnlage.Geschaeftsbank),
            ),
            SpielEreignis.ProzugBegonnen(1L),
        )

        val gesperrt = SpielRegelwerk.wendeAn(
            begonnen,
            SpielEreignis.ProzugErfolgreichAbgeschlossen(1L),
        )
        val bahnhofVersorgt = anwenden(
            begonnen,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, feld.ecken().last()),
        )
        val hauptbahnhofVersorgt = anwenden(
            bahnhofVersorgt,
            SpielEreignis.VerwaltungsstandortVersorgt(1L, kante.anfang),
        )
        val epizug = anwenden(
            hauptbahnhofVersorgt,
            SpielEreignis.ProzugErfolgreichAbgeschlossen(1L),
        )

        assertTrue(gesperrt.isFailure)
        assertEquals(2, begonnen.zugStatus?.prozug?.verwaltungsVerpflichtungen?.size)
        assertEquals(ZugPhase.Epizug, epizug.zugStatus?.phase)
        assertEquals(anna, epizug.aktiverSpieler)
    }

    @Test
    fun handelIstImProzugErlaubtAberBauUndKriegSindGesperrt() {
        val begonnen = anwenden(
            zustand(
                annaGeld = Geld.mark(5),
                bertRohstoffe = mapOf(Rohstoff.HOLZ to 1),
            ),
            SpielEreignis.ProzugBegonnen(1L),
        )

        val gehandelt = anwenden(
            begonnen,
            SpielEreignis.RohstoffHandel(
                kaeufer = anna,
                verkaeufer = bert,
                rohstoff = Rohstoff.HOLZ,
                menge = 1,
                preis = Geld.mark(1),
            ),
        )

        assertEquals(1, gehandelt.spieler.first { it.id == anna }.rohstoffe[Rohstoff.HOLZ])
        assertTrue(
            SpielRegelwerk.wendeAn(
                begonnen,
                SpielEreignis.Expansion(anna, BauteilTyp.EISENBAHNLINIE),
            ).isFailure,
        )
        assertTrue(
            SpielRegelwerk.wendeAn(
                begonnen,
                SpielEreignis.KriegErklaert(anna, bert),
            ).isFailure,
        )
    }

    @Test
    fun kriegserklaerungUndFriedenFunktionierenImEpizug() {
        val begonnen = anwenden(zustand(), SpielEreignis.ProzugBegonnen(1L))
        val epizug = anwenden(
            begonnen,
            SpielEreignis.ProzugErfolgreichAbgeschlossen(1L),
        )

        val imKrieg = anwenden(epizug, SpielEreignis.KriegErklaert(anna, bert))
        val krieg = imKrieg.konflikte.single()
        val imFrieden = anwenden(
            imKrieg,
            SpielEreignis.FriedensvertragAbgeschlossen(
                Friedensvertrag(
                    id = FriedensvertragId("frieden-test"),
                    krieg = krieg.id,
                    beteiligteSpieler = setOf(anna, bert),
                    unentschiedeneTeilnehmer = setOf(anna, bert),
                    angenommenVon = setOf(anna, bert),
                ),
            ),
        )

        assertTrue(imKrieg.konflikte.single().betrifft(anna, bert))
        assertTrue(imFrieden.konflikte.isEmpty())
    }

    @Test
    fun bankEmissionSchoepftGeldUndErzeugtKeineSofortigeFaelligkeit() {
        val begonnen = anwenden(zustand(), SpielEreignis.ProzugBegonnen(1L))
        val anleihe = Anleihe(
            id = AnleiheId("neu"),
            emittent = anna,
            nennwert = Geld.mark(10),
            zinsBasispunkte = 500,
            laufzeitRunden = 2,
            emissionsRunde = 0,
        )

        val danach = anwenden(
            begonnen,
            SpielEreignis.AnleiheEmittiert(anleihe, KontoId.Bank, Geld.mark(10)),
        )

        assertEquals(Geld.mark(10), danach.spieler.first { it.id == anna }.geldkonto)
        assertEquals(Geld.NULL, danach.bankkonto)
        assertTrue(anleihe.id in danach.bankAnleihen)
        assertTrue(danach.zugStatus!!.prozug.verbindlichkeiten.isEmpty())
    }

    @Test
    fun zugendeIstNurImEpizugMoeglichUndErhoehtZugkennung() {
        val start = zustand()
        assertTrue(SpielRegelwerk.wendeAn(start, SpielEreignis.ZugBeendet).isFailure)
        val begonnen = anwenden(start, SpielEreignis.ProzugBegonnen(1L))
        val epizug = anwenden(
            begonnen,
            SpielEreignis.ProzugErfolgreichAbgeschlossen(1L),
        )

        val naechster = anwenden(epizug, SpielEreignis.ZugBeendet)

        assertEquals(bert, naechster.aktiverSpieler)
        assertEquals(2L, naechster.zugStatus?.zugId)
        assertEquals(ZugPhase.Prozug, naechster.zugStatus?.phase)
        assertFalse(naechster.zugStatus!!.prozug.begonnen)
    }

    private fun zustand(
        annaGeld: Geld = Geld.NULL,
        bertGeld: Geld = Geld.NULL,
        annaRohstoffe: Map<Rohstoff, Int> = emptyMap(),
        bertRohstoffe: Map<Rohstoff, Int> = emptyMap(),
        karte: Spielkarte? = null,
    ) = SpielZustand(
        spieler = listOf(
            Spieler(anna, "Anna", rohstoffe = annaRohstoffe, geldkonto = annaGeld),
            Spieler(bert, "Bert", rohstoffe = bertRohstoffe, geldkonto = bertGeld),
        ),
        karte = karte,
        aktiverSpieler = anna,
        zugStatus = ZugStatus(1L, anna, ZugPhase.Prozug),
    )

    private fun karte(anlage: FeldAnlage): Spielkarte {
        val verbindungsKante = KartenKante.zwischen(
            feld.ecken().last(),
            kante.anfang,
        )
        val land = (angrenzendeFelder(kante) + angrenzendeFelder(verbindungsKante))
            .distinct()
            .map { GelaendeFeld(it, GelaendeTyp.EBENE) }
        return Spielkarte(
            id = "prozug-test",
            name = "Prozug-Test",
            hexagon = KartenHexagon(radius = 8),
            gelaendefelder = land,
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(kante.anfang, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                    EckBelegung(feld.ecken().last(), EckGebaeudeTyp.BAHNHOF, anna),
                ),
                kanten = listOf(KantenBelegung(kante), KantenBelegung(verbindungsKante)),
                felder = listOf(FeldBelegung(feld, anlage)),
            ),
        )
    }

    private fun karteMitEigenversorgtemBahnhof(): Spielkarte {
        val basis = karte(FeldAnlage.Abbaueinheit(Rohstoff.NAHRUNG))
        val bahnhofEcke = feld.ecken().last()
        val kohleFeld = angrenzendeFelder(bahnhofEcke).first { kandidat -> kandidat != feld }
        return basis.copy(
            gelaendefelder = (
                basis.gelaendefelder + GelaendeFeld(kohleFeld, GelaendeTyp.EBENE)
            ).distinctBy(GelaendeFeld::position),
            belegung = basis.belegung.copy(
                felder = basis.belegung.felder + FeldBelegung(
                    kohleFeld,
                    FeldAnlage.Abbaueinheit(Rohstoff.KOHLE),
                ),
            ),
        )
    }

    private fun anwenden(zustand: SpielZustand, ereignis: SpielEreignis): SpielZustand =
        SpielRegelwerk.wendeAn(zustand, ereignis).getOrThrow()
}
