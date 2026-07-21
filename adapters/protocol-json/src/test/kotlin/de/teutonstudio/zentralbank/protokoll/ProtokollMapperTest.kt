package de.teutonstudio.zentralbank.protokoll

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtokollMapperTest {
    @Test
    fun zustandsDtoEnthaeltKeinenPasswortHash() {
        val zustand = SpielZustand(
            spieler = listOf(Spieler(SpielerId("Anna"), "Anna", passwortHash = "geheim")),
        )

        val json = Json.encodeToString(zustand.zuDto())

        assertFalse(json.contains("geheim"))
        assertFalse(json.contains("passwort", ignoreCase = true))
    }

    @Test
    fun freigegebeneAktionHatEinenVerlustfreienDtoRoundtrip() {
        val aktion: SpielAktion = SpielAktion.ProzugBeginnen(7L)

        assertEquals(aktion, aktion.zuDto().zuDomain())
    }
}
