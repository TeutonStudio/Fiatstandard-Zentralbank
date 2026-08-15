package de.teutonstudio.zentralbank.spielbrett

import android.util.Log
import de.teutonstudio.zentralbank.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

/** Debug-Nachweis für Modellaufbauten; Release-Builds protokollieren nichts. */
internal object Spielbrett3DInstrumentierung {
    private val modellAufbauten = AtomicInteger()

    fun modellErzeugt() {
        if (BuildConfig.DEBUG) {
            Log.d("Spielbrett3D", "Spielbrett3DModell aufgebaut #${modellAufbauten.incrementAndGet()}")
        }
    }
}
