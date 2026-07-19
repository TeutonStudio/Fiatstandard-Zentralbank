package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun zurueckAufErsterSeiteBrichtDirektAbOhneLeereSeite() {
        val seite = mutableIntStateOf(1)
        var abbruchAufrufe = 0

        composeTestRule.setContent {
            SpielErstellen(
                nachAbbruch = { abbruchAufrufe += 1 },
                erstelleSpiel = { _, _ -> },
                nachAbschluß = {},
                seite = seite,
            )
        }

        composeTestRule.onNodeWithText("Zurück").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, abbruchAufrufe)
            assertEquals(1, seite.intValue)
        }
    }

    @Test
    fun kartenauswahlLaedtAktuellesFormatMitStatischerVorschau() {
        composeTestRule.setContent {
            SpielErstellen(
                nachAbbruch = {},
                erstelleSpiel = { _, _ -> },
                nachAbschluß = {},
                seite = remember { mutableIntStateOf(3) },
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Inselreich").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithContentDescription("Vorschau des 3D-Spielbretts")
            .assertIsDisplayed()
    }

    @Test
    fun warenkorbUndZentralbankStehenGemeinsamLinksUndRechts() {
        composeTestRule.setContent {
            SpielErstellen(
                nachAbbruch = {},
                erstelleSpiel = { _, _ -> },
                nachAbschluß = {},
                seite = remember { mutableIntStateOf(2) },
            )
        }

        val warenkorb = composeTestRule
            .onNodeWithText("Warenkorb")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val zentralbank = composeTestRule
            .onNodeWithText("Zentralbank")
            .assertIsDisplayed()
            .fetchSemanticsNode()

        assertTrue(warenkorb.boundsInRoot.center.x < zentralbank.boundsInRoot.center.x)
    }

    @Test
    fun startbauwerkeAllerSpielerStehenAufEinerHorizontalenSeite() {
        composeTestRule.setContent {
            SpielErstellen(
                nachAbbruch = {},
                erstelleSpiel = { _, _ -> },
                nachAbschluß = {},
                seite = remember { mutableIntStateOf(4) },
            )
        }

        composeTestRule
            .onNodeWithText("Bauteile für Spieler 1")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(STARTBAUWERKE_LAZY_ROW)
            .performScrollToIndex(2)
        composeTestRule
            .onNodeWithText("Bauteile für Spieler 3")
            .assertIsDisplayed()
    }

    @Test
    fun startausstattungKannGemeinsamFuerAlleSpielerErfasstWerden() {
        composeTestRule.setContent {
            SpielErstellen(
                nachAbbruch = {},
                erstelleSpiel = { _, _ -> },
                nachAbschluß = {},
                seite = remember { mutableIntStateOf(4) },
            )
        }

        composeTestRule
            .onNodeWithText("Startrohstoffe für Spieler 1")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(UNTERSCHIEDLICHE_STARTAUSSTATTUNG)
            .performClick()
        composeTestRule
            .onNodeWithText("Startrohstoffe für alle Spieler")
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Startrohstoffe für Spieler 1")
            .assertCountEquals(0)
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
                seite = remember { mutableIntStateOf(5) },
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
