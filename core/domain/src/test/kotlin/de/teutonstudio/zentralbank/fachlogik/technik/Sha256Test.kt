package de.teutonstudio.zentralbank.fachlogik.technik

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sha256Test {
    @Test
    fun `bekannter SHA-256 Testvektor bleibt plattformneutral`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".encodeToByteArray()).alsHex(),
        )
    }

    @Test
    fun `konstanter Vergleich erkennt gleiche und verschiedene Werte`() {
        assertTrue(konstantGleich(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(konstantGleich(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(konstantGleich(byteArrayOf(1, 2), byteArrayOf(1, 2, 0)))
    }
}
