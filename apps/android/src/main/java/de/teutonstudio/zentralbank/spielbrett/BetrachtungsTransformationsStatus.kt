package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.cos
import kotlin.math.sin

/**
 * Speicherbarer Wert der Spielbrett-Kamera.
 *
 * [zoom] ist ein Faktor: Werte groesser als 1 ruecken die Kamera naeher an das Brett. [azimutGrad]
 * dreht die Kamera um die Welt-Y-Achse, [neigungGrad] hebt sie aus der Brett-Ebene an. [fokusX]
 * und [fokusZ] verschieben Kamera und Orbit-Fokus gemeinsam in der Brett-Ebene.
 */
@Immutable
data class BetrachtungsTransformation(
    val zoom: Float = 1f,
    val azimutGrad: Float = 0f,
    val neigungGrad: Float = 38f,
    val fokusX: Float = 0f,
    val fokusZ: Float = 0f,
) {
    init {
        require(zoom.isFinite() && zoom in MIN_ZOOM..MAX_ZOOM) {
            "zoom muss zwischen $MIN_ZOOM und $MAX_ZOOM liegen."
        }
        require(azimutGrad.isFinite()) { "azimutGrad muss endlich sein." }
        require(neigungGrad.isFinite() && neigungGrad in MIN_NEIGUNG_GRAD..MAX_NEIGUNG_GRAD) {
            "neigungGrad muss zwischen $MIN_NEIGUNG_GRAD und $MAX_NEIGUNG_GRAD liegen."
        }
        require(fokusX.isFinite() && fokusZ.isFinite()) { "Der Kamera-Fokus muss endlich sein." }
    }

    companion object {
        const val MIN_ZOOM = 0.35f
        const val MAX_ZOOM = 5f
        const val MIN_NEIGUNG_GRAD = 8f
        const val MAX_NEIGUNG_GRAD = 82f
    }
}

/**
 * Beobachtbarer, von UI und Gesten gemeinsam verwendeter Status der Spielbrett-Kamera.
 *
 * Der Status kann direkt gelesen, in einem ViewModel gehalten oder ueber
 * [rememberBetrachtungsTransformationsStatus] konfigurationssicher erinnert werden.
 */
@Stable
class BetrachtungsTransformationsStatus(
    initialeTransformation: BetrachtungsTransformation = BetrachtungsTransformation(),
) {
    private val startTransformation = initialeTransformation.normalisiert()

    var zoom by mutableFloatStateOf(startTransformation.zoom)
        private set

    var azimutGrad by mutableFloatStateOf(startTransformation.azimutGrad)
        private set

    var neigungGrad by mutableFloatStateOf(startTransformation.neigungGrad)
        private set

    var fokusX by mutableFloatStateOf(startTransformation.fokusX)
        private set

    var fokusZ by mutableFloatStateOf(startTransformation.fokusZ)
        private set

    val transformation: BetrachtungsTransformation
        get() = BetrachtungsTransformation(
            zoom = zoom,
            azimutGrad = azimutGrad,
            neigungGrad = neigungGrad,
            fokusX = fokusX,
            fokusZ = fokusZ,
        )

    /** Multipliziert den aktuellen Zoom; ein Faktor groesser als 1 zoomt hinein. */
    fun zoome(faktor: Float) {
        require(faktor.isFinite() && faktor > 0f) { "Der Zoomfaktor muss positiv und endlich sein." }
        zoom = (zoom * faktor).coerceIn(
            BetrachtungsTransformation.MIN_ZOOM,
            BetrachtungsTransformation.MAX_ZOOM,
        )
    }

    /** Dreht die Kamera in Grad um ihren Fokus und begrenzt die Neigung gegen Ueberschlagen. */
    fun dreheUmFokus(
        azimutDeltaGrad: Float,
        neigungsDeltaGrad: Float,
    ) {
        require(azimutDeltaGrad.isFinite() && neigungsDeltaGrad.isFinite()) {
            "Drehwinkel muessen endlich sein."
        }
        azimutGrad = normalisiereGrad(azimutGrad + azimutDeltaGrad)
        neigungGrad = (neigungGrad + neigungsDeltaGrad).coerceIn(
            BetrachtungsTransformation.MIN_NEIGUNG_GRAD,
            BetrachtungsTransformation.MAX_NEIGUNG_GRAD,
        )
    }

    /**
     * Bewegt Kamera und Fokus gemeinsam in der Brett-Ebene relativ zur aktuellen Blickrichtung.
     * [rechts] verlaeuft parallel zur Bildschirm-Horizontalen, [vorwaerts] vom Kamerastandpunkt
     * in Richtung Fokus.
     */
    fun verschiebeInEbene(
        rechts: Float,
        vorwaerts: Float,
    ) {
        require(rechts.isFinite() && vorwaerts.isFinite()) { "Verschiebungen muessen endlich sein." }
        val azimut = Math.toRadians(azimutGrad.toDouble())
        val sinAzimut = sin(azimut).toFloat()
        val cosAzimut = cos(azimut).toFloat()

        fokusX += rechts * cosAzimut - vorwaerts * sinAzimut
        fokusZ += -rechts * sinAzimut - vorwaerts * cosAzimut
    }

    /**
     * Verschiebt den Fokus entsprechend einer Ziehbewegung auf dem Bildschirm. Dadurch verwenden
     * Ein- und Zwei-Finger-Verschiebung exakt dieselbe Abbildung in die Kartenebene.
     */
    fun verschiebeDurchBildschirmgeste(
        deltaX: Float,
        deltaY: Float,
        welteinheitenProPixel: Float,
    ) {
        require(
            deltaX.isFinite() && deltaY.isFinite() &&
                welteinheitenProPixel.isFinite() && welteinheitenProPixel > 0f,
        ) { "Bildschirmverschiebung und Maßstab müssen endlich und der Maßstab positiv sein." }
        verschiebeInEbene(
            rechts = -deltaX * welteinheitenProPixel,
            vorwaerts = deltaY * welteinheitenProPixel,
        )
    }

    /** Setzt den Orbit-Fokus in Weltkoordinaten auf der XZ-Brett-Ebene. */
    fun setzeFokus(x: Float, z: Float) {
        require(x.isFinite() && z.isFinite()) { "Der Kamera-Fokus muss endlich sein." }
        fokusX = x
        fokusZ = z
    }

    /** Uebernimmt einen vollstaendigen, beispielsweise gespeicherten Kamerawert. */
    fun setze(transformation: BetrachtungsTransformation) {
        val normalisiert = transformation.normalisiert()
        zoom = normalisiert.zoom
        azimutGrad = normalisiert.azimutGrad
        neigungGrad = normalisiert.neigungGrad
        fokusX = normalisiert.fokusX
        fokusZ = normalisiert.fokusZ
    }

    /** Stellt die beim Erzeugen des Status uebergebene Transformation wieder her. */
    fun zuruecksetzen() = setze(startTransformation)
}

/** Erinnert den Kamerastatus inklusive Zoom, Orbit und Ebenenverschiebung ueber Recreation hinweg. */
@Composable
fun rememberBetrachtungsTransformationsStatus(
    initialeTransformation: BetrachtungsTransformation = BetrachtungsTransformation(),
): BetrachtungsTransformationsStatus = rememberSaveable(
    initialeTransformation,
    saver = BetrachtungsTransformationsStatusSaver,
) {
    BetrachtungsTransformationsStatus(initialeTransformation)
}

private val BetrachtungsTransformationsStatusSaver =
    listSaver<BetrachtungsTransformationsStatus, Float>(
        save = { status ->
            listOf(
                status.zoom,
                status.azimutGrad,
                status.neigungGrad,
                status.fokusX,
                status.fokusZ,
            )
        },
        restore = { werte ->
            BetrachtungsTransformationsStatus(
                BetrachtungsTransformation(
                    zoom = werte[0],
                    azimutGrad = werte[1],
                    neigungGrad = werte[2],
                    fokusX = werte[3],
                    fokusZ = werte[4],
                ),
            )
        },
    )

private fun BetrachtungsTransformation.normalisiert() = copy(
    azimutGrad = normalisiereGrad(azimutGrad),
)

private fun normalisiereGrad(winkel: Float): Float = ((winkel % 360f) + 360f) % 360f
