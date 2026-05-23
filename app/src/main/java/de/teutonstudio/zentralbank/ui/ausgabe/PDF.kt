package de.teutonstudio.zentralbank.ui.ausgabe

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os.remove
import android.text.TextUtils.replace
import android.view.View
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.pdf.viewer.fragment.PdfViewerFragment
import de.teutonstudio.zentralbank.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

class PdfDocument private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {

    val seitenAnzahl: Int
        get() = renderer.pageCount

    fun renderSeite(
        seite: Int,
        breitePx: Int
    ): Bitmap {
        synchronized(renderer) {
            val page = renderer.openPage(seite)

            try {
                val faktor = breitePx.toFloat() / page.width.toFloat()
                val hoehePx = (page.height * faktor).roundToInt()

                val bitmap = Bitmap.createBitmap(
                    breitePx,
                    hoehePx,
                    Bitmap.Config.ARGB_8888
                )

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                val matrix = Matrix().apply {
                    postScale(faktor, faktor)
                }

                page.render(
                    bitmap,
                    null,
                    matrix,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                return bitmap
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        renderer.close()
        pfd.close()
    }

    companion object {
        fun open(file: File): PdfDocument {
            val pfd = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            return PdfDocument(
                pfd = pfd,
                renderer = PdfRenderer(pfd)
            )
        }
    }
}

@Composable
fun PdfAnzeige(
    datei: File,
    modifier: Modifier = Modifier
) {
    val istPreview = LocalInspectionMode.current

    if (istPreview) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("PDF-Anzeige läuft nicht in der Compose Preview")
        }
        return
    }

    val dokument by produceState<PdfDocument?>(initialValue = null, datei) {
        val geöffnet = withContext(Dispatchers.IO) {
            PdfDocument.open(datei)
        }

        value = geöffnet

        awaitDispose {
            geöffnet.close()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val breitePx = with(density) {
            maxWidth.roundToPx()
        }.coerceAtLeast(1)

        val pdf = dokument

        if (pdf == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pdf.seitenAnzahl) { seite ->
                    PdfSeite(
                        dokument = pdf,
                        seite = seite,
                        breitePx = breitePx
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfSeite(
    dokument: PdfDocument,
    seite: Int,
    breitePx: Int,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, dokument, seite, breitePx) {
        value = withContext(Dispatchers.IO) {
            dokument.renderSeite(
                seite = seite,
                breitePx = breitePx
            )
        }
    }

    if (bitmap == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "PDF-Seite ${seite + 1}",
            modifier = modifier.fillMaxWidth()
        )
    }
}

fun Context.rawPdfAlsDatei(
    @RawRes resId: Int,
    dateiname: String
): File {
    val ziel = File(cacheDir, dateiname)

    if (!ziel.exists()) {
        resources.openRawResource(resId).use { input ->
            ziel.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    return ziel
}

fun Context.rawPdfUri(
    @RawRes resId: Int,
    dateiname: String
): Uri {
    val datei = File(cacheDir, dateiname)

    if (!datei.exists()) {
        resources.openRawResource(resId).use { input ->
            datei.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    return FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        datei
    )
}

@Composable
fun PdfAnzeigeMitLinks(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val fragmentManager = activity.supportFragmentManager

    val containerId = remember { View.generateViewId() }
    val tag = remember { "pdf_viewer_$containerId" }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerId
            }
        },
        update = {
            val vorhandenesFragment =
                fragmentManager.findFragmentByTag(tag) as? PdfViewerFragment

            val fragment = vorhandenesFragment ?: PdfViewerFragment()

            if (vorhandenesFragment == null) {
                fragmentManager
                    .beginTransaction()
                    .replace(containerId, fragment, tag)
                    .commitNow()
            }

            fragment.documentUri = uri
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            val fragment: Fragment? = fragmentManager.findFragmentByTag(tag)

            if (fragment != null && !fragmentManager.isStateSaved) {
                fragmentManager
                    .beginTransaction()
                    .remove(fragment)
                    .commitNow()
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity {
    var current = this

    while (current is ContextWrapper) {
        if (current is FragmentActivity) {
            return current
        }

        current = current.baseContext
    }

    error("PdfAnzeigeMitLinks braucht eine FragmentActivity als Host.")
}

private fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("Kein Activity-Kontext gefunden")
}

@Preview
@Composable
private fun PDFPreview() {
    val context = LocalContext.current
    val datei = remember {
        context.rawPdfAlsDatei(
            resId = R.raw.handbuch,
            dateiname = "handbuch.pdf"
        )
    }

    PdfAnzeige(
        datei = datei,
        modifier = Modifier.fillMaxSize(),
    )
}
