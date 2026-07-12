package de.teutonstudio.zentralbank.domain.engine

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.Konflikt
import de.teutonstudio.zentralbank.domain.KontoId
import de.teutonstudio.zentralbank.domain.Rohstoff
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import de.teutonstudio.zentralbank.domain.Anleihe
import de.teutonstudio.zentralbank.domain.AnleiheId
import de.teutonstudio.zentralbank.domain.BauteilTyp
import de.teutonstudio.zentralbank.domain.events.GameEvent
import de.teutonstudio.zentralbank.domain.events.TransaktionsGrund
import de.teutonstudio.zentralbank.domain.zug.Phase
import de.teutonstudio.zentralbank.domain.zug.SchrittTyp
import de.teutonstudio.zentralbank.domain.zug.ZugStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducerTest {
    private val annaId = SpielerId("Anna")
    private val berndId = SpielerId("Bernd")
    private val anleiheId = AnleiheId("anna-1")

    private fun startState(): GameState = GameState(
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
    fun rohstoffEinnahmeErhoehtBestand() {
        val state = Reducer.reduce(
            startState(),
            GameEvent.RohstoffEinnahme(annaId, mapOf(Rohstoff.HOLZ to 3)),
        ).getOrThrow()

        assertEquals(5, state.spieler.first { it.id == annaId }.rohstoffe[Rohstoff.HOLZ])
    }

    @Test
    fun rohstoffAusgabeLehntNegativenBestandAb() {
        val result = Reducer.reduce(
            startState(),
            GameEvent.RohstoffAusgabe(berndId, mapOf(Rohstoff.HOLZ to 1)),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun transaktionIstSummenneutral() {
        val start = startState()
        val state = Reducer.reduce(
            start,
            GameEvent.Transaktion(
                von = KontoId.Spieler(annaId),
                an = KontoId.Spieler(berndId),
                betrag = Geld.mark(3),
                grund = TransaktionsGrund.SONSTIGES,
            ),
        ).getOrThrow()

        assertEquals(start.geldsumme(), state.geldsumme())
        assertEquals(Geld.mark(7), state.spieler.first { it.id == annaId }.geldkonto)
        assertEquals(Geld.mark(8), state.spieler.first { it.id == berndId }.geldkonto)
    }

    @Test
    fun transaktionLehntUnterdeckungAb() {
        val result = Reducer.reduce(
            startState(),
            GameEvent.Transaktion(
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
        val state = Reducer.reduce(
            start,
            GameEvent.RohstoffHandel(
                kaeufer = berndId,
                verkaeufer = annaId,
                rohstoff = Rohstoff.HOLZ,
                menge = 1,
                preis = Geld.mark(2),
            ),
        ).getOrThrow()

        assertEquals(start.geldsumme(), state.geldsumme())
        assertEquals(1, state.spieler.first { it.id == annaId }.rohstoffe[Rohstoff.HOLZ])
        assertEquals(1, state.spieler.first { it.id == berndId }.rohstoffe[Rohstoff.HOLZ])
        assertEquals(Geld.mark(12), state.spieler.first { it.id == annaId }.geldkonto)
        assertEquals(Geld.mark(3), state.spieler.first { it.id == berndId }.geldkonto)
    }

    @Test
    fun anleiheVerkauftVerschiebtAnleiheUndGeldSummenneutral() {
        val start = startState()
        val state = Reducer.reduce(
            start,
            GameEvent.AnleiheVerkauft(
                anleihe = anleiheId,
                verkaeufer = annaId,
                kaeufer = KontoId.Spieler(berndId),
                preis = Geld.mark(4),
            ),
        ).getOrThrow()

        assertEquals(start.geldsumme(), state.geldsumme())
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
        val state = Reducer.reduce(
            start,
            GameEvent.AnleiheFaellig(anleiheId),
        ).getOrThrow()

        assertTrue(anleiheId !in state.spieler.first { it.id == annaId }.anleihen)
        assertTrue(anleiheId !in state.spieler.first { it.id == berndId }.anleihen)
        assertEquals(start.geldsumme(), state.geldsumme())
        assertEquals(Geld.mark(2), state.spieler.first { it.id == annaId }.geldkonto)
        assertEquals(Geld.mark(13), state.spieler.first { it.id == berndId }.geldkonto)
        assertEquals(0, state.anleihen.values.count { it.id == anleiheId })
    }

    @Test
    fun expansionVerbrauchtRohstoffeUndErhoehtBauteilbestand() {
        val state = Reducer.reduce(
            startState(),
            GameEvent.Expansion(
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
        val imKrieg = Reducer.reduce(
            startState(),
            GameEvent.KriegErklaert(
                aggressor = annaId,
                verteidiger = berndId,
            ),
        ).getOrThrow()

        assertEquals(1, imKrieg.konflikte.size)

        val frieden = Reducer.reduce(
            imKrieg,
            GameEvent.KriegBeendet(
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
        val start = GameState(
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

        val state = Reducer.reduce(
            start,
            GameEvent.Schuldenstrich(annaId, entfernteBahnwege = 3),
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
        val result = Reducer.reduce(
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
            GameEvent.Schuldenstrich(annaId, entfernteBahnwege = 0),
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
        assertEquals(Geld.mark(5), warnung.marktwert)
        assertTrue(warnung.warnungAktiv)
        assertFalse(warnung.schuldenstrichFaellig)

        state = annaZugBeenden(state)

        val faellig = state.ueberschuldungen.single()
        assertEquals(4, faellig.friedlicheUeberschuldeteZuege)
        assertTrue(faellig.warnungAktiv)
        assertTrue(faellig.schuldenstrichFaellig)
    }

    @Test
    fun ueberschuldungZaehltNurBankgehalteneAnleihen() {
        val anleihe = AnleiheId("spieler-anleihe")
        val state = GameState(
            spieler = listOf(
                Spieler(
                    id = annaId,
                    name = "Anna",
                    bauteile = mapOf(BauteilTyp.BAHNHOF to 1),
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

        val nachZugende = Reducer.reduce(state, GameEvent.ZugBeendet).getOrThrow()

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
        val nachSchritt = Reducer.reduce(
            startState().copy(
                aktiverSpieler = annaId,
                zugStatus = ZugStatus(annaId, Phase.Einnahmen),
            ),
            GameEvent.SchrittAbgeschlossen(SchrittTyp.ROHSTOFF_EINNAHMEN),
        ).getOrThrow()

        val ausgaben = Reducer.reduce(
            nachSchritt,
            GameEvent.PhaseAbgeschlossen(Phase.Einnahmen),
        ).getOrThrow()

        val ausgabenFertig = listOf(
            GameEvent.SchrittAbgeschlossen(SchrittTyp.ROHSTOFF_AUSGABEN),
            GameEvent.SchrittAbgeschlossen(SchrittTyp.FINANZ_AUSGABEN),
        ).fold(ausgaben) { state, event -> Reducer.reduce(state, event).getOrThrow() }

        val aktionen = Reducer.reduce(
            ausgabenFertig,
            GameEvent.PhaseAbgeschlossen(Phase.Ausgaben),
        ).getOrThrow()

        val naechsterSpieler = Reducer.reduce(aktionen, GameEvent.ZugBeendet).getOrThrow()

        assertEquals(berndId, naechsterSpieler.aktiverSpieler)
        assertEquals(Phase.Einnahmen, naechsterSpieler.zugStatus?.phase)
    }

    @Test
    fun phasenfremderSchrittWirdAbgelehnt() {
        val result = Reducer.reduce(
            startState().copy(
                aktiverSpieler = annaId,
                zugStatus = ZugStatus(annaId, Phase.Einnahmen),
            ),
            GameEvent.RohstoffHandel(
                kaeufer = annaId,
                verkaeufer = berndId,
                rohstoff = Rohstoff.HOLZ,
                menge = 1,
                preis = Geld.mark(1),
            ),
        )

        assertTrue(result.isFailure)
    }

    private fun ueberschuldeterAnnaZug(): GameState {
        val anleihe = AnleiheId("bank-anleihe")
        return GameState(
            spieler = listOf(
                Spieler(
                    id = annaId,
                    name = "Anna",
                    bauteile = mapOf(BauteilTyp.BAHNHOF to 1),
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

    private fun annaZugBeenden(state: GameState): GameState {
        return Reducer.reduce(
            state.copy(
                aktiverSpieler = annaId,
                zugStatus = ZugStatus(annaId, Phase.Aktionen),
            ),
            GameEvent.ZugBeendet,
        ).getOrThrow()
    }
}
