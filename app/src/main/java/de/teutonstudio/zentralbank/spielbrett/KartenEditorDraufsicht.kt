package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import kotlin.math.ceil
import kotlin.math.floor

private const val MIN_DRAUFSICHT_ZOOM = 0.35f
private const val MAX_DRAUFSICHT_ZOOM = 5f
private val DREIECK_HOEHE_AUF_BILDSCHIRM = 36.dp

private val DraufsichtHintergrund = Color(0xFF0B3D66)
private val RasterFarbe = Color(0x5AFFFFFF)
private val GelaendeUmrissFarbe = Color(0xB3000000)
private val DraufsichtGelaendeFarben = mapOf(
    GelaendeTyp.EBENE to Color(0xFF8DBB61),
    GelaendeTyp.WALD to Color(0xFF2E7D32),
    GelaendeTyp.GEBIRGE to Color(0xFF757575),
    GelaendeTyp.WUESTE to Color(0xFFD8B56A),
    GelaendeTyp.SUMPF to Color(0xFF607D3B),
)

@Immutable
internal data class DraufsichtTransformation(
    val zoom: Float = 1f,
    val fokusX: Float = 0f,
    val fokusZ: Float = 0f,
) {
    init {
        require(zoom.isFinite() && zoom in MIN_DRAUFSICHT_ZOOM..MAX_DRAUFSICHT_ZOOM)
        require(fokusX.isFinite() && fokusZ.isFinite())
    }
}

@Stable
internal class KartenDraufsichtStatus(
    initialeTransformation: DraufsichtTransformation = DraufsichtTransformation(),
) {
    private val startTransformation = initialeTransformation

    var transformation by mutableStateOf(initialeTransformation)
        private set

    fun verarbeiteGeste(
        vorherigerSchwerpunkt: Offset,
        verschiebung: Offset,
        zoomFaktor: Float,
        ansichtsGroesse: IntSize,
        basisMassstab: Float,
    ) {
        transformation = transformation.nachGeste(
            vorherigerSchwerpunkt = vorherigerSchwerpunkt,
            verschiebung = verschiebung,
            zoomFaktor = zoomFaktor,
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )
    }

    fun zuruecksetzen() {
        transformation = startTransformation
    }
}

@Composable
internal fun rememberKartenDraufsichtStatus(
    initialeTransformation: DraufsichtTransformation = DraufsichtTransformation(),
): KartenDraufsichtStatus = rememberSaveable(
    initialeTransformation,
    saver = KartenDraufsichtStatusSaver,
) {
    KartenDraufsichtStatus(initialeTransformation)
}

private val KartenDraufsichtStatusSaver = listSaver<KartenDraufsichtStatus, Float>(
    save = { status ->
        status.transformation.let { transformation ->
            listOf(transformation.zoom, transformation.fokusX, transformation.fokusZ)
        }
    },
    restore = { werte ->
        KartenDraufsichtStatus(
            DraufsichtTransformation(
                zoom = werte[0],
                fokusX = werte[1],
                fokusZ = werte[2],
            ),
        )
    },
)

/**
 * Schlanke Draufsicht fuer den Baumodus. Die statischen Raster- und Gelaendepfade werden gecacht;
 * Ziehen und Zoomen invalidieren nur den Zeichenaufruf und bauen keine Szenenknoten auf.
 */
@Composable
internal fun KartenEditorDraufsicht(
    karte: KartenVorlage,
    status: KartenDraufsichtStatus,
    referenzStatus: KartenReferenzEditorStatus,
    referenzBild: ImageBitmap?,
    referenzAusrichten: Boolean,
    modifier: Modifier = Modifier,
    onDreieckBeruehrt: (DreieckTreffer) -> Unit,
) {
    val geometrie = remember(karte.hexagon.zentrum) {
        // Fuer Ursprung und unbegrenzte Trefferrechnung genuegt ein lokales Hexagon. Dadurch
        // waechst der Aufwand der Draufsicht nicht mit dem gespeicherten Kartenradius.
        berechneSpielbrettGeometrie(karte.hexagon.copy(radius = 1))
    }
    val basisMassstab = with(LocalDensity.current) {
        DREIECK_HOEHE_AUF_BILDSCHIRM.toPx() / GRUNDDREIECK_HOEHE
    }
    val aktuellerBeruehrungsEmpfaenger by rememberUpdatedState(onDreieckBeruehrt)

    Box(
        modifier = modifier
            .semantics {
                contentDescription =
                    "Draufsicht der bearbeitbaren Karte mit ${karte.gelaendefelder.size} " +
                    "Geländedreiecken" +
                    if (referenzBild == null) "" else " und Referenzbild"
            }
            .pointerInput(status, referenzStatus, basisMassstab, referenzAusrichten) {
                detectTransformGestures(panZoomLock = true) {
                        schwerpunkt,
                        verschiebung,
                        zoomFaktor,
                        _,
                    ->
                    if (referenzAusrichten && referenzStatus.referenz != null) {
                        referenzStatus.verarbeiteAusrichtungsGeste(
                            verschiebungBildschirmX = verschiebung.x,
                            verschiebungBildschirmZ = verschiebung.y,
                            zoomFaktor = zoomFaktor,
                            bildschirmMassstab = basisMassstab * status.transformation.zoom,
                        )
                    } else {
                        status.verarbeiteGeste(
                            vorherigerSchwerpunkt = schwerpunkt,
                            verschiebung = verschiebung,
                            zoomFaktor = zoomFaktor,
                            ansichtsGroesse = size,
                            basisMassstab = basisMassstab,
                        )
                    }
                }
            }
            .pointerInput(status, geometrie, basisMassstab, referenzAusrichten) {
                if (referenzAusrichten) return@pointerInput
                detectTapGestures { bildschirmPunkt ->
                    val brettPunkt = status.transformation.bildschirmZuBrett(
                        bildschirmPunkt = bildschirmPunkt,
                        ansichtsGroesse = size,
                        basisMassstab = basisMassstab,
                    )
                    geometrie.unbegrenzterTreffer(brettPunkt)?.let {
                        aktuellerBeruehrungsEmpfaenger(it)
                    }
                }
            }
            .drawWithCache {
                val minimalerMassstab = basisMassstab * MIN_DRAUFSICHT_ZOOM
                val sichtbareWeltBreite = size.width / minimalerMassstab
                val sichtbareWeltHoehe = size.height / minimalerMassstab
                val zeilenRadius =
                    ceil(sichtbareWeltHoehe / (2f * GRUNDDREIECK_HOEHE)).toInt() + 3
                val spaltenRadius =
                    ceil(sichtbareWeltBreite / (2f * GRUNDDREIECK_SEITENLAENGE)).toInt() +
                        zeilenRadius / 2 + 3
                val rasterPfad = erstelleRasterPfad(
                    geometrie = geometrie,
                    zeilenRadius = zeilenRadius,
                    spaltenRadius = spaltenRadius,
                )
                val gelaendePfade = DraufsichtGelaendeFarben.keys.associateWith { Path() }
                val gelaendeUmriss = Path()
                karte.gelaendefelder.forEach { feld ->
                    val dreieck = grundDreieck(
                        position = feld.position.zu3DPosition(),
                        ursprung = geometrie.ursprung,
                    )
                    gelaendePfade.getValue(feld.gelaende).fuegeHinzu(dreieck)
                    gelaendeUmriss.fuegeHinzu(dreieck)
                }
                val rasterStrichBreite = 1.dp.toPx()
                val gelaendeStrichBreite = 1.5.dp.toPx()
                val referenzRahmenBreite = 2.dp.toPx()

                onDrawBehind {
                    drawRect(DraufsichtHintergrund)
                    val transformation = status.transformation
                    val massstab = basisMassstab * transformation.zoom
                    val ansichtsMitte = Offset(size.width / 2f, size.height / 2f)
                    val rasterVerschiebung = rasterAnkerVerschiebung(
                        fokus = BrettPunkt(transformation.fokusX, transformation.fokusZ),
                        ursprung = geometrie.ursprung,
                    )

                    translate(left = ansichtsMitte.x, top = ansichtsMitte.y) {
                        scale(scale = massstab, pivot = Offset.Zero) {
                            translate(
                                left = -transformation.fokusX,
                                top = -transformation.fokusZ,
                            ) {
                                gelaendePfade.forEach { (typ, pfad) ->
                                    drawPath(
                                        path = pfad,
                                        color = DraufsichtGelaendeFarben.getValue(typ),
                                    )
                                }
                                val referenz = referenzStatus.referenz?.metadaten
                                if (referenzBild != null && referenz?.sichtbar == true) {
                                    val bildMassstab =
                                        referenz.breiteInBrettEinheiten / referenzBild.width
                                    val bildHoehe = referenzBild.height * bildMassstab
                                    translate(
                                        left = referenz.zentrumX -
                                            referenz.breiteInBrettEinheiten / 2f,
                                        top = referenz.zentrumZ - bildHoehe / 2f,
                                    ) {
                                        scale(scale = bildMassstab, pivot = Offset.Zero) {
                                            drawImage(
                                                image = referenzBild,
                                                alpha = referenz.deckkraft,
                                            )
                                        }
                                    }
                                    if (referenzAusrichten) {
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(
                                                referenz.zentrumX -
                                                    referenz.breiteInBrettEinheiten / 2f,
                                                referenz.zentrumZ - bildHoehe / 2f,
                                            ),
                                            size = Size(
                                                referenz.breiteInBrettEinheiten,
                                                bildHoehe,
                                            ),
                                            style = Stroke(width = referenzRahmenBreite / massstab),
                                        )
                                    }
                                }
                                translate(
                                    left = rasterVerschiebung.x,
                                    top = rasterVerschiebung.z,
                                ) {
                                    drawPath(
                                        path = rasterPfad,
                                        color = RasterFarbe,
                                        style = Stroke(width = rasterStrichBreite / massstab),
                                    )
                                }
                                drawPath(
                                    path = gelaendeUmriss,
                                    color = GelaendeUmrissFarbe,
                                    style = Stroke(width = gelaendeStrichBreite / massstab),
                                )
                            }
                        }
                    }
                }
            },
    )
}

internal fun DraufsichtTransformation.nachGeste(
    vorherigerSchwerpunkt: Offset,
    verschiebung: Offset,
    zoomFaktor: Float,
    ansichtsGroesse: IntSize,
    basisMassstab: Float,
): DraufsichtTransformation {
    if (
        ansichtsGroesse.width <= 0 || ansichtsGroesse.height <= 0 ||
        !basisMassstab.isFinite() || basisMassstab <= 0f ||
        !zoomFaktor.isFinite() || zoomFaktor <= 0f
    ) {
        return this
    }
    val neuerZoom = (zoom * zoomFaktor).coerceIn(MIN_DRAUFSICHT_ZOOM, MAX_DRAUFSICHT_ZOOM)
    val alterMassstab = basisMassstab * zoom
    val neuerMassstab = basisMassstab * neuerZoom
    val ansichtsMitte = Offset(ansichtsGroesse.width / 2f, ansichtsGroesse.height / 2f)
    val aktuellerSchwerpunkt = vorherigerSchwerpunkt + verschiebung
    val alterAnker = vorherigerSchwerpunkt - ansichtsMitte
    val neuerAnker = aktuellerSchwerpunkt - ansichtsMitte

    return copy(
        zoom = neuerZoom,
        fokusX = fokusX + alterAnker.x / alterMassstab - neuerAnker.x / neuerMassstab,
        fokusZ = fokusZ + alterAnker.y / alterMassstab - neuerAnker.y / neuerMassstab,
    )
}

internal fun DraufsichtTransformation.bildschirmZuBrett(
    bildschirmPunkt: Offset,
    ansichtsGroesse: IntSize,
    basisMassstab: Float,
): BrettPunkt {
    val massstab = basisMassstab * zoom
    return BrettPunkt(
        x = fokusX + (bildschirmPunkt.x - ansichtsGroesse.width / 2f) / massstab,
        z = fokusZ + (bildschirmPunkt.y - ansichtsGroesse.height / 2f) / massstab,
    )
}

internal fun DraufsichtTransformation.brettZuBildschirm(
    brettPunkt: BrettPunkt,
    ansichtsGroesse: IntSize,
    basisMassstab: Float,
): Offset {
    val massstab = basisMassstab * zoom
    return Offset(
        x = ansichtsGroesse.width / 2f + (brettPunkt.x - fokusX) * massstab,
        y = ansichtsGroesse.height / 2f + (brettPunkt.z - fokusZ) * massstab,
    )
}

internal fun rasterAnkerVerschiebung(
    fokus: BrettPunkt,
    ursprung: BrettPunkt,
): BrettPunkt {
    val zeile = floor((fokus.z + ursprung.z) / GRUNDDREIECK_HOEHE).toInt()
    val spalte = floor(
        (fokus.x + ursprung.x - zeile * GRUNDDREIECK_SEITENLAENGE / 2f) /
            GRUNDDREIECK_SEITENLAENGE,
    ).toInt()
    return BrettPunkt(
        x = zeile * GRUNDDREIECK_SEITENLAENGE / 2f +
            spalte * GRUNDDREIECK_SEITENLAENGE,
        z = zeile * GRUNDDREIECK_HOEHE,
    )
}

private fun erstelleRasterPfad(
    geometrie: SpielbrettGeometrie,
    zeilenRadius: Int,
    spaltenRadius: Int,
): Path = Path().apply {
    for (zeile in -zeilenRadius..zeilenRadius) {
        for (spalte in -spaltenRadius..spaltenRadius) {
            DreieckAusrichtung.entries.forEach { ausrichtung ->
                fuegeHinzu(
                    grundDreieck(
                        position = DreieckPosition(zeile, spalte, ausrichtung),
                        ursprung = geometrie.ursprung,
                    ),
                )
            }
        }
    }
}

private fun Path.fuegeHinzu(dreieck: GrundDreieck) {
    val ersteEcke = dreieck.ecken[0]
    moveTo(ersteEcke.x, ersteEcke.z)
    lineTo(dreieck.ecken[1].x, dreieck.ecken[1].z)
    lineTo(dreieck.ecken[2].x, dreieck.ecken[2].z)
    close()
}
