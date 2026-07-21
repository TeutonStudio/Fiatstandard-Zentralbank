package de.teutonstudio.zentralbank.fachlogik.modell

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val PASSWORT_HASH_PREFIX = "sha256:"

fun hasheSpielerPasswort(passwort: String): String {
    require(passwort.isNotBlank()) { "Das Spielerpasswort darf nicht leer sein." }
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(passwort.toByteArray(StandardCharsets.UTF_8))
    return PASSWORT_HASH_PREFIX + bytes.joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

fun Spieler.pruefePasswort(passwort: String): Boolean {
    if (passwortHash.isBlank()) return true
    val eingabeHash = runCatching { hasheSpielerPasswort(passwort) }.getOrNull() ?: return false
    return MessageDigest.isEqual(
        passwortHash.toByteArray(StandardCharsets.UTF_8),
        eingabeHash.toByteArray(StandardCharsets.UTF_8),
    )
}
