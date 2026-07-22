package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KonfliktV2Test {
    private val a = SpielerId("a")
    private val b = SpielerId("b")
    private val c = SpielerId("c")
    private val d = SpielerId("d")

    @Test
    fun alleVorgegebenenKampfbeispieleUndSpiegelungenSindDeterministisch() {
        val beispiele = listOf(
            Triple(1, 1, 0), Triple(2, 1, 1), Triple(3, 1, 3),
            Triple(4, 1, 4), Triple(3, 2, 1), Triple(4, 2, 3),
            Triple(6, 2, 6), Triple(10, 3, 10),
        )
        beispiele.forEach { (staerker, schwächer, ueberlebende) ->
            assertEquals(ueberlebende to 0, KonfliktRegelwerk.ueberlebendeTruppen(staerker, schwächer))
            assertEquals(0 to ueberlebende, KonfliktRegelwerk.ueberlebendeTruppen(schwächer, staerker))
        }
    }

    @Test
    fun mehrereAggressorenUndMehrereGleichzeitigeKriegeBleibenFormalGetrennt() {
        var zustand = basis()
        zustand = KonfliktRegelwerk.kriegErklaeren(
            zustand, SpielEreignis.KriegErklaert(a, c, KriegId("k1")),
        )
        zustand = KonfliktRegelwerk.kriegErklaeren(
            zustand, SpielEreignis.KriegErklaert(b, c, KriegId("k2")),
        )
        zustand = KonfliktRegelwerk.kriegErklaeren(
            zustand, SpielEreignis.KriegErklaert(a, d, KriegId("k3")),
        )

        assertEquals(2, zustand.konflikte.size)
        assertEquals(setOf(a, b), zustand.konflikte.single { c in it.verteidiger }.aggressoren)
        assertTrue(zustand.konflikte.any { it.betrifft(a, d) })
    }

    @Test
    fun vollstaendigePaarweiseWaffenstillstaendeBeendenKriegUnentschieden() {
        var zustand = basis().copy(
            konflikte = setOf(
                Konflikt(a, c, KriegId("k1"), aggressoren = setOf(a, b), verteidiger = setOf(c, d)),
            ),
        )
        listOf(a to c, a to d, b to c, b to d).forEach { (von, an) ->
            zustand = KonfliktRegelwerk.waffenstillstandAnbieten(
                zustand,
                SpielEreignis.WaffenstillstandAngeboten(KriegId("k1"), von, an),
            )
            zustand = KonfliktRegelwerk.waffenstillstandSchliessen(
                zustand,
                SpielEreignis.WaffenstillstandGeschlossen(
                    KriegId("k1"), SpielerPaar.aus(von, an), an,
                ),
            )
        }

        assertTrue(zustand.konflikte.isEmpty())
        val frieden = zustand.friedensvertraege.single()
        assertEquals(setOf(a, b, c, d), frieden.unentschiedeneTeilnehmer)
        assertTrue(frieden.schuldUebertragungen.isEmpty())
    }

    @Test
    fun schuldanteileFolgenMarktwertUndRundungsrestIstDeterministisch() {
        val schuld = Anleihe(AnleiheId("schuld"), a, Geld.cent(10_001), 200, 3)
        val zustand = basis().copy(
            spieler = listOf(
                Spieler(a, "A"),
                Spieler(b, "B", geldkonto = Geld.mark(100)),
                Spieler(c, "C", geldkonto = Geld.mark(300)),
                Spieler(d, "D"),
            ),
            anleihen = mapOf(schuld.id to schuld),
            bankAnleihen = listOf(schuld.id),
        )

        val anteile = KonfliktRegelwerk.schuldUebertragungen(zustand, setOf(a), setOf(b, c))

        assertEquals(10_001L, anteile.sumOf { it.betrag.cent })
        assertEquals(2_501L, anteile.single { it.verlierer == b }.betrag.cent)
        assertEquals(7_500L, anteile.single { it.verlierer == c }.betrag.cent)
        assertEquals(1L, anteile.single { it.verlierer == b }.rundungsRestCent)
        assertEquals(0L, anteile.single { it.verlierer == c }.rundungsRestCent)
    }

    @Test
    fun friedensschlussLoestSiegeranleihenAbUndErsetztSieBeimSelbenGlaeubiger() {
        val alteSchuld = Anleihe(AnleiheId("schuld-a"), a, Geld.cent(10_001), 100, 3)
        val krieg = Konflikt(a, b, KriegId("krieg"), verteidiger = setOf(b, c))
        val zustand = basis().copy(
            spieler = listOf(
                Spieler(a, "A"),
                Spieler(b, "B", geldkonto = Geld.mark(100)),
                Spieler(c, "C", geldkonto = Geld.mark(300)),
                Spieler(d, "D"),
            ),
            anleihen = mapOf(alteSchuld.id to alteSchuld),
            bankAnleihen = listOf(alteSchuld.id),
            konflikte = setOf(krieg),
            leitzins = Basispunkte(250),
        )
        val vertrag = Friedensvertrag(
            id = FriedensvertragId("frieden"),
            krieg = krieg.id,
            beteiligteSpieler = setOf(a, b, c),
            gewinner = setOf(a),
            verlierer = setOf(b, c),
            angenommenVon = setOf(a, b, c),
            abgeschlossenInRunde = 0,
        )

        val danach = KonfliktRegelwerk.friedenAbschliessen(
            zustand,
            SpielEreignis.FriedensvertragAbgeschlossen(vertrag),
        )

        assertTrue(alteSchuld.id !in danach.anleihen)
        assertEquals(10_001L, danach.anleihen.values.sumOf { it.nennwert.cent })
        assertEquals(2_501L, danach.anleihen.values.single { it.emittent == b }.nennwert.cent)
        assertEquals(7_500L, danach.anleihen.values.single { it.emittent == c }.nennwert.cent)
        assertTrue(danach.anleihen.values.all { it.zinsBasispunkte >= 250 })
        assertEquals(danach.anleihen.keys, danach.bankAnleihen.toSet())
        assertEquals(danach.anleihen.keys, danach.friedensvertraege.single().entstehendeAnleihen.toSet())
        assertTrue(danach.konflikte.isEmpty())
    }

    @Test
    fun kapitulationEntferntInMehrparteienkriegNurDenKapitulierenden() {
        val krieg = Konflikt(
            spielerA = a,
            spielerB = b,
            id = KriegId("mehrparteienkrieg"),
            aggressoren = setOf(a, c),
            verteidiger = setOf(b, d),
        )

        val danach = KonfliktRegelwerk.kapitulieren(
            basis().copy(konflikte = setOf(krieg)),
            SpielEreignis.KriegKapituliert(krieg.id, a),
        )

        val fortgesetzt = danach.konflikte.single()
        assertEquals(setOf(c), fortgesetzt.aggressoren)
        assertEquals(setOf(b, d), fortgesetzt.verteidiger)
        val vertrag = danach.friedensvertraege.single()
        assertEquals(setOf(a), vertrag.ausscheidendeTeilnehmer)
        assertEquals(setOf(a), vertrag.kapitulationen)
        assertEquals(setOf(a), vertrag.verlierer)
    }

    @Test
    fun unabhaengigerFriedenLaesstDieGegenparteiImKrieg() {
        val krieg = Konflikt(
            spielerA = a,
            spielerB = b,
            id = KriegId("unabhaengig"),
            aggressoren = setOf(a, c),
            verteidiger = setOf(b, d),
        )
        val vertrag = Friedensvertrag(
            id = FriedensvertragId("austritt-a"),
            krieg = krieg.id,
            beteiligteSpieler = setOf(a, b),
            unentschiedeneTeilnehmer = setOf(a, b),
            ausscheidendeTeilnehmer = setOf(a),
            angenommenVon = setOf(a, b),
        )

        val danach = KonfliktRegelwerk.friedenAbschliessen(
            basis().copy(konflikte = setOf(krieg)),
            SpielEreignis.FriedensvertragAbgeschlossen(vertrag),
        )

        val fortgesetzt = danach.konflikte.single()
        assertEquals(setOf(c), fortgesetzt.aggressoren)
        assertEquals(setOf(b, d), fortgesetzt.verteidiger)
    }

    private fun basis() = SpielZustand(
        spieler = listOf(Spieler(a, "A"), Spieler(b, "B"), Spieler(c, "C"), Spieler(d, "D")),
    )
}
