package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpielErstellenTest {
    @get:Rule
    val composeTestRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun formularKannSofortAngezeigtWerden() {
        composeTestRule.setContent {
            SpielErstellen(
                nachAbbruch = {},
                erstelleSpiel = { _, _ -> },
                nachAbschluß = {},
            )
        }

        composeTestRule
            .onNodeWithText("Spieler Anzahl: ")
            .assertIsDisplayed()
    }

    @Test
    fun abschlussLegtSpielEinmalAnUndNavigiertErstNachErfolg() {
        var anlageAufrufe = 0
        var navigationsAufrufe = 0
        var nachErstellen: (() -> Unit)? = null
        val erzwungeneNeuzusammensetzung = mutableIntStateOf(0)

        composeTestRule.setContent {
            erzwungeneNeuzusammensetzung.intValue
            SpielErstellen(
                nachAbbruch = {},
                erstelleSpiel = { _, nachErfolg ->
                    anlageAufrufe += 1
                    nachErstellen = nachErfolg
                },
                nachAbschluß = { navigationsAufrufe += 1 },
                seite = remember { mutableIntStateOf(8) },
                vorbelegteKarte = KartenVorlage(
                    id = "test-vorlage",
                    name = "Testkarte",
                ),
            )
        }

        composeTestRule.waitUntil { anlageAufrufe == 1 }
        composeTestRule.runOnIdle {
            assertEquals(0, navigationsAufrufe)
            erzwungeneNeuzusammensetzung.intValue += 1
        }
        composeTestRule.runOnIdle {
            assertEquals(1, anlageAufrufe)
            nachErstellen?.invoke()
        }
        composeTestRule.runOnIdle {
            assertEquals(1, navigationsAufrufe)
        }
    }
}
