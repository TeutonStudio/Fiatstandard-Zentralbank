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
import androidx.compose.runtime.rememberUpdatedState
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
import io.github.sceneview.utils.screenToRay
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val BRETT_DICKE = 0.12f
private const val OBERFLAECHEN_ABSTAND = 0.004f
private const val WASSER_MINDEST_SICHTWEITE = 10_000f
private const val BRETT_RAND = 0.35f
private const val KAMERA_FOKUS_HOEHE = AUFLAGEN_HOEHE * 0.2f
private const val OBJEKT_BASIS_HOEHE = AUFLAGEN_HOEHE + 0.03f

private val WasserFarbe = Color(0xFF1565A8)
private val WasserRasterHell = Color(0x1A90A4AE)
private val WasserRasterDunkel = Color(0x0D90A4AE)
private val VorschauFarbe = Color(0xFF0B3D66)
private val BrettSeitenFarbe = WasserFarbe
private val GrundFarbeHell = WasserRasterHell
private val GrundFarbeDunkel = WasserRasterDunkel

/**
 * Rendert [modell] als interaktives 3D-Spielbrett mit SceneView und Google Filament.
 *
 * Die Grundflaeche besteht aus gleichseitigen Dreiecken mit der geometrischen Hoehe 2.
 * Jede [DreieckAuflage] wird als deckungsgleiches dreiseitiges Prisma dargestellt. Land und
 * Geländeauflagen liegen dabei auf getrennten visuellen Ebenen. Ziehen dreht die Kamera, eine
 * Zwei-Finger-Geste verschiebt sie und Pinch zoomt.
 *
 * [betrachtungsStatus] enthaelt dieselbe Transformation, die auch die Gesten veraendern. Dadurch
 * kann die Ansicht von aussen beobachtet, ueber Schaltflaechen gesteuert oder gespeichert werden.
 * [kameraInteraktionsModus] schaltet die Ein-Finger-Geste zwischen Orbit-Drehung und einer
 * Verschiebung des Kamerafokus parallel zur Kartenebene um.
 *
 * Beispiel:
 * ```kotlin
 * Spielbrett3D(
 *     modell = Spielbrett3DModell(
 *         hexagon = KartenHexagon(radius = 3),
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
    kameraInteraktionsModus: KameraInteraktionsModus = KameraInteraktionsModus.DREHEN,
    onDreieckBeruehrt: ((DreieckTreffer) -> Unit)? = null,
) {
    if (LocalInspectionMode.current) {
        SpielbrettVorschau(
            modell = modell,
            transformation = betrachtungsStatus.transformation,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val geometrie = remember(modell.hexagon) {
        berechneSpielbrettGeometrie(modell.hexagon)
    }
    val transformation = betrachtungsStatus.transformation
    val rasterMitte = if (modell.unbegrenztesBearbeitungsRaster) {
        geometrie.unbegrenzterTreffer(
            BrettPunkt(transformation.fokusX, transformation.fokusZ),
        )?.position
    } else {
        null
    }
    val rasterGeometrie = remember(geometrie, rasterMitte) {
        rasterMitte?.let { geometrie.rasterAusschnittUm(it) } ?: geometrie
    }
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    var ansichtsGroesse by remember { mutableStateOf(IntSize.Zero) }
    val aktuellerBeruehrungsEmpfaenger = rememberUpdatedState(onDreieckBeruehrt)
    val betrachtungsGesten = remember(
        betrachtungsStatus,
        kameraInteraktionsModus,
        cameraNode,
        geometrie,
        modell.unbegrenztesBearbeitungsRaster,
    ) {
        BetrachtungsGesten(betrachtungsStatus, kameraInteraktionsModus) { x, y ->
            val strahl = cameraNode.view?.screenToRay(x, y) ?: return@BetrachtungsGesten
            if (abs(strahl.direction.y) < 0.0001f) return@BetrachtungsGesten
            val faktor = -strahl.origin.y / strahl.direction.y
            if (faktor < 0f) return@BetrachtungsGesten
            val punkt = BrettPunkt(
                x = strahl.origin.x + strahl.direction.x * faktor,
                z = strahl.origin.z + strahl.direction.z * faktor,
            )
            val treffer = if (modell.unbegrenztesBearbeitungsRaster) {
                geometrie.unbegrenzterTreffer(punkt)
            } else {
                geometrie.treffer(punkt)
            }
            treffer?.let {
                aktuellerBeruehrungsEmpfaenger.value?.invoke(it)
            }
        }
    }
    val aktuelleGesten = rememberUpdatedState(betrachtungsGesten)

    val wasserMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = WasserFarbe,
            metallic = 0.08f,
            roughness = 0.28f,
        )
    }
    val grundMaterialien = remember(materialLoader) {
        listOf(
            materialLoader.doppelseitigesMaterial(WasserRasterHell, rauheit = 0.32f),
            materialLoader.doppelseitigesMaterial(WasserRasterDunkel, rauheit = 0.38f),
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
    val verwendeteObjektTypen = (
        modell.eckObjekte.map(EckObjektAuflage::typ) +
            modell.kantenObjekte.map(KantenObjektAuflage::typ) +
            modell.feldObjekte.map(FeldObjektAuflage::typ)
        ).distinct()
    val objektMaterialien = remember(materialLoader, verwendeteObjektTypen) {
        verwendeteObjektTypen.associateWith { typ ->
            materialLoader.createColorInstance(
                color = typ.farbe,
                metallic = if (typ.zustand == ObjektDarstellungsZustand.ZERSTOERT) 0.05f else 0.3f,
                roughness = if (typ.zustand == ObjektDarstellungsZustand.AUSGEWAEHLT) 0.25f else 0.62f,
            )
        }
    }

    val szenengroesse = max(
        max(geometrie.breite, geometrie.tiefe),
        if (modell.unbegrenztesBearbeitungsRaster) 24f else 0f,
    )
    val wasserAusdehnung = max(szenengroesse * 64f, WASSER_MINDEST_SICHTWEITE)
    val kameraAbstand = szenengroesse * 1.15f + AUFLAGEN_HOEHE * 2f
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
                    "Hexagonales 3D-Spielbrett mit ${modell.hexagon.anzahlFelder} " +
                        "Dreiecken und ${modell.auflagen.size} Auflagen"
            },
        engine = engine,
        materialLoader = materialLoader,
        cameraNode = cameraNode,
        cameraManipulator = null,
        onGestureListener = null,
        onTouchEvent = { ereignis, _ ->
            aktuelleGesten.value.verarbeite(
                ereignis = ereignis,
                ansichtsGroesse = ansichtsGroesse,
                szenengroesse = szenengroesse,
            )
        },
        autoCenterContent = false,
    ) {
        if (modell.zeigeWasserFlaeche) {
            CubeNode(
                size = Size(
                    x = wasserAusdehnung,
                    y = BRETT_DICKE,
                    z = wasserAusdehnung,
                ),
                materialInstance = wasserMaterial,
                position = Position(
                    x = transformation.fokusX,
                    y = -BRETT_DICKE / 2f,
                    z = transformation.fokusZ,
                ),
            )
        }

        if (modell.zeigeBearbeitungsRaster) {
            rasterGeometrie.dreiecke.forEachIndexed { index, dreieck ->
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
        }

        modell.auflagen.forEach { auflage ->
            key(auflage.position, auflage.ebene) {
                val grundDreieck = geometrie.dreieck(auflage.position)
                val basisHoehe = when (auflage.ebene) {
                    AuflagenEbene.LAND -> 0f
                    AuflagenEbene.SPEZIAL -> AUFLAGEN_HOEHE
                }
                val hoehe = when (auflage.ebene) {
                    AuflagenEbene.LAND -> AUFLAGEN_HOEHE
                    AuflagenEbene.SPEZIAL -> SPEZIAL_AUFLAGEN_HOEHE
                }
                CylinderNode(
                    radius = AUFLAGEN_RADIUS,
                    height = hoehe,
                    sideCount = 3,
                    materialInstance = auflagenMaterialien.getValue(auflage.typ),
                    position = Position(
                        x = grundDreieck.mittelpunkt.x,
                        y = OBERFLAECHEN_ABSTAND + basisHoehe + hoehe / 2f,
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

        modell.kantenObjekte.forEach { objekt ->
            val anfang = geometrie.punkt(objekt.position.anfang)
            val ende = geometrie.punkt(objekt.position.ende)
            if (anfang != null && ende != null) {
                key(objekt.position, objekt.typ.name, objekt.typ.zustand) {
                    val deltaX = ende.x - anfang.x
                    val deltaZ = ende.z - anfang.z
                    val laenge = hypot(deltaX, deltaZ)
                    val markierung = objekt.typ.form == SpielObjektForm.MARKIERUNG
                    val seeweg = objekt.typ.form == SpielObjektForm.FRACHTSCHIFF
                    CubeNode(
                        size = Size(
                            x = laenge,
                            y = if (markierung || seeweg) 0.10f else 0.16f,
                            z = if (markierung) 0.16f else if (seeweg) 0.08f else 0.12f,
                        ),
                        materialInstance = objektMaterialien.getValue(objekt.typ),
                        position = Position(
                            x = (anfang.x + ende.x) / 2f,
                            y = OBJEKT_BASIS_HOEHE + if (markierung) 0.08f else 0.12f,
                            z = (anfang.z + ende.z) / 2f,
                        ),
                        rotation = Rotation(
                            y = -Math.toDegrees(atan2(deltaZ, deltaX).toDouble()).toFloat(),
                        ),
                    )
                }
            }
        }

        modell.feldObjekte.forEach { objekt ->
            key(objekt.position, objekt.typ.name, objekt.typ.zustand) {
                val mitte = geometrie.dreieck(objekt.position).mittelpunkt
                val (radius, hoehe, seiten) = objekt.typ.feldAbmessungen()
                CylinderNode(
                    radius = radius,
                    height = hoehe,
                    sideCount = seiten,
                    materialInstance = objektMaterialien.getValue(objekt.typ),
                    position = Position(
                        x = mitte.x,
                        y = OBJEKT_BASIS_HOEHE + hoehe / 2f,
                        z = mitte.z,
                    ),
                )
            }
        }

        modell.eckObjekte.forEach { objekt ->
            val punkt = geometrie.punkt(objekt.position)
            if (punkt != null) {
                key(objekt.position, objekt.typ.name, objekt.typ.zustand) {
                    val (radius, hoehe, seiten) = objekt.typ.eckAbmessungen()
                    CylinderNode(
                        radius = radius,
                        height = hoehe,
                        sideCount = seiten,
                        materialInstance = objektMaterialien.getValue(objekt.typ),
                        position = Position(
                            x = punkt.x,
                            y = OBJEKT_BASIS_HOEHE + hoehe / 2f,
                            z = punkt.z,
                        ),
                    )
                }
            }
        }
    }
}

private data class ObjektAbmessungen(
    val radius: Float,
    val hoehe: Float,
    val seiten: Int,
)

private fun SpielObjektTyp.eckAbmessungen(): ObjektAbmessungen = when (form) {
    SpielObjektForm.HAUPTBAHNHOF -> ObjektAbmessungen(0.34f, 0.92f, 6)
    SpielObjektForm.BAHNHOF -> ObjektAbmessungen(0.28f, 0.64f, 4)
    SpielObjektForm.GROSSBAHNHOF -> ObjektAbmessungen(0.36f, 0.88f, 4)
    SpielObjektForm.HAFEN -> ObjektAbmessungen(0.30f, 0.54f, 8)
    SpielObjektForm.GROSSHAFEN -> ObjektAbmessungen(0.39f, 0.72f, 8)
    SpielObjektForm.MARKIERUNG -> ObjektAbmessungen(0.46f, 0.12f, 12)
    else -> ObjektAbmessungen(0.28f, 0.55f, 6)
}

private fun SpielObjektTyp.feldAbmessungen(): ObjektAbmessungen = when (form) {
    SpielObjektForm.ABBAUEINHEIT -> ObjektAbmessungen(0.32f, 0.46f, 8)
    SpielObjektForm.GESCHAEFTSBANK -> ObjektAbmessungen(0.36f, 0.58f, 4)
    SpielObjektForm.PANZER -> ObjektAbmessungen(0.34f, 0.38f, 4)
    SpielObjektForm.KRIEGSSCHIFF -> ObjektAbmessungen(0.38f, 0.30f, 3)
    SpielObjektForm.MARKIERUNG -> ObjektAbmessungen(0.50f, 0.08f, 12)
    else -> ObjektAbmessungen(0.30f, 0.44f, 6)
}

private class BetrachtungsGesten(
    private val status: BetrachtungsTransformationsStatus,
    private val einFingerModus: KameraInteraktionsModus,
    private val beiTippen: (x: Float, y: Float) -> Unit,
) {
    private var letzteZeigerAnzahl = 0
    private var letzterSchwerpunktX = 0f
    private var letzterSchwerpunktY = 0f
    private var letzteSpannweite = 0f
    private var startX = 0f
    private var startY = 0f
    private var wurdeBewegt = false

    fun verarbeite(
        ereignis: MotionEvent,
        ansichtsGroesse: IntSize,
        szenengroesse: Float,
    ): Boolean {
        when (ereignis.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ereignis.x
                startY = ereignis.y
                wurdeBewegt = false
                merkeAktuellePosition(ereignis)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                wurdeBewegt = true
                merkeAktuellePosition(ereignis)
            }

            MotionEvent.ACTION_MOVE -> {
                val zeigerAnzahl = ereignis.pointerCount
                val schwerpunktX = ereignis.schwerpunktX()
                val schwerpunktY = ereignis.schwerpunktY()
                if (hypot(schwerpunktX - startX, schwerpunktY - startY) > 12f) {
                    wurdeBewegt = true
                }

                if (zeigerAnzahl == letzteZeigerAnzahl) {
                    val deltaX = schwerpunktX - letzterSchwerpunktX
                    val deltaY = schwerpunktY - letzterSchwerpunktY

                    if (zeigerAnzahl == 1) {
                        when (einFingerModus) {
                            KameraInteraktionsModus.DREHEN -> status.dreheUmFokus(
                                azimutDeltaGrad = -deltaX * 0.3f,
                                neigungsDeltaGrad = -deltaY * 0.24f,
                            )
                            KameraInteraktionsModus.VERSCHIEBEN -> {
                                val welteinheitenProPixel = berechneWelteinheitenProPixel(
                                    ansichtsGroesse = ansichtsGroesse,
                                    szenengroesse = szenengroesse,
                                    zoom = status.zoom,
                                )
                                if (welteinheitenProPixel != null) {
                                    status.verschiebeDurchBildschirmgeste(
                                        deltaX = deltaX,
                                        deltaY = deltaY,
                                        welteinheitenProPixel = welteinheitenProPixel,
                                    )
                                }
                            }
                        }
                    } else if (zeigerAnzahl >= 2) {
                        val spannweite = ereignis.spannweite()
                        if (letzteSpannweite > 0f && spannweite > 0f) {
                            status.zoome(spannweite / letzteSpannweite)
                        }

                        val welteinheitenProPixel = berechneWelteinheitenProPixel(
                            ansichtsGroesse = ansichtsGroesse,
                            szenengroesse = szenengroesse,
                            zoom = status.zoom,
                        )
                        if (welteinheitenProPixel != null) {
                            status.verschiebeDurchBildschirmgeste(
                                deltaX = deltaX,
                                deltaY = deltaY,
                                welteinheitenProPixel = welteinheitenProPixel,
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

            MotionEvent.ACTION_POINTER_UP -> {
                wurdeBewegt = true
                zuruecksetzen()
            }
            MotionEvent.ACTION_UP -> {
                if (!wurdeBewegt) beiTippen(ereignis.x, ereignis.y)
                zuruecksetzen()
            }
            MotionEvent.ACTION_CANCEL -> zuruecksetzen()
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

private fun berechneWelteinheitenProPixel(
    ansichtsGroesse: IntSize,
    szenengroesse: Float,
    zoom: Float,
): Float? {
    val kurzeAnsichtsseite = min(ansichtsGroesse.width, ansichtsGroesse.height)
    return kurzeAnsichtsseite.takeIf { it > 0 }
        ?.let { seite -> szenengroesse / seite / zoom }
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
    val geometrie = remember(modell.hexagon) {
        berechneSpielbrettGeometrie(modell.hexagon)
    }
    val rasterMitte = geometrie.unbegrenzterTreffer(
        BrettPunkt(transformation.fokusX, transformation.fokusZ),
    )?.position
    val rasterGeometrie = remember(geometrie, rasterMitte) {
        rasterMitte?.let { geometrie.rasterAusschnittUm(it) } ?: geometrie
    }
    val rahmenGeometrie = if (modell.unbegrenztesBearbeitungsRaster) {
        rasterGeometrie
    } else {
        geometrie
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

        val halbeBrettBreite = (rahmenGeometrie.breite + BRETT_RAND * 2f) / 2f
        val halbeBrettTiefe = (rahmenGeometrie.tiefe + BRETT_RAND * 2f) / 2f
        val maximaleHoehe = when {
            modell.eckObjekte.isNotEmpty() -> OBJEKT_BASIS_HOEHE + 0.92f
            modell.auflagen.any { it.ebene == AuflagenEbene.SPEZIAL } ->
                AUFLAGEN_HOEHE + SPEZIAL_AUFLAGEN_HOEHE
            modell.auflagen.isNotEmpty() -> AUFLAGEN_HOEHE
            else -> 0f
        }
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
        if (modell.zeigeWasserFlaeche) {
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
        }

        if (modell.zeigeBearbeitungsRaster) {
            rasterGeometrie.dreiecke
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
                val basisHoehe = when (auflage.ebene) {
                    AuflagenEbene.LAND -> 0f
                    AuflagenEbene.SPEZIAL -> AUFLAGEN_HOEHE
                }
                val hoehe = when (auflage.ebene) {
                    AuflagenEbene.LAND -> AUFLAGEN_HOEHE
                    AuflagenEbene.SPEZIAL -> SPEZIAL_AUFLAGEN_HOEHE
                }
                val auflagenGrundEcken = grundDreieck.ecken.map { ecke ->
                    VorschauWeltPunkt(
                        x = ecke.x,
                        y = OBERFLAECHEN_ABSTAND + basisHoehe,
                        z = ecke.z,
                    )
                }
                val auflagenDachEcken = auflagenGrundEcken.map { punkt ->
                    punkt.copy(y = punkt.y + hoehe)
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

        modell.kantenObjekte.forEach { objekt ->
            val anfang = geometrie.punkt(objekt.position.anfang) ?: return@forEach
            val ende = geometrie.punkt(objekt.position.ende) ?: return@forEach
            drawLine(
                color = objekt.typ.farbe,
                start = VorschauWeltPunkt(anfang.x, OBJEKT_BASIS_HOEHE, anfang.z).alsOffset(),
                end = VorschauWeltPunkt(ende.x, OBJEKT_BASIS_HOEHE, ende.z).alsOffset(),
                strokeWidth = if (objekt.typ.form == SpielObjektForm.MARKIERUNG) {
                    6.dp.toPx()
                } else {
                    4.dp.toPx()
                },
            )
        }
        modell.feldObjekte.forEach { objekt ->
            val mitte = geometrie.dreieck(objekt.position).mittelpunkt
            drawCircle(
                color = objekt.typ.farbe,
                radius = if (objekt.typ.form == SpielObjektForm.MARKIERUNG) 8.dp.toPx() else 6.dp.toPx(),
                center = VorschauWeltPunkt(mitte.x, OBJEKT_BASIS_HOEHE, mitte.z).alsOffset(),
            )
        }
        modell.eckObjekte.forEach { objekt ->
            val punkt = geometrie.punkt(objekt.position) ?: return@forEach
            drawCircle(
                color = objekt.typ.farbe,
                radius = if (objekt.typ.form == SpielObjektForm.MARKIERUNG) 9.dp.toPx() else 7.dp.toPx(),
                center = VorschauWeltPunkt(punkt.x, OBJEKT_BASIS_HOEHE, punkt.z).alsOffset(),
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
            hexagon = KartenHexagon(radius = 6),
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
