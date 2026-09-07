package de.teutonstudio.zentralbank.web

import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class BrowserSpielSpeicher(
    private val json: Json,
) {
    private val storage get() = window.localStorage

    fun alle(): List<GespeichertesSpiel> = ids().mapNotNull(::laden)

    fun laden(id: Long): GespeichertesSpiel? = storage
        .getItem(spielSchluessel(id))
        ?.let { text -> runCatching { json.decodeFromString<GespeichertesSpiel>(text) }.getOrNull() }

    fun speichern(spiel: GespeichertesSpiel) {
        storage.setItem(spielSchluessel(spiel.id), json.encodeToString(spiel))
        val neueIds = (ids() + spiel.id).distinct().sorted()
        storage.setItem(INDEX_SCHLUESSEL, neueIds.joinToString(","))
    }

    fun loeschen(id: Long) {
        storage.removeItem(spielSchluessel(id))
        storage.setItem(INDEX_SCHLUESSEL, ids().filterNot { it == id }.joinToString(","))
    }

    fun naechsteId(): Long = (ids().maxOrNull() ?: 0L) + 1L

    private fun ids(): List<Long> = storage.getItem(INDEX_SCHLUESSEL)
        .orEmpty()
        .split(',')
        .mapNotNull(String::toLongOrNull)
        .distinct()

    private fun spielSchluessel(id: Long): String = "$SPIEL_PREFIX$id"

    private companion object {
        const val INDEX_SCHLUESSEL = "fiatreich.v2.spiele"
        const val SPIEL_PREFIX = "fiatreich.v2.spiel."
    }
}
