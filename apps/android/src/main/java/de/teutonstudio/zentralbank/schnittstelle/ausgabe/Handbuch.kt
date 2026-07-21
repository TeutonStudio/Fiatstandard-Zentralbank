package de.teutonstudio.zentralbank.schnittstelle.ausgabe

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.R

@Composable
fun zeigeHandbuch(modifier: Modifier) {
    val context = LocalContext.current
    val uri = remember { context.rawPdfUri(
        resId = R.raw.handbuch,
        dateiname = "handbuch.pdf"
    ) }
    PdfAnzeigeMitLinks(uri,modifier)
}

@Preview
@Composable
private fun HandbuchPreview() {
    zeigeHandbuch(Modifier.fillMaxSize())
}