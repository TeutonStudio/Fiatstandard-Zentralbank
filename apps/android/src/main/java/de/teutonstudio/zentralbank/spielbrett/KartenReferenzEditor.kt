package de.teutonstudio.zentralbank.spielbrett

import android.graphics.ImageDecoder
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import de.teutonstudio.zentralbank.daten.karten.KartenReferenz
import de.teutonstudio.zentralbank.daten.karten.KartenReferenzMetadaten
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
internal class KartenReferenzEditorStatus {
    var referenz by mutableStateOf<KartenReferenz?>(null)
        private set

    var initialisiert by mutableStateOf(false)
        private set

    fun initialisiere(referenz: KartenReferenz?) {
        if (initialisiert) return
        this.referenz = referenz
        initialisiert = true
    }

    fun setze(referenz: KartenReferenz?) {
        this.referenz = referenz
        initialisiert = true
    }

    fun aktualisiereMetadaten(
        aenderung: (KartenReferenzMetadaten) -> KartenReferenzMetadaten,
    ) {
        val aktuell = referenz ?: return
        referenz = aktuell.copy(metadaten = aenderung(aktuell.metadaten))
    }

    fun verarbeiteAusrichtungsGeste(
        verschiebungBildschirmX: Float,
        verschiebungBildschirmZ: Float,
        zoomFaktor: Float,
        bildschirmMassstab: Float,
    ) {
        if (!bildschirmMassstab.isFinite() || bildschirmMassstab <= 0f) return
        aktualisiereMetadaten { metadaten ->
            metadaten.nachAusrichtungsGeste(
                verschiebungX = verschiebungBildschirmX / bildschirmMassstab,
                verschiebungZ = verschiebungBildschirmZ / bildschirmMassstab,
                zoomFaktor = zoomFaktor,
            )
        }
    }
}

internal suspend fun File.ladeReferenzImageBitmap(): ImageBitmap = withContext(Dispatchers.IO) {
    require(isFile) { "Das Referenzbild ist nicht mehr verfügbar." }
    val quelle = ImageDecoder.createSource(this@ladeReferenzImageBitmap)
    ImageDecoder.decodeBitmap(quelle) { decoder, info, _ ->
        val laengsteSeite = max(info.size.width, info.size.height)
        if (laengsteSeite > MAX_DEKODIERTE_REFERENZ_SEITE) {
            val faktor = MAX_DEKODIERTE_REFERENZ_SEITE.toFloat() / laengsteSeite
            decoder.setTargetSize(
                (info.size.width * faktor).roundToInt().coerceAtLeast(1),
                (info.size.height * faktor).roundToInt().coerceAtLeast(1),
            )
        }
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
    }.asImageBitmap()
}

private const val MAX_DEKODIERTE_REFERENZ_SEITE = 4096
