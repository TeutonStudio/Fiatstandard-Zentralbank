package de.teutonstudio.zentralbank.spielbrett

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneView
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Position2
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val BRETT_DICKE = 0.12f
private const val OBERFLAECHEN_ABSTAND = 0.004f
private const val BRETT_RAND = 0.35f
private const val KAMERA_FOKUS_HOEHE = AUFLAGEN_HOEHE * 0.2f

private val BrettSeitenFarbe = Color(0xFF20282E)
private val GrundFarbeHell = Color(0xFF526670)
private val GrundFarbeDunkel = Color(0xFF40525C)
private val VorschauFarbe = Color(0xFF263238)

/**
 * Rendert [modell] als interaktives 3D-Spielbrett mit SceneView und Google Filament.
 *
 * Die Grundflaeche besteht aus gleichseitigen Dreiecken mit der geometrischen Hoehe 2.
 * Jede [DreieckAuflage] wird als dreiseitiges Prisma dargestellt. Seine vertikale Hoehe und
 * die Hoehe seiner dreieckigen Grundflaeche betragen jeweils exakt 1. Ziehen dreht die Kamera,
 * eine Zwei-Finger-Geste verschiebt sie und Pinch zoomt.
 *
 * [betrachtungsStatus] enthaelt dieselbe Transformation, die auch die Gesten veraendern. Dadurch
 * kann die Ansicht von aussen beobachtet, ueber Schaltflaechen gesteuert oder gespeichert werden.
 *
 * Beispiel:
 * ```kotlin
 * Spielbrett3D(
 *     modell = Spielbrett3DModell(
 *         zeilen = 3,
 *         spalten = 4,
 *         auflagen = listOf(
 *             DreieckAuflage(
 *                 DreieckPosition(0, 1, DreieckAusrichtung.UNTEN),
 *                 DreieckTyp("Gold", Color(0xFFFFC107)),
 *             ),
 *         ),
 *     ),
 *     betrachtungsStatus = rememberBetrachtungsTransformationsStatus(),
 * )
 * ```
 */
@Composable
fun Spielbrett3D(
    modell: Spielbrett3DModell,
    modifier: Modifier = Modifier,
    betrachtungsStatus: BetrachtungsTransformationsStatus =
        rememberBetrachtungsTransformationsStatus(),
) {
    if (LocalInspectionMode.current) {
        SpielbrettVorschau(
            modell = modell,
            transformation = betrachtungsStatus.transformation,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val geometrie = remember(modell.zeilen, modell.spalten) {
        berechneSpielbrettGeometrie(modell.zeilen, modell.spalten)
    }
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    var ansichtsGroesse by remember { mutableStateOf(IntSize.Zero) }
    val betrachtungsGesten = remember(betrachtungsStatus) {
        BetrachtungsGesten(betrachtungsStatus)
    }

    val brettMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = BrettSeitenFarbe,
            metallic = 0.02f,
            roughness = 0.88f,
        )
    }
    val grundMaterialien = remember(materialLoader) {
        listOf(
            materialLoader.doppelseitigesMaterial(GrundFarbeHell, rauheit = 0.84f),
            materialLoader.doppelseitigesMaterial(GrundFarbeDunkel, rauheit = 0.9f),
        )
    }
    val verwendeteTypen = modell.auflagen.map(DreieckAuflage::typ).distinct()
    val auflagenMaterialien = remember(materialLoader, verwendeteTypen) {
        verwendeteTypen.associateWith { typ ->
            materialLoader.createColorInstance(
                color = typ.farbe,
                metallic = typ.metallisch,
                roughness = typ.rauheit,
            )
        }
    }

    val szenengroesse = max(geometrie.breite, geometrie.tiefe)
    val kameraAbstand = szenengroesse * 1.15f + AUFLAGEN_HOEHE * 2f
    val transformation = betrachtungsStatus.transformation
    val kameraDistanz = kameraAbstand / transformation.zoom
    val azimut = Math.toRadians(transformation.azimutGrad.toDouble())
    val neigung = Math.toRadians(transformation.neigungGrad.toDouble())
    val horizontalerAbstand = kameraDistanz * cos(neigung).toFloat()
    val fokusPosition = Position(
        x = transformation.fokusX,
        y = KAMERA_FOKUS_HOEHE,
        z = transformation.fokusZ,
    )
    val kameraPosition = Position(
        x = fokusPosition.x + horizontalerAbstand * sin(azimut).toFloat(),
        y = fokusPosition.y + kameraDistanz * sin(neigung).toFloat(),
        z = fokusPosition.z + horizontalerAbstand * cos(azimut).toFloat(),
    )

    SideEffect {
        cameraNode.position = kameraPosition
        cameraNode.lookAt(targetWorldPosition = fokusPosition, smooth = false)
    }

    SceneView(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { groesse -> ansichtsGroesse = groesse }
            .semantics {
                contentDescription =
                    "3D-Spielbrett mit ${modell.zeilen * modell.spalten * 2} " +
                        "Grunddreiecken und ${modell.auflagen.size} Auflagen"
            },
        engine = engine,
        materialLoader = materialLoader,
        cameraNode = cameraNode,
        cameraManipulator = null,
        onGestureListener = null,
        onTouchEvent = { ereignis, _ ->
            betrachtungsGesten.verarbeite(
                ereignis = ereignis,
                ansichtsGroesse = ansichtsGroesse,
                szenengroesse = szenengroesse,
            )
        },
        autoCenterContent = false,
    ) {
        CubeNode(
            size = Size(
                x = geometrie.breite + BRETT_RAND * 2f,
                y = BRETT_DICKE,
                z = geometrie.tiefe + BRETT_RAND * 2f,
            ),
            materialInstance = brettMaterial,
            position = Position(y = -BRETT_DICKE / 2f),
        )

        geometrie.dreiecke.forEachIndexed { index, dreieck ->
            key(dreieck.position) {
                ShapeNode(
                    polygonPath = dreieck.ecken.map { punkt ->
                        // ShapeNode liegt lokal in XY. -90 Grad um X bildet -Y auf Welt-Z ab.
                        Position2(x = punkt.x, y = -punkt.z)
                    },
                    normal = Direction(z = 1f),
                    materialInstance = grundMaterialien[index % grundMaterialien.size],
                    position = Position(y = OBERFLAECHEN_ABSTAND),
                    rotation = Rotation(x = -90f),
                )
            }
        }

        modell.auflagen.forEach { auflage ->
            key(auflage.position) {
                val grundDreieck = geometrie.dreieck(auflage.position)
                CylinderNode(
                    radius = AUFLAGEN_RADIUS,
                    height = AUFLAGEN_HOEHE,
                    sideCount = 3,
                    materialInstance = auflagenMaterialien.getValue(auflage.typ),
                    position = Position(
                        x = grundDreieck.mittelpunkt.x,
                        y = OBERFLAECHEN_ABSTAND + AUFLAGEN_HOEHE / 2f,
                        z = grundDreieck.mittelpunkt.z,
                    ),
                    rotation = Rotation(
                        y = when (auflage.position.ausrichtung) {
                            DreieckAusrichtung.OBEN -> 90f
                            DreieckAusrichtung.UNTEN -> -90f
                        },
                    ),
                )
            }
        }
    }
}

private class BetrachtungsGesten(
    private val status: BetrachtungsTransformationsStatus,
) {
    private var letzteZeigerAnzahl = 0
    private var letzterSchwerpunktX = 0f
    private var letzterSchwerpunktY = 0f
    private var letzteSpannweite = 0f

    fun verarbeite(
        ereignis: MotionEvent,
        ansichtsGroesse: IntSize,
        szenengroesse: Float,
    ): Boolean {
        when (ereignis.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            -> merkeAktuellePosition(ereignis)

            MotionEvent.ACTION_MOVE -> {
                val zeigerAnzahl = ereignis.pointerCount
                val schwerpunktX = ereignis.schwerpunktX()
                val schwerpunktY = ereignis.schwerpunktY()

                if (zeigerAnzahl == letzteZeigerAnzahl) {
                    val deltaX = schwerpunktX - letzterSchwerpunktX
                    val deltaY = schwerpunktY - letzterSchwerpunktY

                    if (zeigerAnzahl == 1) {
                        status.dreheUmFokus(
                            azimutDeltaGrad = -deltaX * 0.3f,
                            neigungsDeltaGrad = -deltaY * 0.24f,
                        )
                    } else if (zeigerAnzahl >= 2) {
                        val spannweite = ereignis.spannweite()
                        if (letzteSpannweite > 0f && spannweite > 0f) {
                            status.zoome(spannweite / letzteSpannweite)
                        }

                        val kurzeAnsichtsseite = min(
                            ansichtsGroesse.width,
                            ansichtsGroesse.height,
                        )
                        if (kurzeAnsichtsseite > 0) {
                            val welteinheitenProPixel =
                                szenengroesse / kurzeAnsichtsseite / status.zoom
                            status.verschiebeInEbene(
                                rechts = -deltaX * welteinheitenProPixel,
                                vorwaerts = deltaY * welteinheitenProPixel,
                            )
                        }
                        letzteSpannweite = spannweite
                    }
                }

                letzteZeigerAnzahl = zeigerAnzahl
                letzterSchwerpunktX = schwerpunktX
                letzterSchwerpunktY = schwerpunktY
                if (zeigerAnzahl >= 2 && letzteSpannweite == 0f) {
                    letzteSpannweite = ereignis.spannweite()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> zuruecksetzen()
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> zuruecksetzen()
        }
        return true
    }

    private fun merkeAktuellePosition(ereignis: MotionEvent) {
        letzteZeigerAnzahl = ereignis.pointerCount
        letzterSchwerpunktX = ereignis.schwerpunktX()
        letzterSchwerpunktY = ereignis.schwerpunktY()
        letzteSpannweite = if (ereignis.pointerCount >= 2) ereignis.spannweite() else 0f
    }

    private fun zuruecksetzen() {
        letzteZeigerAnzahl = 0
        letzteSpannweite = 0f
    }
}

private fun MotionEvent.schwerpunktX(): Float =
    (0 until pointerCount).sumOf { index -> getX(index).toDouble() }.toFloat() / pointerCount

private fun MotionEvent.schwerpunktY(): Float =
    (0 until pointerCount).sumOf { index -> getY(index).toDouble() }.toFloat() / pointerCount

private fun MotionEvent.spannweite(): Float = if (pointerCount >= 2) {
    hypot(getX(1) - getX(0), getY(1) - getY(0))
} else {
    0f
}

private fun io.github.sceneview.loaders.MaterialLoader.doppelseitigesMaterial(
    farbe: Color,
    rauheit: Float,
): MaterialInstance = createColorInstance(
    color = farbe,
    metallic = 0f,
    roughness = rauheit,
).apply {
    setDoubleSided(true)
}

/**
 * LayoutLib kann Filaments native Engine nicht starten. Diese Vorschau projiziert deshalb exakt
 * dieselbe berechnete Brettgeometrie mit Compose Canvas aus [transformation] in eine schraege
 * Ansicht. Sie wird nur im Inspection Mode verwendet; auf einem Gerät rendert SceneView.
 */
@Composable
private fun SpielbrettVorschau(
    modell: Spielbrett3DModell,
    transformation: BetrachtungsTransformation,
    modifier: Modifier = Modifier,
) {
    val geometrie = remember(modell.zeilen, modell.spalten) {
        berechneSpielbrettGeometrie(modell.zeilen, modell.spalten)
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Vorschau des 3D-Spielbretts"
        },
    ) {
        drawRect(VorschauFarbe)

        val rand = 24.dp.toPx()
        val nutzbareBreite = (size.width - rand * 2f).coerceAtLeast(1f)
        val nutzbareHoehe = (size.height - rand * 2f).coerceAtLeast(1f)
        val azimut = Math.toRadians(transformation.azimutGrad.toDouble())
        val neigung = Math.toRadians(transformation.neigungGrad.toDouble())
        val sinAzimut = sin(azimut).toFloat()
        val cosAzimut = cos(azimut).toFloat()
        val sinNeigung = sin(neigung).toFloat()
        val cosNeigung = cos(neigung).toFloat()

        fun unskalierteProjektion(punkt: VorschauWeltPunkt): VorschauProjektion {
            val x = punkt.x - transformation.fokusX
            val y = punkt.y
            val z = punkt.z - transformation.fokusZ
            return VorschauProjektion(
                horizontal = x * cosAzimut - z * sinAzimut,
                vertikal =
                    x * -sinNeigung * sinAzimut +
                        y * cosNeigung +
                        z * -sinNeigung * cosAzimut,
                tiefe =
                    x * sinAzimut * cosNeigung +
                        y * sinNeigung +
                        z * cosAzimut * cosNeigung,
            )
        }

        val halbeBrettBreite = (geometrie.breite + BRETT_RAND * 2f) / 2f
        val halbeBrettTiefe = (geometrie.tiefe + BRETT_RAND * 2f) / 2f
        val maximaleHoehe = if (modell.auflagen.isEmpty()) 0f else AUFLAGEN_HOEHE
        val begrenzungsPunkte = buildList {
            listOf(-halbeBrettBreite, halbeBrettBreite).forEach { x ->
                listOf(-halbeBrettTiefe, halbeBrettTiefe).forEach { z ->
                    add(unskalierteProjektion(VorschauWeltPunkt(x, -BRETT_DICKE, z)))
                    add(unskalierteProjektion(VorschauWeltPunkt(x, maximaleHoehe, z)))
                }
            }
        }
        val projektionsBreite =
            begrenzungsPunkte.maxOf(VorschauProjektion::horizontal) -
                begrenzungsPunkte.minOf(VorschauProjektion::horizontal)
        val projektionsHoehe =
            begrenzungsPunkte.maxOf(VorschauProjektion::vertikal) -
                begrenzungsPunkte.minOf(VorschauProjektion::vertikal)
        val massstab = min(
            nutzbareBreite / projektionsBreite.coerceAtLeast(0.001f),
            nutzbareHoehe / projektionsHoehe.coerceAtLeast(0.001f),
        ) * transformation.zoom
        val ursprung = Offset(size.width / 2f, size.height / 2f)

        fun VorschauWeltPunkt.alsOffset(): Offset {
            val projektion = unskalierteProjektion(this)
            return Offset(
                x = ursprung.x + projektion.horizontal * massstab,
                y = ursprung.y - projektion.vertikal * massstab,
            )
        }

        fun pfad(punkte: List<VorschauWeltPunkt>): Path = Path().apply {
            val erster = punkte.first().alsOffset()
            moveTo(erster.x, erster.y)
            punkte.drop(1).forEach { punkt ->
                val offset = punkt.alsOffset()
                lineTo(offset.x, offset.y)
            }
            close()
        }

        val obereBrettEcken = listOf(
            VorschauWeltPunkt(-halbeBrettBreite, 0f, -halbeBrettTiefe),
            VorschauWeltPunkt(halbeBrettBreite, 0f, -halbeBrettTiefe),
            VorschauWeltPunkt(halbeBrettBreite, 0f, halbeBrettTiefe),
            VorschauWeltPunkt(-halbeBrettBreite, 0f, halbeBrettTiefe),
        )
        val untereBrettEcken = obereBrettEcken.map { punkt ->
            punkt.copy(y = -BRETT_DICKE)
        }
        drawPath(pfad(untereBrettEcken), BrettSeitenFarbe.abgedunkelt(0.45f))
        obereBrettEcken.indices
            .map { index ->
                val naechsterIndex = (index + 1) % obereBrettEcken.size
                val seite = listOf(
                    untereBrettEcken[index],
                    untereBrettEcken[naechsterIndex],
                    obereBrettEcken[naechsterIndex],
                    obereBrettEcken[index],
                )
                seite to seite.map { unskalierteProjektion(it).tiefe }.average()
            }
            .sortedBy { (_, tiefe) -> tiefe }
            .forEachIndexed { index, (seite, _) ->
                drawPath(
                    path = pfad(seite),
                    color = BrettSeitenFarbe.abgedunkelt(0.58f + index * 0.07f),
                )
            }
        drawPath(pfad(obereBrettEcken), BrettSeitenFarbe)

        geometrie.dreiecke
            .withIndex()
            .sortedBy { (_, dreieck) ->
                unskalierteProjektion(
                    VorschauWeltPunkt(
                        x = dreieck.mittelpunkt.x,
                        y = OBERFLAECHEN_ABSTAND,
                        z = dreieck.mittelpunkt.z,
                    ),
                ).tiefe
            }
            .forEach { (index, dreieck) ->
                val dreieckPfad = pfad(
                    dreieck.ecken.map { punkt ->
                        VorschauWeltPunkt(punkt.x, OBERFLAECHEN_ABSTAND, punkt.z)
                    },
                )
                drawPath(
                    path = dreieckPfad,
                    color = if (index % 2 == 0) GrundFarbeHell else GrundFarbeDunkel,
                )
                drawPath(
                    path = dreieckPfad,
                    color = Color(0xFFB0BEC5).copy(alpha = 0.48f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

        modell.auflagen
            .sortedBy { auflage ->
                val mitte = geometrie.dreieck(auflage.position).mittelpunkt
                unskalierteProjektion(
                    VorschauWeltPunkt(mitte.x, AUFLAGEN_HOEHE / 2f, mitte.z),
                ).tiefe
            }
            .forEach { auflage ->
                val grundDreieck = geometrie.dreieck(auflage.position)
                val auflagenGrundEcken = grundDreieck.ecken.map { ecke ->
                    VorschauWeltPunkt(
                        x = grundDreieck.mittelpunkt.x +
                            (ecke.x - grundDreieck.mittelpunkt.x) *
                            (AUFLAGEN_HOEHE / GRUNDDREIECK_HOEHE),
                        y = OBERFLAECHEN_ABSTAND,
                        z = grundDreieck.mittelpunkt.z +
                            (ecke.z - grundDreieck.mittelpunkt.z) *
                            (AUFLAGEN_HOEHE / GRUNDDREIECK_HOEHE),
                    )
                }
                val auflagenDachEcken = auflagenGrundEcken.map { punkt ->
                    punkt.copy(y = OBERFLAECHEN_ABSTAND + AUFLAGEN_HOEHE)
                }
                auflagenGrundEcken.indices
                    .map { index ->
                        val naechsterIndex = (index + 1) % auflagenGrundEcken.size
                        val seite = listOf(
                            auflagenGrundEcken[index],
                            auflagenGrundEcken[naechsterIndex],
                            auflagenDachEcken[naechsterIndex],
                            auflagenDachEcken[index],
                        )
                        seite to seite.map { unskalierteProjektion(it).tiefe }.average()
                    }
                    .sortedBy { (_, tiefe) -> tiefe }
                    .forEachIndexed { index, (seite, _) ->
                        drawPath(
                            path = pfad(seite),
                            color = auflage.typ.farbe.abgedunkelt(0.52f + index * 0.09f),
                        )
                    }
                val dachPfad = pfad(auflagenDachEcken)
                drawPath(path = dachPfad, color = auflage.typ.farbe)
                drawPath(
                    path = dachPfad,
                    color = Color.White.copy(alpha = 0.72f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
    }
}

private data class VorschauWeltPunkt(
    val x: Float,
    val y: Float,
    val z: Float,
)

private data class VorschauProjektion(
    val horizontal: Float,
    val vertikal: Float,
    val tiefe: Float,
)

private fun Color.abgedunkelt(faktor: Float) = Color(
    red = red * faktor,
    green = green * faktor,
    blue = blue * faktor,
    alpha = alpha,
)

@Preview(
    name = "Dreiecks-Spielbrett",
    showBackground = true,
    backgroundColor = 0xFF263238,
    widthDp = 420,
    heightDp = 340,
)
@Composable
private fun Spielbrett3DPreview() {
    val betrachtungsStatus = rememberBetrachtungsTransformationsStatus(
        BetrachtungsTransformation(
            zoom = 0.88f,
            azimutGrad = 32f,
            neigungGrad = 34f,
        ),
    )
    Spielbrett3D(
        modell = Spielbrett3DModell(
            zeilen = 3,
            spalten = 4,
            auflagen = listOf(
                DreieckAuflage(
                    position = DreieckPosition(0, 0, DreieckAusrichtung.UNTEN),
                    typ = DreieckTyp("Gold", Color(0xFFFFC107), metallisch = 0.65f),
                ),
                DreieckAuflage(
                    position = DreieckPosition(0, 2, DreieckAusrichtung.OBEN),
                    typ = DreieckTyp("Wasser", Color(0xFF2196F3), rauheit = 0.3f),
                ),
                DreieckAuflage(
                    position = DreieckPosition(1, 1, DreieckAusrichtung.UNTEN),
                    typ = DreieckTyp("Wald", Color(0xFF43A047)),
                ),
                DreieckAuflage(
                    position = DreieckPosition(2, 3, DreieckAusrichtung.OBEN),
                    typ = DreieckTyp("Erz", Color(0xFFAB47BC), metallisch = 0.8f),
                ),
            ),
        ),
        modifier = Modifier.fillMaxSize(),
        betrachtungsStatus = betrachtungsStatus,
    )
}
