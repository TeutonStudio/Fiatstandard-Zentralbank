package de.teutonstudio.zentralbank.fachlogik.modell

import de.teutonstudio.zentralbank.fachlogik.technik.alsHex
import de.teutonstudio.zentralbank.fachlogik.technik.konstantGleich
import de.teutonstudio.zentralbank.fachlogik.technik.sha256

private const val PASSWORT_HASH_PREFIX = "sha256:"

fun hasheSpielerPasswort(passwort: String): String {
    require(passwort.isNotBlank()) { "Das Spielerpasswort darf nicht leer sein." }
    return PASSWORT_HASH_PREFIX + sha256(passwort.encodeToByteArray()).alsHex()
}

fun Spieler.pruefePasswort(passwort: String): Boolean {
    if (passwortHash.isBlank()) return true
    val eingabeHash = runCatching { hasheSpielerPasswort(passwort) }.getOrNull() ?: return false
    return konstantGleich(passwortHash.encodeToByteArray(), eingabeHash.encodeToByteArray())
}
