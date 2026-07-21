package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.ProzugStatus
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.VerwaltungsVerpflichtung
import de.teutonstudio.zentralbank.fachlogik.modell.VerwaltungsVerpflichtungId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProzugAuswertungTest {
    private val anna = SpielerId("anna")
    private val ecke = KartenEcke(2, 4)
    private val verpflichtung = VerwaltungsVerpflichtung(
        id = VerwaltungsVerpflichtungId(7L, ecke),
        typ = EckGebaeudeTyp.BAHNHOF,
        bedarf = mapOf(Rohstoff.NAHRUNG to 2, Rohstoff.KOHLE to 1),
    )

    @Test
    fun offeneStandorteErzeugenDefizitUndSperrenAbschluss() {
        val zustand = zustand(
            rohstoffe = mapOf(Rohstoff.NAHRUNG to 1),
            prozug = ProzugStatus(
                begonnen = true,
                verwaltungsVerpflichtungen = listOf(verpflichtung),
            ),
        )

        val plan = requireNotNull(ProzugAuswertung.plan(zustand))

        assertEquals(mapOf(Rohstoff.NAHRUNG to 1, Rohstoff.KOHLE to 1), plan.fehlendeRohstoffe)
        assertFalse(plan.kannErfolgreichAbschliessen)
        assertTrue(plan.sperrgruende.single().contains("Verwaltungsstandort"))
    }

    @Test
    fun nurGebuchtePflichtenErlaubenAbschluss() {
        val prozug = ProzugStatus(
            begonnen = true,
            verwaltungsVerpflichtungen = listOf(verpflichtung),
            versorgteStandorte = setOf(verpflichtung.id),
        )

        val plan = requireNotNull(ProzugAuswertung.plan(zustand(prozug = prozug)))

        assertTrue(plan.kannErfolgreichAbschliessen)
        assertTrue(plan.sperrgruende.isEmpty())
    }

    private fun zustand(
        rohstoffe: Map<Rohstoff, Int> = emptyMap(),
        prozug: ProzugStatus,
    ) = SpielZustand(
        spieler = listOf(Spieler(anna, "Anna", rohstoffe = rohstoffe, geldkonto = Geld.mark(1))),
        aktiverSpieler = anna,
        zugStatus = ZugStatus(7L, anna, ZugPhase.Prozug, prozug),
    )
}
