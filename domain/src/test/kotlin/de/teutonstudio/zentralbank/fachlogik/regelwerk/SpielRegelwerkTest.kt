package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.ereignis.TransaktionsGrund
import de.teutonstudio.zentralbank.fachlogik.auswertung.FinanzAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittTyp
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielRegelwerkTest {
    private val annaId = SpielerId("Anna")
    private val berndId = SpielerId("Bernd")
    private val anleiheId = AnleiheId("anna-1")

    private fun startState(): SpielZustand = SpielZustand(
        spieler = listOf(
            Spieler(
                id = annaId,
                name = "Anna",
                rohstoffe = mapOf(Rohstoff.HOLZ to 2, Rohstoff.STAHL to 1),
                geldkonto = Geld.mark(10),
                anleihen = listOf(anleiheId),
            ),
            Spieler(
                id = berndId,
                name = "Bernd",
                rohstoffe = emptyMap(),
                geldkonto = Geld.mark(5),
            ),
        ),
        bankkonto = Geld.mark(100),
        anleihen = mapOf(
            anleiheId to Anleihe(
                id = anleiheId,
                emittent = annaId,
                nennwert = Geld.mark(8),
                zinsBasispunkte = 500,
                laufzeitRunden = 4,
            ),
        ),
        zugStatus = null,
    )

    @Test
    fun warenkorbAenderungErsetztZusammensetzungUndEntferntNullmengen() {
        val state = SpielRegelwerk.wendeAn(
            startState(),
            SpielEreignis.WarenkorbGeaendert(
                mapOf(
                    Rohstoff.HOLZ to 4,
                    Rohstoff.STAHL to 0,
                )
            ),
        ).getOrThrow()

        assertEquals(mapOf(Rohstoff.HOLZ to 4), state.warenkorb)
    }

    @Test
    fun warenkorbAenderungLehntNegativeMengenAb() {
        val result = SpielRegelwerk.wendeAn(
            startState(),
            SpielEreignis.WarenkorbGeaendert(mapOf(Rohstoff.HOLZ to -1)),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rohstoffEinnahmeErhoehtBestand() {
        val state = SpielRegelwerk.wendeAn(
            startState(),
            SpielEreignis.RohstoffEinnahme(annaId, mapOf(Rohstoff.HOLZ to 3)),
        ).getOrThrow()

        assertEquals(5, state.spieler.first { it.id == annaId }.rohstoffe[Rohstoff.HOLZ])
    }

    @Test
    fun rohstoffAusgabeLehntNegativenBestandAb() {
        val result = SpielRegelwerk.wendeAn(
            startState(),
            SpielEreignis.RohstoffAusgabe(berndId, mapOf(Rohstoff.HOLZ to 1)),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun transaktionIstSummenneutral() {
        val start = startState()
        val state = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.Transaktion(
                von = KontoId.Spieler(annaId),
                an = KontoId.Spieler(berndId),
                betrag = Geld.mark(3),
                grund = TransaktionsGrund.SONSTIGES,
            ),
        ).getOrThrow()

        assertEquals(FinanzAuswertung.geldsumme(start), FinanzAuswertung.geldsumme(state))
        assertEquals(Geld.mark(7), state.spieler.first { it.id == annaId }.geldkonto)
        assertEquals(Geld.mark(8), state.spieler.first { it.id == berndId }.geldkonto)
    }

    @Test
    fun transaktionLehntUnterdeckungAb() {
        val result = SpielRegelwerk.wendeAn(
            startState(),
            SpielEreignis.Transaktion(
                von = KontoId.Spieler(berndId),
                an = KontoId.Bank,
                betrag = Geld.mark(6),
                grund = TransaktionsGrund.SONSTIGES,
            ),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rohstoffHandelVerschiebtRohstoffUndGeldSummenneutral() {
        val start = startState()
        val state = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.RohstoffHandel(
                kaeufer = berndId,
                verkaeufer = annaId,
                rohstoff = Rohstoff.HOLZ,
                menge = 1,
                preis = Geld.mark(2),
            ),
        ).getOrThrow()

        assertEquals(FinanzAuswertung.geldsumme(start), FinanzAuswertung.geldsumme(state))
        assertEquals(1, state.spieler.first { it.id == annaId }.rohstoffe[Rohstoff.HOLZ])
        assertEquals(1, state.spieler.first { it.id == berndId }.rohstoffe[Rohstoff.HOLZ])
        assertEquals(Geld.mark(12), state.spieler.first { it.id == annaId }.geldkonto)
        assertEquals(Geld.mark(3), state.spieler.first { it.id == berndId }.geldkonto)
    }

    @Test
    fun anleiheVerkauftVerschiebtAnleiheUndGeldSummenneutral() {
        val start = startState()
        val state = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.AnleiheVerkauft(
                anleihe = anleiheId,
                verkaeufer = annaId,
                kaeufer = KontoId.Spieler(berndId),
                preis = Geld.mark(4),
            ),
        ).getOrThrow()

        assertEquals(FinanzAuswertung.geldsumme(start), FinanzAuswertung.geldsumme(state))
        assertTrue(anleiheId !in state.spieler.first { it.id == annaId }.anleihen)
        assertTrue(anleiheId in state.spieler.first { it.id == berndId }.anleihen)
        assertEquals(Geld.mark(14), state.spieler.first { it.id == annaId }.geldkonto)
        assertEquals(Geld.mark(1), state.spieler.first { it.id == berndId }.geldkonto)
    }

    @Test
    fun anleiheFaelligZahltNennwertUndEntferntAnleihe() {
        val start = startState().copy(
            spieler = startState().spieler.map { spieler ->
                when (spieler.id) {
                    annaId -> spieler.copy(anleihen = emptyList())
                    berndId -> spieler.copy(anleihen = listOf(anleiheId))
                    else -> spieler
                }
            },
        )
        val state = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.AnleiheFaellig(anleiheId),
        ).getOrThrow()

        assertTrue(anleiheId !in state.spieler.first { it.id == annaId }.anleihen)
        assertTrue(anleiheId !in state.spieler.first { it.id == berndId }.anleihen)
        assertEquals(FinanzAuswertung.geldsumme(start), FinanzAuswertung.geldsumme(state))
        assertEquals(Geld.mark(2), state.spieler.first { it.id == annaId }.geldkonto)
        assertEquals(Geld.mark(13), state.spieler.first { it.id == berndId }.geldkonto)
        assertEquals(0, state.anleihen.values.count { it.id == anleiheId })
    }

    @Test
    fun expansionVerbrauchtRohstoffeUndErhoehtBauteilbestand() {
        val state = SpielRegelwerk.wendeAn(
            startState(),
            SpielEreignis.Expansion(
                spieler = annaId,
                bauteil = BauteilTyp.EISENBAHNLINIE,
            ),
        ).getOrThrow()

        val anna = state.spieler.first { it.id == annaId }
        assertEquals(1, anna.rohstoffe[Rohstoff.HOLZ])
        assertEquals(null, anna.rohstoffe[Rohstoff.STAHL])
        assertEquals(1, anna.bauteile[BauteilTyp.EISENBAHNLINIE])
    }

    @Test
    fun kriegErklaertUndBeendetKonflikt() {
        val imKrieg = SpielRegelwerk.wendeAn(
            startState(),
            SpielEreignis.KriegErklaert(
                aggressor = annaId,
                verteidiger = berndId,
            ),
        ).getOrThrow()

        assertEquals(1, imKrieg.konflikte.size)

        val frieden = SpielRegelwerk.wendeAn(
            imKrieg,
            SpielEreignis.KriegBeendet(
                spielerA = berndId,
                spielerB = annaId,
            ),
        ).getOrThrow()

        assertEquals(0, frieden.konflikte.size)
    }

    @Test
    fun schuldenstrichBautAbZahltSpieleranleihenFrischAusUndUeberspringtAktionen() {
        val bankAnleihe = AnleiheId("anna-bank")
        val spielerAnleihe = AnleiheId("anna-player")
        val start = SpielZustand(
            spieler = listOf(
                Spieler(
                    id = annaId,
                    name = "Anna",
                    geldkonto = Geld.mark(1),
                    bauteile = mapOf(
                        BauteilTyp.BAHNHOF to 2,
                        BauteilTyp.HAFEN to 1,
                        BauteilTyp.GROSSBAHNHOF to 1,
                        BauteilTyp.GROSSHAFEN to 1,
                        BauteilTyp.EISENBAHNLINIE to 5,
                    ),
                ),
                Spieler(
                    id = berndId,
                    name = "Bernd",
                    geldkonto = Geld.mark(5),
                    anleihen = listOf(spielerAnleihe),
                ),
            ),
            bankAnleihen = listOf(bankAnleihe),
            anleihen = mapOf(
                bankAnleihe to Anleihe(
                    id = bankAnleihe,
                    emittent = annaId,
                    nennwert = Geld.mark(7),
                    zinsBasispunkte = 500,
                    laufzeitRunden = 4,
                ),
                spielerAnleihe to Anleihe(
                    id = spielerAnleihe,
                    emittent = annaId,
                    nennwert = Geld.mark(8),
                    zinsBasispunkte = 500,
                    laufzeitRunden = 4,
                ),
            ),
            aktiverSpieler = annaId,
            zugStatus = ZugStatus(
                spieler = annaId,
                phase = Phase.Ausgaben,
                erledigteSchritte = setOf(SchrittTyp.ROHSTOFF_AUSGABEN),
            ),
        )

        val state = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.Schuldenstrich(annaId, entfernteBahnwege = 3),
        ).getOrThrow()

        val anna = state.spieler.first { it.id == annaId }
        val bernd = state.spieler.first { it.id == berndId }
        assertEquals(null, anna.bauteile[BauteilTyp.GROSSBAHNHOF])
        assertEquals(null, anna.bauteile[BauteilTyp.GROSSHAFEN])
        assertEquals(1, anna.bauteile[BauteilTyp.BAHNHOF])
        assertEquals(1, anna.bauteile[BauteilTyp.HAFEN])
        assertEquals(2, anna.bauteile[BauteilTyp.EISENBAHNLINIE])
        assertEquals(Geld.mark(13), bernd.geldkonto)
        assertTrue(state.bankAnleihen.isEmpty())
        assertTrue(state.anleihen.isEmpty())
        assertTrue(state.spieler.all { it.anleihen.isEmpty() })
        assertEquals(1, state.schuldenstriche.size)
        assertEquals(Geld.mark(8), state.schuldenstriche.single().ausgezahlterBetrag)
        assertEquals(berndId, state.aktiverSpieler)
        assertEquals(Phase.Einnahmen, state.zugStatus?.phase)
    }

    @Test
    fun schuldenstrichImKriegWirdAbgelehnt() {
        val result = SpielRegelwerk.wendeAn(
            startState().copy(
                bankAnleihen = listOf(anleiheId),
                konflikte = setOf(Konflikt(annaId, berndId)),
                aktiverSpieler = annaId,
                zugStatus = ZugStatus(
                    spieler = annaId,
                    phase = Phase.Ausgaben,
                    erledigteSchritte = setOf(SchrittTyp.ROHSTOFF_AUSGABEN),
                ),
            ),
            SpielEreignis.Schuldenstrich(annaId, entfernteBahnwege = 0),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun zugendeZaehltFriedlicheUeberschuldungUndMarkiertWarnungUndFaelligkeit() {
        var state = ueberschuldeterAnnaZug()

        repeat(3) {
            state = annaZugBeenden(state)
        }

        val warnung = state.ueberschuldungen.single()
        assertEquals(3, warnung.friedlicheUeberschuldeteZuege)
        assertEquals(Geld.mark(110), warnung.schuldensumme)
        assertEquals(Geld.mark(7), warnung.marktwert)
        assertTrue(warnung.warnungAktiv)
        assertFalse(warnung.schuldenstrichFaellig)

        state = annaZugBeenden(state)

        val faellig = state.ueberschuldungen.single()
        assertEquals(4, faellig.friedlicheUeberschuldeteZuege)
        assertTrue(faellig.warnungAktiv)
        assertTrue(faellig.schuldenstrichFaellig)
        assertEquals(annaId, state.aktiverSpieler)
        assertEquals(Phase.Aktionen, state.zugStatus?.phase)
    }

    @Test
    fun faelligerAutomatischerSchuldenstrichBlockiertBisZurBahnwegEingabe() {
        val faellig = (1..4).fold(ueberschuldeterAnnaZug()) { state, _ ->
            annaZugBeenden(state)
        }

        val handel = SpielRegelwerk.wendeAn(
            faellig,
            SpielEreignis.RohstoffHandel(
                kaeufer = annaId,
                verkaeufer = berndId,
                rohstoff = Rohstoff.HOLZ,
                menge = 1,
                preis = Geld.mark(1),
            ),
        )

        assertTrue(handel.isFailure)

        val nachSchuldenstrich = SpielRegelwerk.wendeAn(
            faellig,
            SpielEreignis.Schuldenstrich(annaId, entfernteBahnwege = 1),
        ).getOrThrow()

        val anna = nachSchuldenstrich.spieler.first { it.id == annaId }
        assertTrue(nachSchuldenstrich.ueberschuldungen.isEmpty())
        assertTrue(nachSchuldenstrich.bankAnleihen.isEmpty())
        assertTrue(nachSchuldenstrich.anleihen.isEmpty())
        assertEquals(null, anna.bauteile[BauteilTyp.EISENBAHNLINIE])
        assertEquals(berndId, nachSchuldenstrich.aktiverSpieler)
        assertEquals(Phase.Einnahmen, nachSchuldenstrich.zugStatus?.phase)
    }

    @Test
    fun ueberschuldungZaehltNurBankgehalteneAnleihen() {
        val anleihe = AnleiheId("spieler-anleihe")
        val state = SpielZustand(
            spieler = listOf(
                Spieler(
                    id = annaId,
                    name = "Anna",
                    bauteile = mapOf(
                        BauteilTyp.BAHNHOF to 1,
                        BauteilTyp.EISENBAHNLINIE to 1,
                    ),
                ),
                Spieler(
                    id = berndId,
                    name = "Bernd",
                    anleihen = listOf(anleihe),
                ),
            ),
            anleihen = mapOf(
                anleihe to Anleihe(
                    id = anleihe,
                    emittent = annaId,
                    nennwert = Geld.mark(100),
                    zinsBasispunkte = 1_000,
                    laufzeitRunden = 1,
                ),
            ),
            marktpreise = mapOf(
                Rohstoff.HOLZ to Geld.mark(1),
                Rohstoff.ZIEGEL to Geld.mark(1),
                Rohstoff.STAHL to Geld.mark(1),
            ),
            aktiverSpieler = annaId,
            zugStatus = ZugStatus(annaId, Phase.Aktionen),
        )

        val nachZugende = SpielRegelwerk.wendeAn(state, SpielEreignis.ZugBeendet).getOrThrow()

        assertTrue(nachZugende.ueberschuldungen.isEmpty())
    }

    @Test
    fun kriegUnterbrichtFriedlicheUeberschuldungsserie() {
        val einmalUeberschuldet = annaZugBeenden(ueberschuldeterAnnaZug())
        val imKrieg = einmalUeberschuldet.copy(
            konflikte = setOf(Konflikt(annaId, berndId)),
        )

        val nachZugende = annaZugBeenden(imKrieg)

        assertTrue(nachZugende.ueberschuldungen.isEmpty())
    }

    @Test
    fun schrittPhaseUndZugendeWechselnAktivenSpieler() {
        val nachSchritt = SpielRegelwerk.wendeAn(
            startState().copy(
                aktiverSpieler = annaId,
                zugStatus = ZugStatus(annaId, Phase.Einnahmen),
            ),
            SpielEreignis.SchrittAbgeschlossen(SchrittTyp.ROHSTOFF_EINNAHMEN),
        ).getOrThrow()

        val ausgaben = SpielRegelwerk.wendeAn(
            nachSchritt,
            SpielEreignis.PhaseAbgeschlossen(Phase.Einnahmen),
        ).getOrThrow()

        val ausgabenFertig = listOf(
            SpielEreignis.SchrittAbgeschlossen(SchrittTyp.ROHSTOFF_AUSGABEN),
            SpielEreignis.SchrittAbgeschlossen(SchrittTyp.FINANZ_AUSGABEN),
        ).fold(ausgaben) { state, event -> SpielRegelwerk.wendeAn(state, event).getOrThrow() }

        val aktionen = SpielRegelwerk.wendeAn(
            ausgabenFertig,
            SpielEreignis.PhaseAbgeschlossen(Phase.Ausgaben),
        ).getOrThrow()

        val naechsterSpieler = SpielRegelwerk.wendeAn(aktionen, SpielEreignis.ZugBeendet).getOrThrow()

        assertEquals(berndId, naechsterSpieler.aktiverSpieler)
        assertEquals(Phase.Einnahmen, naechsterSpieler.zugStatus?.phase)
    }

    @Test
    fun neueRundeBeginntErstNachdemJederSpielerEinmalAmZugWar() {
        val claraId = SpielerId("Clara")
        var state = SpielZustand(
            spieler = listOf(
                Spieler(annaId, "Anna"),
                Spieler(berndId, "Bernd"),
                Spieler(claraId, "Clara"),
            ),
            rundenzähler = 2,
            aktiverSpieler = annaId,
            zugStatus = ZugStatus(annaId, Phase.Aktionen),
        )

        state = SpielRegelwerk.wendeAn(state, SpielEreignis.ZugBeendet).getOrThrow()
        assertEquals(berndId, state.aktiverSpieler)
        assertEquals(2, state.rundenzähler)

        state = SpielRegelwerk.wendeAn(
            state.copy(zugStatus = ZugStatus(berndId, Phase.Aktionen)),
            SpielEreignis.ZugBeendet,
        ).getOrThrow()
        assertEquals(claraId, state.aktiverSpieler)
        assertEquals(2, state.rundenzähler)

        state = SpielRegelwerk.wendeAn(
            state.copy(zugStatus = ZugStatus(claraId, Phase.Aktionen)),
            SpielEreignis.ZugBeendet,
        ).getOrThrow()
        assertEquals(annaId, state.aktiverSpieler)
        assertEquals(3, state.rundenzähler)
        assertEquals(Phase.Einnahmen, state.zugStatus?.phase)
    }

    @Test
    fun phasenfremderSchrittWirdAbgelehnt() {
        val result = SpielRegelwerk.wendeAn(
            startState().copy(
                aktiverSpieler = annaId,
                zugStatus = ZugStatus(annaId, Phase.Einnahmen),
            ),
            SpielEreignis.RohstoffHandel(
                kaeufer = annaId,
                verkaeufer = berndId,
                rohstoff = Rohstoff.HOLZ,
                menge = 1,
                preis = Geld.mark(1),
            ),
        )

        assertTrue(result.isFailure)
    }

    private fun ueberschuldeterAnnaZug(): SpielZustand {
        val anleihe = AnleiheId("bank-anleihe")
        return SpielZustand(
            spieler = listOf(
                Spieler(
                    id = annaId,
                    name = "Anna",
                    bauteile = mapOf(
                        BauteilTyp.BAHNHOF to 1,
                        BauteilTyp.EISENBAHNLINIE to 1,
                    ),
                ),
                Spieler(
                    id = berndId,
                    name = "Bernd",
                ),
            ),
            bankAnleihen = listOf(anleihe),
            anleihen = mapOf(
                anleihe to Anleihe(
                    id = anleihe,
                    emittent = annaId,
                    nennwert = Geld.mark(100),
                    zinsBasispunkte = 1_000,
                    laufzeitRunden = 1,
                ),
            ),
            marktpreise = mapOf(
                Rohstoff.HOLZ to Geld.mark(1),
                Rohstoff.ZIEGEL to Geld.mark(1),
                Rohstoff.STAHL to Geld.mark(1),
            ),
            aktiverSpieler = annaId,
            zugStatus = ZugStatus(annaId, Phase.Aktionen),
        )
    }

    private fun annaZugBeenden(state: SpielZustand): SpielZustand {
        return SpielRegelwerk.wendeAn(
            state.copy(
                aktiverSpieler = annaId,
                zugStatus = ZugStatus(annaId, Phase.Aktionen),
            ),
            SpielEreignis.ZugBeendet,
        ).getOrThrow()
    }
}
