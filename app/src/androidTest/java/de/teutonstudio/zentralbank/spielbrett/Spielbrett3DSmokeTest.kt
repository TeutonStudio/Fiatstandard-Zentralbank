package de.teutonstudio.zentralbank.spielbrett

import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import de.teutonstudio.zentralbank.MainActivity
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenDreieck
import de.teutonstudio.zentralbank.fachlogik.modell.Landfeld
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import org.junit.Assert.assertFalse
import org.junit.Test

class Spielbrett3DSmokeTest {
    @Test
    fun sceneViewStelltWasserRasterUndLandAuflageDar() {
        val karte = Spielkarte(
            id = "3d-test",
            name = "3D-Test",
            zeilen = 3,
            spalten = 3,
            landfelder = listOf(
                Landfeld(
                    KartenDreieck(1, 1, DreieckHaelfte.OBEN),
                    GelaendeTyp.WALD,
                ),
            ),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    Spielbrett3D(karte.zu3DModell(zeigeBearbeitungsRaster = true))
                }
            }
            Thread.sleep(1_000)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }
}
