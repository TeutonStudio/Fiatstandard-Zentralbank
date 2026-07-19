package de.teutonstudio.zentralbank.spielbrett

import de.teutonstudio.zentralbank.fachlogik.modell.BauteilArt
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenPlanungsWerkzeugeTest {
    @Test
    fun planungsmodusEnthaeltAlleWirtschaftsstandorteEinschliesslichAngler() {
        val wirtschaftsstandorte = BauteilTyp.entries
            .filter { bauteil -> bauteil.art == BauteilArt.WIRTSCHAFTSREGION }
            .toSet()

        assertTrue(planbareBauteilTypen.containsAll(wirtschaftsstandorte))
        assertTrue(BauteilTyp.ANGLER in planbareBauteilTypen)
    }
}
