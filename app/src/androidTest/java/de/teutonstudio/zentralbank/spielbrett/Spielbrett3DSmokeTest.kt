package de.teutonstudio.zentralbank.spielbrett

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import de.teutonstudio.zentralbank.MainActivity
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenDreieck
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.Landfeld
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Spielbrett3DSmokeTest {
    @Test
    fun sceneViewStelltUnbegrenztesRasterUndLandAuflageDar() {
        val karte = Spielkarte(
            id = "3d-test",
            name = "3D-Test",
            hexagon = KartenHexagon(radius = 3),
            gelaendefelder = listOf(
                Landfeld(
                    KartenDreieck(-1, -1, DreieckHaelfte.OBEN),
                    GelaendeTyp.WALD,
                ),
            ),
        )

        val betrachtungsStatus = BetrachtungsTransformationsStatus()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    Spielbrett3D(
                        modell = karte.zu3DModell(zeigeBearbeitungsRaster = true),
                        betrachtungsStatus = betrachtungsStatus,
                        kameraInteraktionsModus = KameraInteraktionsModus.VERSCHIEBEN,
                    )
                }
            }
            Thread.sleep(1_000)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                val wurzel = activity.window.decorView
                val startX = wurzel.width / 2f
                val startY = wurzel.height / 2f
                val startzeit = SystemClock.uptimeMillis()
                listOf(
                    MotionEvent.obtain(
                        startzeit,
                        startzeit,
                        MotionEvent.ACTION_DOWN,
                        startX,
                        startY,
                        0,
                    ),
                    MotionEvent.obtain(
                        startzeit,
                        startzeit + 16,
                        MotionEvent.ACTION_MOVE,
                        startX + 100f,
                        startY + 30f,
                        0,
                    ),
                    MotionEvent.obtain(
                        startzeit,
                        startzeit + 32,
                        MotionEvent.ACTION_UP,
                        startX + 100f,
                        startY + 30f,
                        0,
                    ),
                ).forEach { ereignis ->
                    wurzel.dispatchTouchEvent(ereignis)
                    ereignis.recycle()
                }
            }
            Thread.sleep(200)
            scenario.onActivity {
                assertTrue(
                    betrachtungsStatus.fokusX != 0f || betrachtungsStatus.fokusZ != 0f,
                )
            }
        }
    }
}
