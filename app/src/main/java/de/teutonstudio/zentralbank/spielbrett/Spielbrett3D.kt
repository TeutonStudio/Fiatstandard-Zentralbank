package de.teutonstudio.zentralbank.spielbrett

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.SceneView
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.material.setColor
import io.github.sceneview.material.setMetallic
import io.github.sceneview.material.setReflectance
import io.github.sceneview.material.setRoughness
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Position2
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.node.TextNode as SceneTextNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.utils.screenToRay
import io.github.sceneview.utils.worldToScreen
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.sin

private const val BRETT_DICKE = 0.12f
private const val WASSER_GRUND_NIVEAU = -0.04f
private const val HIMMELSRICHTUNG_ABSTAND_ZUM_WASSER = 0.065f
internal const val OBERFLAECHEN_ABSTAND = 0.004f
private const val WASSER_MINDEST_SICHTWEITE = 10_000f
private const val BRETT_RAND = 0.35f
private const val KAMERA_FOKUS_HOEHE = AUFLAGEN_HOEHE * 0.2f
private const val OBJEKT_BASIS_HOEHE = AUFLAGEN_HOEHE + 0.03f

private val WasserFarbe = Color(0xFF1565A8)
private val NachtWasserFarbe = Color(0xFF061A31)
private val WasserRasterHell = Color(0x1A90A4AE)
private val WasserRasterDunkel = Color(0x0D90A4AE)
private val VorschauFarbe = Color(0xFF0B3D66)
private val TagesHimmelFarbe = Color(0xFF4A90D9)
private val DaemmerungsHimmelFarbe = Color(0xFF5B446A)
private val NachtHimmelFarbe = Color(0xFF020817)
private val SonnenFarbe = Color(0xFFFFD27A)
private val MondFarbe = Color(0xFFDCE8F6)
private val BrettSeitenFarbe = WasserFarbe
private val GrundFarbeHell = WasserRasterHell
private val GrundFarbeDunkel = WasserRasterDunkel

/**
 * Rendert [modell] als interaktives 3D-Spielbrett mit SceneView und Google Filament.
 *
 * Die Grundflaeche besteht aus gleichseitigen Dreiecken mit der geometrischen Hoehe 2.
 * Land wird mit abgeschraegten Kanten dargestellt; Spezialauflagen liegen auf einer getrennten
 * visuellen Ebene. Ziehen dreht die Kamera, eine Zwei-Finger-Geste verschiebt sie und Pinch zoomt.
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
    statischeVorschau: Boolean = false,
    himmel: HimmelsDarstellung = HimmelsDarstellung.fuerUhrzeit(12f),
    eingabeAktiv: Boolean = true,
    bauwerkInfoFreiraum: PaddingValues = PaddingValues(),
) {
    if (LocalInspectionMode.current || statischeVorschau) {
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
    val bauwerkHoverZiele = remember(modell, geometrie) {
        erstelleBauwerkHoverZiele(modell, geometrie)
    }
    var bauwerkUnterZeiger by remember(modell, geometrie) {
        mutableStateOf<BauwerkHoverZiel?>(null)
    }
    var angehefteteBauwerke by remember(modell, geometrie) {
        mutableStateOf<List<BauwerkHoverZiel>>(emptyList())
    }
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader)
    val cameraNode = rememberCameraNode(engine)
    val lichtVektor = himmel.lichtVektor()
    val sonnenHoehenAnteil = (himmel.lichtHoeheGrad / 58f).coerceIn(0f, 1f)
    val himmelsFarbe = himmel.himmelsFarbe()
    val lichtFarbe = if (himmel.mondSichtbarkeit > himmel.sonnenSichtbarkeit) {
        colorOf(Color(0xFF9CB9E8))
    } else {
        colorOf(lerp(Color(0xFFFFA45B), Color.White, sqrt(sonnenHoehenAnteil)))
    }
    val hauptlichtIntensitaet = if (himmel.mondSichtbarkeit > himmel.sonnenSichtbarkeit) {
        350f + 650f * himmel.mondSichtbarkeit
    } else {
        1_500f + 11_500f * sonnenHoehenAnteil
    }
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = hauptlichtIntensitaet
        lightDirection = Direction(-lichtVektor.x, -lichtVektor.y, -lichtVektor.z)
        color = lichtFarbe
        isShadowCaster = true
    }
    val fillLightNode = rememberFillLightNode(engine) {
        intensity = if (himmel.nachtAnteil > 0f) {
            120f + 180f * himmel.nachtAnteil
        } else {
            700f + 1_300f * sonnenHoehenAnteil
        }
        lightDirection = Direction(
            lichtVektor.x * 0.6f,
            -max(lichtVektor.y, 0.25f),
            lichtVektor.z * 0.6f,
        )
    }
    var ansichtsGroesse by remember { mutableStateOf(IntSize.Zero) }
    val aktuellerBeruehrungsEmpfaenger = rememberUpdatedState(onDreieckBeruehrt)
    val betrachtungsGesten = remember(
        betrachtungsStatus,
        kameraInteraktionsModus,
        cameraNode,
        geometrie,
        modell.unbegrenztesBearbeitungsRaster,
        bauwerkHoverZiele,
    ) {
        BetrachtungsGesten(betrachtungsStatus, kameraInteraktionsModus) { x, y ->
            val strahl = cameraNode.view?.screenToRay(x, y) ?: return@BetrachtungsGesten
            val angeklicktesBauwerk = bauwerkHoverZiele.findeTreffer(
                ursprungX = strahl.origin.x,
                ursprungY = strahl.origin.y,
                ursprungZ = strahl.origin.z,
                richtungX = strahl.direction.x,
                richtungY = strahl.direction.y,
                richtungZ = strahl.direction.z,
            )
            angehefteteBauwerke = angeklicktesBauwerk
                ?.let { ziel -> bauwerkHoverZiele.angehefteteZieleFuer(ziel) }
                .orEmpty()
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
    val sternenMaterial = remember(materialLoader) {
        materialLoader.createUnlitColorInstance(Color.White.copy(alpha = 0.001f)).apply {
            setDoubleSided(true)
        }
    }
    val sonnenMaterial = remember(materialLoader) {
        materialLoader.createUnlitColorInstance(SonnenFarbe)
    }
    val mondMaterial = remember(materialLoader) {
        materialLoader.createUnlitColorInstance(MondFarbe)
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
    val gelaendeMeshes = remember(geometrie, modell.auflagen) {
        erstelleAbgeschraegteGelaendeMeshes(geometrie, modell.auflagen)
    }
    val wasserMesh = remember(geometrie, modell.auflagen, modell.zeigeWasserFlaeche) {
        if (modell.zeigeWasserFlaeche) {
            erstelleAbgeschraegtesWasserMesh(geometrie, modell.auflagen)
        } else {
            null
        }
    }
    val wasserRandMesh = remember(geometrie, modell.zeigeWasserFlaeche) {
        if (modell.zeigeWasserFlaeche) {
            erstelleAbgeschraegtenWasserRand(
                geometrie = geometrie,
                aussenY = WASSER_GRUND_NIVEAU,
            )
        } else {
            null
        }
    }
    val gebirgsPyramidenMesh = remember(geometrie, modell.auflagen) {
        erstelleGebirgsPyramidenMesh(geometrie, modell.auflagen)
    }
    val gebirgsTyp = remember(modell.auflagen) {
        modell.auflagen.firstOrNull { auflage ->
            auflage.ebene == AuflagenEbene.LAND &&
                auflage.typ.relief == DreieckRelief.GEBIRGE
        }?.typ
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
    val verwaltungsKernMaterialien = remember(materialLoader, verwendeteObjektTypen) {
        verwendeteObjektTypen
            .filter(SpielObjektTyp::istVerwaltungsstandort)
            .associateWith { typ -> materialLoader.createUnlitColorInstance(typ.farbe) }
    }
    val verwaltungsHaloMaterialien = remember(materialLoader, verwendeteObjektTypen) {
        verwendeteObjektTypen
            .filter(SpielObjektTyp::istVerwaltungsstandort)
            .associateWith { typ ->
                materialLoader.createUnlitColorInstance(typ.farbe.copy(alpha = 0.001f))
            }
    }

    SideEffect {
        environment.skybox?.setColor(colorOf(himmelsFarbe).toFloatArray())
        environment.indirectLight?.intensity = if (himmel.nachtAnteil > 0f) {
            500f + 700f * (1f - himmel.nachtAnteil)
        } else {
            3_000f + 7_000f * sonnenHoehenAnteil
        }
        wasserMaterial.setColor(lerp(WasserFarbe, NachtWasserFarbe, himmel.nachtAnteil))
        wasserMaterial.setMetallic(0.08f + 0.52f * himmel.nachtAnteil)
        wasserMaterial.setRoughness(0.28f - 0.15f * himmel.nachtAnteil)
        wasserMaterial.setReflectance(0.45f + 0.4f * himmel.nachtAnteil)
        sternenMaterial.setColor(Color.White.copy(alpha = 0.92f * himmel.sterneSichtbarkeit))
        verwaltungsHaloMaterialien.forEach { (typ, material) ->
            material.setColor(
                typ.farbe.copy(alpha = 0.34f * himmel.nachtAnteil.coerceIn(0f, 1f)),
            )
        }
    }

    val szenengroesse = max(
        max(geometrie.breite, geometrie.tiefe),
        if (modell.unbegrenztesBearbeitungsRaster) 24f else 0f,
    )
    val richtungsGroesse = (szenengroesse * 0.08f).coerceIn(0.75f, 1.25f)
    val richtungsAbstand = richtungsGroesse * 0.65f + WASSER_BEVEL_BREITE
    val brettMinX = geometrie.dreiecke.minOf { dreieck ->
        dreieck.ecken.minOf(BrettPunkt::x)
    }
    val brettMaxX = geometrie.dreiecke.maxOf { dreieck ->
        dreieck.ecken.maxOf(BrettPunkt::x)
    }
    val brettMinZ = geometrie.dreiecke.minOf { dreieck ->
        dreieck.ecken.minOf(BrettPunkt::z)
    }
    val brettMaxZ = geometrie.dreiecke.maxOf { dreieck ->
        dreieck.ecken.maxOf(BrettPunkt::z)
    }
    val brettMitteX = (brettMinX + brettMaxX) / 2f
    val brettMitteZ = (brettMinZ + brettMaxZ) / 2f
    val richtungsPositionen = remember(
        brettMinX,
        brettMaxX,
        brettMinZ,
        brettMaxZ,
        richtungsAbstand,
    ) {
        listOf(
            "N" to Position(x = brettMitteX, z = brettMinZ - richtungsAbstand),
            "O" to Position(x = brettMaxX + richtungsAbstand, z = brettMitteZ),
            "S" to Position(x = brettMitteX, z = brettMaxZ + richtungsAbstand),
            "W" to Position(x = brettMinX - richtungsAbstand, z = brettMitteZ),
        )
    }
    val richtungsBitmaps = remember {
        listOf("N", "O", "S", "W").associateWith { richtung ->
            SceneTextNode.renderTextBitmap(
                text = richtung,
                fontSize = 176f,
                textColor = if (richtung == "N") {
                    0xFFD32F2F.toInt()
                } else {
                    android.graphics.Color.WHITE
                },
                backgroundColor = android.graphics.Color.TRANSPARENT,
                bitmapWidth = 256,
                bitmapHeight = 256,
            )
        }
    }
    val himmelsRadius = max(szenengroesse * 3.2f, 32f)
    val sternenMesh = remember(himmelsRadius) {
        erstelleSternenhimmelMesh(himmelsRadius)
    }
    val wasserAusdehnung = max(szenengroesse * 64f, WASSER_MINDEST_SICHTWEITE)
    val wasserGrundPfad = remember(geometrie, wasserRandMesh, wasserAusdehnung) {
        val halbeAusdehnung = wasserAusdehnung / 2f
        val aussenKontur = listOf(
            Position2(x = -halbeAusdehnung, y = halbeAusdehnung),
            Position2(x = halbeAusdehnung, y = halbeAusdehnung),
            Position2(x = halbeAusdehnung, y = -halbeAusdehnung),
            Position2(x = -halbeAusdehnung, y = -halbeAusdehnung),
        )
        val brettAussparung = (wasserRandMesh?.aussenKontur ?: geometrie.aussenKontur())
            .map { punkt ->
                Position2(x = punkt.x, y = -punkt.z)
            }
        aussenKontur + brettAussparung
    }
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
    val himmelskoerperPosition = Position(
        x = fokusPosition.x + lichtVektor.x * himmelsRadius * 0.88f,
        y = fokusPosition.y + lichtVektor.y * himmelsRadius * 0.88f,
        z = fokusPosition.z + lichtVektor.z * himmelsRadius * 0.88f,
    )
    val sichtbareBauwerkInfos = angehefteteBauwerke.ifEmpty {
        listOfNotNull(bauwerkUnterZeiger)
    }
    var bauwerkInfoPositionen by remember {
        mutableStateOf<Map<String, Offset>>(emptyMap())
    }

    SideEffect {
        cameraNode.position = kameraPosition
        cameraNode.lookAt(targetWorldPosition = fokusPosition, smooth = false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        SceneView(
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { groesse -> ansichtsGroesse = groesse }
                .pointerInput(bauwerkHoverZiele, cameraNode) {
                    awaitPointerEventScope {
                        while (true) {
                            val ereignis = awaitPointerEvent(PointerEventPass.Initial)
                            val aenderung = ereignis.changes.firstOrNull()
                            when (ereignis.type) {
                                PointerEventType.Exit -> bauwerkUnterZeiger = null
                                PointerEventType.Enter,
                                PointerEventType.Move -> if (
                                    aenderung?.type == PointerType.Mouse ||
                                    aenderung?.type == PointerType.Stylus
                                ) {
                                    val strahl = cameraNode.view?.screenToRay(
                                        aenderung.position.x,
                                        aenderung.position.y,
                                    )
                                    bauwerkUnterZeiger = strahl?.let { aktuellerStrahl ->
                                        bauwerkHoverZiele.findeTreffer(
                                            ursprungX = aktuellerStrahl.origin.x,
                                            ursprungY = aktuellerStrahl.origin.y,
                                            ursprungZ = aktuellerStrahl.origin.z,
                                            richtungX = aktuellerStrahl.direction.x,
                                            richtungY = aktuellerStrahl.direction.y,
                                            richtungZ = aktuellerStrahl.direction.z,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription =
                        "Hexagonales 3D-Spielbrett mit ${modell.hexagon.anzahlFelder} " +
                            "Dreiecken und ${modell.auflagen.size} Auflagen"
                },
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            environment = environment,
            mainLightNode = mainLightNode,
            fillLightNode = fillLightNode,
            cameraNode = cameraNode,
            cameraManipulator = null,
            onGestureListener = null,
            onTouchEvent = { ereignis, _ ->
                if (eingabeAktiv) {
                    aktuelleGesten.value.verarbeite(
                        ereignis = ereignis,
                        ansichtsGroesse = ansichtsGroesse,
                        szenengroesse = szenengroesse,
                    )
                } else {
                    true
                }
            },
            onFrame = {
                val neuePositionen = cameraNode.view?.let { ansicht ->
                    sichtbareBauwerkInfos.mapNotNull { ziel ->
                        ansicht.worldToScreen(ziel.infoPosition)?.let { position ->
                            ziel.schluessel to Offset(position.x, position.y)
                        }
                    }.toMap()
                }.orEmpty()
                if (bauwerkInfoPositionen != neuePositionen) {
                    bauwerkInfoPositionen = neuePositionen
                }
            },
            autoCenterContent = false,
        ) {
            SternenhimmelNode(
                meshDaten = sternenMesh,
                materialInstance = sternenMaterial,
                position = Position(x = fokusPosition.x, z = fokusPosition.z),
            )
            if (himmel.sonnenSichtbarkeit > 0.01f) {
                SphereNode(
                    radius = himmelsRadius * 0.022f,
                    stacks = 10,
                    slices = 14,
                    materialInstance = sonnenMaterial,
                    position = himmelskoerperPosition,
                )
            }
            if (himmel.mondSichtbarkeit > 0.01f) {
                SphereNode(
                    radius = himmelsRadius * 0.018f,
                    stacks = 10,
                    slices = 14,
                    materialInstance = mondMaterial,
                    position = himmelskoerperPosition,
                )
            }

            if (modell.zeigeWasserFlaeche) {
                ShapeNode(
                    polygonPath = wasserGrundPfad,
                    polygonHoles = listOf(4),
                    normal = Direction(z = 1f),
                    materialInstance = wasserMaterial,
                    position = Position(y = WASSER_GRUND_NIVEAU),
                    rotation = Rotation(x = -90f),
                )
                wasserRandMesh?.let { randDaten ->
                    key("wasser-hexagon-bevel") {
                        AbgeschraegtesGelaendeNode(
                            meshDaten = randDaten.mesh,
                            materialInstance = wasserMaterial,
                        )
                    }
                }
                wasserMesh?.let { meshDaten ->
                    key("abgeschraegtes-wasser") {
                        AbgeschraegtesGelaendeNode(
                            meshDaten = meshDaten,
                            materialInstance = wasserMaterial,
                        )
                    }
                }
                richtungsPositionen.forEach { (richtung, position) ->
                    key("himmelsrichtung", richtung) {
                        ImageNode(
                            bitmap = richtungsBitmaps.getValue(richtung),
                            size = Size(x = richtungsGroesse, z = richtungsGroesse),
                            normal = Direction(y = 1f),
                            position = position.copy(
                                y = WASSER_GRUND_NIVEAU + HIMMELSRICHTUNG_ABSTAND_ZUM_WASSER,
                            ),
                            rotation = Rotation(y = transformation.azimutGrad),
                        )
                    }
                }
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

            gelaendeMeshes.forEach { (typ, meshDaten) ->
                key("abgeschraegtes-gelaende", typ) {
                    AbgeschraegtesGelaendeNode(
                        meshDaten = meshDaten,
                        materialInstance = auflagenMaterialien.getValue(typ),
                    )
                }
            }

            if (gebirgsPyramidenMesh != null && gebirgsTyp != null) {
                key("gebirgs-pyramiden") {
                    AbgeschraegtesGelaendeNode(
                        meshDaten = gebirgsPyramidenMesh,
                        materialInstance = auflagenMaterialien.getValue(gebirgsTyp),
                    )
                }
            }

            modell.auflagen.filter { it.ebene == AuflagenEbene.SPEZIAL }.forEach { auflage ->
                key(auflage.position, auflage.ebene) {
                    val grundDreieck = geometrie.dreieck(auflage.position)
                    CylinderNode(
                        radius = AUFLAGEN_RADIUS,
                        height = SPEZIAL_AUFLAGEN_HOEHE,
                        sideCount = 3,
                        materialInstance = auflagenMaterialien.getValue(auflage.typ),
                        position = Position(
                            x = grundDreieck.mittelpunkt.x,
                            y = OBERFLAECHEN_ABSTAND + AUFLAGEN_HOEHE +
                                SPEZIAL_AUFLAGEN_HOEHE / 2f,
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
                        val leuchtet =
                            objekt.typ.istVerwaltungsstandort && himmel.nachtAnteil > 0.04f
                        CylinderNode(
                            radius = radius,
                            height = hoehe,
                            sideCount = seiten,
                            materialInstance = if (leuchtet) {
                                verwaltungsKernMaterialien.getValue(objekt.typ)
                            } else {
                                objektMaterialien.getValue(objekt.typ)
                            },
                            position = Position(
                                x = punkt.x,
                                y = OBJEKT_BASIS_HOEHE + hoehe / 2f,
                                z = punkt.z,
                            ),
                        )
                        if (objekt.typ.istVerwaltungsstandort) {
                            CylinderNode(
                                radius = radius * 1.34f,
                                height = hoehe * 1.08f,
                                sideCount = 16,
                                materialInstance = verwaltungsHaloMaterialien.getValue(objekt.typ),
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
        BauwerkInfoEbene(
            ziele = sichtbareBauwerkInfos,
            positionen = bauwerkInfoPositionen,
            ansichtsGroesse = ansichtsGroesse,
            freiraum = bauwerkInfoFreiraum,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun BauwerkInfoEbene(
    ziele: List<BauwerkHoverZiel>,
    positionen: Map<String, Offset>,
    ansichtsGroesse: IntSize,
    freiraum: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dichte = LocalDensity.current
    val layoutRichtung = LocalLayoutDirection.current
    val systemFreiraum = WindowInsets.safeDrawing.asPaddingValues()
    val randPixel = with(dichte) { 8.dp.toPx() }
    val sicherheitsBereich = BauwerkInfoSicherheitsBereich(
        links = max(
            randPixel,
            max(
                with(dichte) { freiraum.calculateLeftPadding(layoutRichtung).toPx() },
                with(dichte) { systemFreiraum.calculateLeftPadding(layoutRichtung).toPx() },
            ),
        ),
        oben = max(
            randPixel,
            max(
                with(dichte) { freiraum.calculateTopPadding().toPx() },
                with(dichte) { systemFreiraum.calculateTopPadding().toPx() },
            ),
        ),
        rechts = max(
            randPixel,
            max(
                with(dichte) { freiraum.calculateRightPadding(layoutRichtung).toPx() },
                with(dichte) { systemFreiraum.calculateRightPadding(layoutRichtung).toPx() },
            ),
        ),
        unten = max(
            randPixel,
            max(
                with(dichte) { freiraum.calculateBottomPadding().toPx() },
                with(dichte) { systemFreiraum.calculateBottomPadding().toPx() },
            ),
        ),
    )
    val verfuegbareBreitePixel = (
        ansichtsGroesse.width - sicherheitsBereich.links - sicherheitsBereich.rechts
        ).coerceAtLeast(0f)
    val verfuegbareHoehePixel = (
        ansichtsGroesse.height - sicherheitsBereich.oben - sicherheitsBereich.unten
        ).coerceAtLeast(0f)
    val kartenBreitePixel = min(
        with(dichte) { 300.dp.toPx() },
        verfuegbareBreitePixel,
    )
    var kartenGroessen by remember {
        mutableStateOf<Map<String, IntSize>>(emptyMap())
    }
    if (kartenBreitePixel <= 0f || verfuegbareHoehePixel <= 0f) return
    val kartenBreite = with(dichte) { kartenBreitePixel.toDp() }
    val maximaleKartenHoehe = with(dichte) { verfuegbareHoehePixel.toDp() }

    Box(modifier = modifier) {
        ziele.forEach { ziel ->
            val position = positionen[ziel.schluessel] ?: return@forEach
            key("bauwerk-info-karte", ziel.schluessel) {
                val kartenGroesse = kartenGroessen[ziel.schluessel] ?: IntSize.Zero
                val scrollStatus = rememberScrollState()
                Surface(
                    modifier = Modifier
                        .width(kartenBreite)
                        .heightIn(max = maximaleKartenHoehe)
                        .onSizeChanged { neueGroesse ->
                            if (kartenGroessen[ziel.schluessel] != neueGroesse) {
                                kartenGroessen = kartenGroessen +
                                    (ziel.schluessel to neueGroesse)
                            }
                        }
                        .offset {
                            begrenzeBauwerkInfoPosition(
                                anker = position,
                                kartenGroesse = kartenGroesse,
                                ansichtsGroesse = ansichtsGroesse,
                                sicherheitsBereich = sicherheitsBereich,
                            )
                        },
                    shape = MaterialTheme.shapes.medium,
                    color = ziel.typ.farbe.copy(alpha = 0.96f),
                    contentColor = if (ziel.typ.farbe.luminance() > 0.48f) {
                        Color.Black
                    } else {
                        Color.White
                    },
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollStatus)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = "Bauwerksdetails",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        val infos = ziel.typ.infos.ifEmpty {
                            listOf(SpielObjektInfoEintrag("Gebäude", ziel.typ.name))
                        }
                        infos.forEach { info ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = info.bezeichnung,
                                    modifier = Modifier.width(104.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = info.wert,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class BauwerkInfoSicherheitsBereich(
    val links: Float,
    val oben: Float,
    val rechts: Float,
    val unten: Float,
)

internal fun begrenzeBauwerkInfoPosition(
    anker: Offset,
    kartenGroesse: IntSize,
    ansichtsGroesse: IntSize,
    sicherheitsBereich: BauwerkInfoSicherheitsBereich,
): IntOffset {
    val maximaleXPosition = (
        ansichtsGroesse.width - kartenGroesse.width - sicherheitsBereich.rechts
        ).coerceAtLeast(sicherheitsBereich.links)
    val maximaleYPosition = (
        ansichtsGroesse.height - kartenGroesse.height - sicherheitsBereich.unten
        ).coerceAtLeast(sicherheitsBereich.oben)
    return IntOffset(
        x = (anker.x - kartenGroesse.width / 2f)
            .coerceIn(sicherheitsBereich.links, maximaleXPosition)
            .roundToInt(),
        y = (anker.y - kartenGroesse.height - 8f)
            .coerceIn(sicherheitsBereich.oben, maximaleYPosition)
            .roundToInt(),
    )
}

private fun HimmelsDarstellung.himmelsFarbe(): Color = if (nachtAnteil > 0f) {
    lerp(DaemmerungsHimmelFarbe, NachtHimmelFarbe, nachtAnteil)
} else {
    val tagesHelligkeit = sqrt((lichtHoeheGrad / 22f).coerceIn(0f, 1f))
    lerp(DaemmerungsHimmelFarbe, TagesHimmelFarbe, tagesHelligkeit)
}

private fun erstelleSternenhimmelMesh(radius: Float): GelaendeMeshDaten {
    val ecken = mutableListOf<GelaendeMeshEcke>()
    val indizes = mutableListOf<Int>()
    repeat(72) { index ->
        val azimutAnteil = ((index + 1) * 0.61803398875f) % 1f
        val hoehenAnteil = ((index + 1) * 0.754877666f) % 1f
        val azimut = 2.0 * Math.PI * azimutAnteil
        val hoehe = Math.toRadians((12f + 68f * hoehenAnteil).toDouble())
        val cosHoehe = cos(hoehe).toFloat()
        val richtung = GelaendeMeshVektor(
            x = sin(azimut).toFloat() * cosHoehe,
            y = sin(hoehe).toFloat(),
            z = -cos(azimut).toFloat() * cosHoehe,
        )
        val rechts = GelaendeMeshVektor(
            x = cos(azimut).toFloat(),
            y = 0f,
            z = sin(azimut).toFloat(),
        )
        val oben = GelaendeMeshVektor(
            x = -sin(azimut).toFloat() * sin(hoehe).toFloat(),
            y = cosHoehe,
            z = cos(azimut).toFloat() * sin(hoehe).toFloat(),
        )
        val mitte = richtung * radius
        val groesse = radius * (0.0018f + 0.0016f * ((index * 37) % 11) / 10f)
        val basis = ecken.size
        val normale = richtung * -1f
        listOf(
            mitte - rechts * groesse - oben * groesse,
            mitte + rechts * groesse - oben * groesse,
            mitte + rechts * groesse + oben * groesse,
            mitte - rechts * groesse + oben * groesse,
        ).forEach { position -> ecken += GelaendeMeshEcke(position, normale) }
        indizes += listOf(basis, basis + 1, basis + 2, basis, basis + 2, basis + 3)
    }
    return GelaendeMeshDaten(ecken, indizes)
}

private operator fun GelaendeMeshVektor.times(faktor: Float) = GelaendeMeshVektor(
    x = x * faktor,
    y = y * faktor,
    z = z * faktor,
)

private operator fun GelaendeMeshVektor.plus(anderer: GelaendeMeshVektor) = GelaendeMeshVektor(
    x = x + anderer.x,
    y = y + anderer.y,
    z = z + anderer.z,
)

private operator fun GelaendeMeshVektor.minus(anderer: GelaendeMeshVektor) = GelaendeMeshVektor(
    x = x - anderer.x,
    y = y - anderer.y,
    z = z - anderer.z,
)

@SuppressLint("RestrictedApi")
@Composable
private fun SceneScope.SternenhimmelNode(
    meshDaten: GelaendeMeshDaten,
    materialInstance: MaterialInstance,
    position: Position,
) {
    val node = remember(engine, meshDaten, materialInstance) {
        val geometrie = Geometry.Builder()
            .vertices(
                meshDaten.ecken.map { ecke ->
                    Geometry.Vertex(
                        position = Position(
                            x = ecke.position.x,
                            y = ecke.position.y,
                            z = ecke.position.z,
                        ),
                        normal = Direction(
                            x = ecke.normale.x,
                            y = ecke.normale.y,
                            z = ecke.normale.z,
                        ),
                    )
                },
            )
            .indices(meshDaten.indizes)
            .build(engine)
        GeometryNode(engine, geometrie, materialInstance)
    }
    SideEffect { node.position = position }
    NodeLifecycle(node, content = null)
}

@SuppressLint("RestrictedApi")
@Composable
private fun SceneScope.AbgeschraegtesGelaendeNode(
    meshDaten: GelaendeMeshDaten,
    materialInstance: MaterialInstance,
) {
    val node = remember(engine, meshDaten, materialInstance) {
        val geometrie = Geometry.Builder()
            .vertices(
                meshDaten.ecken.map { ecke ->
                    Geometry.Vertex(
                        position = Position(
                            x = ecke.position.x,
                            y = ecke.position.y,
                            z = ecke.position.z,
                        ),
                        normal = Direction(
                            x = ecke.normale.x,
                            y = ecke.normale.y,
                            z = ecke.normale.z,
                        ),
                    )
                },
            )
            .indices(meshDaten.indizes)
            .build(engine)
        GeometryNode(
            engine = engine,
            geometry = geometrie,
            materialInstance = materialInstance,
        )
    }
    NodeLifecycle(node, content = null)
}

private data class ObjektAbmessungen(
    val radius: Float,
    val hoehe: Float,
    val seiten: Int,
)

internal data class BauwerkHoverZiel(
    val schluessel: String,
    val typ: SpielObjektTyp,
    val trefferAnfang: Position,
    val trefferEnde: Position,
    val trefferRadius: Float,
    val prioritaet: Int,
    val infoPosition: Position,
) {
    fun trefferWertung(
        ursprungX: Float,
        ursprungY: Float,
        ursprungZ: Float,
        richtungX: Float,
        richtungY: Float,
        richtungZ: Float,
    ): Float? {
        val abstandQuadrat = quadratischerAbstandZwischenStrahlUndStrecke(
            ursprungX = ursprungX,
            ursprungY = ursprungY,
            ursprungZ = ursprungZ,
            richtungX = richtungX,
            richtungY = richtungY,
            richtungZ = richtungZ,
            anfang = trefferAnfang,
            ende = trefferEnde,
        )
        val radiusQuadrat = trefferRadius * trefferRadius
        return if (abstandQuadrat <= radiusQuadrat) {
            abstandQuadrat / radiusQuadrat
        } else {
            null
        }
    }
}

private fun erstelleBauwerkHoverZiele(
    modell: Spielbrett3DModell,
    geometrie: SpielbrettGeometrie,
): List<BauwerkHoverZiel> = buildList {
    modell.kantenObjekte
        .filter { objekt -> objekt.typ.istBauwerk }
        .forEach { objekt ->
            val anfang = geometrie.punkt(objekt.position.anfang) ?: return@forEach
            val ende = geometrie.punkt(objekt.position.ende) ?: return@forEach
            val mitteX = (anfang.x + ende.x) / 2f
            val mitteZ = (anfang.z + ende.z) / 2f
            add(
                BauwerkHoverZiel(
                    schluessel = "kante:${objekt.position}:${objekt.typ.name}",
                    typ = objekt.typ,
                    trefferAnfang = Position(
                        x = anfang.x,
                        y = OBJEKT_BASIS_HOEHE + 0.12f,
                        z = anfang.z,
                    ),
                    trefferEnde = Position(
                        x = ende.x,
                        y = OBJEKT_BASIS_HOEHE + 0.12f,
                        z = ende.z,
                    ),
                    trefferRadius = 0.30f,
                    prioritaet = 2,
                    infoPosition = Position(
                        x = mitteX,
                        y = OBJEKT_BASIS_HOEHE + 0.78f,
                        z = mitteZ,
                    ),
                ),
            )
        }

    modell.feldObjekte
        .filter { objekt -> objekt.typ.istBauwerk }
        .forEach { objekt ->
            val mitte = geometrie.dreieck(objekt.position).mittelpunkt
            val abmessungen = objekt.typ.feldAbmessungen()
            add(
                BauwerkHoverZiel(
                    schluessel = "feld:${objekt.position}:${objekt.typ.name}",
                    typ = objekt.typ,
                    trefferAnfang = Position(
                        x = mitte.x,
                        y = OBJEKT_BASIS_HOEHE,
                        z = mitte.z,
                    ),
                    trefferEnde = Position(
                        x = mitte.x,
                        y = OBJEKT_BASIS_HOEHE + abmessungen.hoehe,
                        z = mitte.z,
                    ),
                    trefferRadius = max(abmessungen.radius * 1.35f, 0.44f),
                    prioritaet = 1,
                    infoPosition = Position(
                        x = mitte.x,
                        y = OBJEKT_BASIS_HOEHE + abmessungen.hoehe + 0.42f,
                        z = mitte.z,
                    ),
                ),
            )
        }

    modell.eckObjekte
        .filter { objekt -> objekt.typ.istBauwerk }
        .forEach { objekt ->
            val punkt = geometrie.punkt(objekt.position) ?: return@forEach
            val abmessungen = objekt.typ.eckAbmessungen()
            add(
                BauwerkHoverZiel(
                    schluessel = "ecke:${objekt.position}:${objekt.typ.name}",
                    typ = objekt.typ,
                    trefferAnfang = Position(
                        x = punkt.x,
                        y = OBJEKT_BASIS_HOEHE,
                        z = punkt.z,
                    ),
                    trefferEnde = Position(
                        x = punkt.x,
                        y = OBJEKT_BASIS_HOEHE + abmessungen.hoehe,
                        z = punkt.z,
                    ),
                    trefferRadius = max(abmessungen.radius * 1.55f, 0.48f),
                    prioritaet = 0,
                    infoPosition = Position(
                        x = punkt.x,
                        y = OBJEKT_BASIS_HOEHE + abmessungen.hoehe + 0.42f,
                        z = punkt.z,
                    ),
                ),
            )
        }
}

private val SpielObjektTyp.istBauwerk: Boolean
    get() = when (form) {
        SpielObjektForm.TEICH,
        SpielObjektForm.FRACHTSCHIFF,
        SpielObjektForm.PANZER,
        SpielObjektForm.KRIEGSSCHIFF,
        SpielObjektForm.MARKIERUNG -> false
        else -> true
    }

internal fun List<BauwerkHoverZiel>.findeTreffer(
    ursprungX: Float,
    ursprungY: Float,
    ursprungZ: Float,
    richtungX: Float,
    richtungY: Float,
    richtungZ: Float,
): BauwerkHoverZiel? = mapNotNull { ziel ->
    ziel.trefferWertung(
        ursprungX = ursprungX,
        ursprungY = ursprungY,
        ursprungZ = ursprungZ,
        richtungX = richtungX,
        richtungY = richtungY,
        richtungZ = richtungZ,
    )?.let { wertung -> ziel to wertung }
}.minWithOrNull(
    compareBy<Pair<BauwerkHoverZiel, Float>> { (ziel, _) -> ziel.prioritaet }
        .thenBy { (_, wertung) -> wertung },
)?.first

internal fun List<BauwerkHoverZiel>.angehefteteZieleFuer(
    ziel: BauwerkHoverZiel,
): List<BauwerkHoverZiel> {
    if (ziel.typ.form != SpielObjektForm.HAUPTBAHNHOF) return listOf(ziel)
    val spieler = ziel.typ.spieler.singleOrNull() ?: return listOf(ziel)
    return filter { kandidat -> spieler in kandidat.typ.spieler }
        .distinctBy(BauwerkHoverZiel::schluessel)
}

private fun quadratischerAbstandZwischenStrahlUndStrecke(
    ursprungX: Float,
    ursprungY: Float,
    ursprungZ: Float,
    richtungX: Float,
    richtungY: Float,
    richtungZ: Float,
    anfang: Position,
    ende: Position,
): Float {
    val segmentX = ende.x - anfang.x
    val segmentY = ende.y - anfang.y
    val segmentZ = ende.z - anfang.z
    val strahlLaengeQuadrat =
        richtungX * richtungX + richtungY * richtungY + richtungZ * richtungZ
    val segmentLaengeQuadrat =
        segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ
    if (strahlLaengeQuadrat <= 0.000001f) return Float.POSITIVE_INFINITY

    fun abstandQuadrat(strahlAnteil: Float, segmentAnteil: Float): Float {
        val strahlX = ursprungX + richtungX * strahlAnteil
        val strahlY = ursprungY + richtungY * strahlAnteil
        val strahlZ = ursprungZ + richtungZ * strahlAnteil
        val segmentPunktX = anfang.x + segmentX * segmentAnteil
        val segmentPunktY = anfang.y + segmentY * segmentAnteil
        val segmentPunktZ = anfang.z + segmentZ * segmentAnteil
        val deltaX = strahlX - segmentPunktX
        val deltaY = strahlY - segmentPunktY
        val deltaZ = strahlZ - segmentPunktZ
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
    }

    fun strahlAnteilZu(punkt: Position): Float = (
        ((punkt.x - ursprungX) * richtungX +
            (punkt.y - ursprungY) * richtungY +
            (punkt.z - ursprungZ) * richtungZ) /
            strahlLaengeQuadrat
        ).coerceAtLeast(0f)

    var minimum = abstandQuadrat(strahlAnteilZu(anfang), 0f)
    minimum = min(minimum, abstandQuadrat(strahlAnteilZu(ende), 1f))
    if (segmentLaengeQuadrat <= 0.000001f) return minimum

    val segmentAnteilAmUrsprung = (
        ((ursprungX - anfang.x) * segmentX +
            (ursprungY - anfang.y) * segmentY +
            (ursprungZ - anfang.z) * segmentZ) /
            segmentLaengeQuadrat
        ).coerceIn(0f, 1f)
    minimum = min(minimum, abstandQuadrat(0f, segmentAnteilAmUrsprung))

    val ursprungZuAnfangX = ursprungX - anfang.x
    val ursprungZuAnfangY = ursprungY - anfang.y
    val ursprungZuAnfangZ = ursprungZ - anfang.z
    val richtungMalSegment =
        richtungX * segmentX + richtungY * segmentY + richtungZ * segmentZ
    val richtungMalUrsprung =
        richtungX * ursprungZuAnfangX +
            richtungY * ursprungZuAnfangY +
            richtungZ * ursprungZuAnfangZ
    val segmentMalUrsprung =
        segmentX * ursprungZuAnfangX +
            segmentY * ursprungZuAnfangY +
            segmentZ * ursprungZuAnfangZ
    val nenner = strahlLaengeQuadrat * segmentLaengeQuadrat -
        richtungMalSegment * richtungMalSegment
    if (abs(nenner) > 0.000001f) {
        val strahlAnteil = (
            richtungMalSegment * segmentMalUrsprung -
                segmentLaengeQuadrat * richtungMalUrsprung
            ) / nenner
        val segmentAnteil = (
            strahlLaengeQuadrat * segmentMalUrsprung -
                richtungMalSegment * richtungMalUrsprung
            ) / nenner
        if (strahlAnteil >= 0f && segmentAnteil in 0f..1f) {
            minimum = min(minimum, abstandQuadrat(strahlAnteil, segmentAnteil))
        }
    }
    return minimum
}

private fun SpielObjektTyp.eckAbmessungen(): ObjektAbmessungen = when (form) {
    SpielObjektForm.TEICH -> ObjektAbmessungen(0.58f, 0.04f, 32)
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
 * Diese Vorschau projiziert dieselbe berechnete Brettgeometrie mit Compose Canvas aus
 * [transformation] in eine schräge Ansicht. Sie wird im Inspection Mode sowie für kleine,
 * nicht interaktive Kartenvorschauen verwendet; interaktive Ansichten rendern mit SceneView.
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
                radius = when (objekt.typ.form) {
                    SpielObjektForm.MARKIERUNG -> 9.dp.toPx()
                    SpielObjektForm.TEICH -> 11.dp.toPx()
                    else -> 7.dp.toPx()
                },
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
