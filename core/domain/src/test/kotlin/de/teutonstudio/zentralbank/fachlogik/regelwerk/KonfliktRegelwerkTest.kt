package de.teutonstudio.zentralbank.fachlogik.regelwerk

import org.junit.Assert.assertEquals
import org.junit.Test

class KonfliktRegelwerkTest {
    @Test
    fun gleichstandVernichtetBeideTruppenverbaende() {
        assertEquals(0 to 0, KonfliktRegelwerk.ueberlebendeTruppen(4, 4))
    }

    @Test
    fun beiEinerTruppeVorsprungUeberlebtEine() {
        assertEquals(1 to 0, KonfliktRegelwerk.ueberlebendeTruppen(4, 3))
        assertEquals(0 to 1, KonfliktRegelwerk.ueberlebendeTruppen(3, 4))
    }

    @Test
    fun beiZweiTruppenVorsprungUeberlebenDrei() {
        assertEquals(3 to 0, KonfliktRegelwerk.ueberlebendeTruppen(5, 3))
        assertEquals(0 to 3, KonfliktRegelwerk.ueberlebendeTruppen(2, 4))
    }

    @Test
    fun abDreiTruppenVorsprungUeberlebtDerGesamteStaerkereVerband() {
        assertEquals(6 to 0, KonfliktRegelwerk.ueberlebendeTruppen(6, 3))
        assertEquals(0 to 5, KonfliktRegelwerk.ueberlebendeTruppen(2, 5))
    }
}
