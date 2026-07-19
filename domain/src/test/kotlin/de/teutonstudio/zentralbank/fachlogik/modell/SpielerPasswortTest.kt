package de.teutonstudio.zentralbank.fachlogik.modell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielerPasswortTest {
    @Test
    fun passwortWirdNurAlsHashGespeichertUndGeprueft() {
        val hash = hasheSpielerPasswort("mein geheimnis")
        val spieler = Spieler(
            id = SpielerId("anna"),
            name = "Anna",
            passwortHash = hash,
        )

        assertNotEquals("mein geheimnis", hash)
        assertTrue(spieler.pruefePasswort("mein geheimnis"))
        assertFalse(spieler.pruefePasswort("falsch"))
    }

    @Test
    fun alterSpielstandOhnePasswortBleibtBedienbar() {
        val spieler = Spieler(id = SpielerId("anna"), name = "Anna")

        assertTrue(spieler.pruefePasswort(""))
    }
}
