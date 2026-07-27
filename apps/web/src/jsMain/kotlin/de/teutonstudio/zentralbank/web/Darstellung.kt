package de.teutonstudio.zentralbank.web

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.ecken

internal fun SpielAktion.anzeigeName(): String = toString()
    .replace("SpielAktion.", "")
    .replace(Regex("([a-zäöü])([A-ZÄÖÜ])"), "$1 $2")

internal fun SpielZustand.zusammenfassungHtml(): String {
    val aktiverName = spieler.firstOrNull { it.id == aktiverSpieler }?.name ?: "–"
    val ergebnisText = ergebnis?.toString() ?: "läuft"
    return """
        <h2>Spielstand</h2>
        <dl class="summary-list">
          <dt>Runde</dt><dd>$rundenzähler</dd>
          <dt>Aktiver Spieler</dt><dd>${aktiverName.escapeHtml()}</dd>
          <dt>Phase</dt><dd>${zugStatus?.phase ?: "–"}</dd>
          <dt>Leitzins</dt><dd>${leitzins.wert / 100.0} %</dd>
          <dt>Status</dt><dd>${ergebnisText.escapeHtml()}</dd>
        </dl>
    """.trimIndent()
}

internal fun SpielZustand.spielerHtml(): String = spieler.joinToString("") { spieler ->
    val aktiv = if (spieler.id == aktiverSpieler) " active" else ""
    val rohstoffe = spieler.rohstoffe.values.sum()
    val bauteile = spieler.bauteile.values.sum()
    """
      <section class="player-card$aktiv">
        <h3>${spieler.name.escapeHtml()}</h3>
        <dl>
          <dt>Geld</dt><dd>${spieler.geldkonto.zuMarkString()}</dd>
          <dt>Rohstoffe</dt><dd>$rohstoffe</dd>
          <dt>Bauteile</dt><dd>$bauteile</dd>
          <dt>Anleihen</dt><dd>${spieler.anleihen.size}</dd>
          <dt>Stil</dt><dd>${spieler.spielstil}</dd>
        </dl>
      </section>
    """.trimIndent()
}

internal fun SpielZustand.kartenSvg(): String {
    val karte = karte ?: return "<p>Keine Karte geladen.</p>"
    val alleEcken = karte.gelaendefelder.flatMap { it.position.ecken() }
    if (alleEcken.isEmpty()) return "<p>Die Karte enthält keine Felder.</p>"
    val minX = alleEcken.minOf(KartenEcke::x)
    val maxX = alleEcken.maxOf(KartenEcke::x)
    val minY = alleEcken.minOf(KartenEcke::y)
    val maxY = alleEcken.maxOf(KartenEcke::y)
    val scaleX = 18.0
    val scaleY = 13.0
    val margin = 28.0
    fun px(ecke: KartenEcke): Double = margin + (ecke.x - minX) * scaleX
    fun py(ecke: KartenEcke): Double = margin + (ecke.y - minY) * scaleY
    val width = margin * 2 + (maxX - minX) * scaleX
    val height = margin * 2 + (maxY - minY) * scaleY

    val felder = karte.gelaendefelder.joinToString("") { feld ->
        val punkte = feld.position.ecken().joinToString(" ") { ecke -> "${px(ecke)},${py(ecke)}" }
        "<polygon class=\"map-field\" points=\"$punkte\" fill=\"${feld.gelaende.farbe()}\"><title>${feld.gelaende}</title></polygon>"
    }
    val linien = karte.belegung.kanten.joinToString("") { belegung ->
        val a = belegung.position.anfang
        val b = belegung.position.ende
        "<line class=\"map-line\" x1=\"${px(a)}\" y1=\"${py(a)}\" x2=\"${px(b)}\" y2=\"${py(b)}\"><title>Handelslinie</title></line>"
    }
    val gebaeude = karte.belegung.ecken.joinToString("") { belegung ->
        "<circle class=\"map-building\" cx=\"${px(belegung.position)}\" cy=\"${py(belegung.position)}\" r=\"7\"><title>${belegung.typ}</title></circle>"
    }
    return """
      <svg viewBox="0 0 $width $height" role="img" aria-label="Spielkarte ${karte.name.escapeHtml()}">
        $felder
        $linien
        $gebaeude
      </svg>
    """.trimIndent()
}

private fun GelaendeTyp.farbe(): String = when (this) {
    GelaendeTyp.EBENE -> "#9f9a63"
    GelaendeTyp.WALD -> "#426b4a"
    GelaendeTyp.GEBIRGE -> "#777d82"
    GelaendeTyp.WUESTE -> "#c79c55"
    GelaendeTyp.SUMPF -> "#526d63"
}

private fun String.escapeHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")
