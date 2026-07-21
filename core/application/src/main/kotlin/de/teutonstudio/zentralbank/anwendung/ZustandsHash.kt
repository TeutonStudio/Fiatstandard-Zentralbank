package de.teutonstudio.zentralbank.anwendung

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

fun SpielZustand.stabilerHash(): String {
    val element = Json.encodeToJsonElement(SpielZustand.serializer(), this)
    val kanonisch = element.kanonisch().toString().encodeToByteArray()
    return MessageDigest.getInstance("SHA-256")
        .digest(kanonisch)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun JsonElement.kanonisch(): JsonElement = when (this) {
    is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { (key, value) ->
        key to value.kanonisch()
    })
    is JsonArray -> JsonArray(map(JsonElement::kanonisch))
    else -> this
}
