package de.teutonstudio.zentralbank.anwendung

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.technik.alsHex
import de.teutonstudio.zentralbank.fachlogik.technik.sha256
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

fun SpielZustand.stabilerHash(): String {
    val element = Json.encodeToJsonElement(SpielZustand.serializer(), this)
    return sha256(element.kanonisch().toString().encodeToByteArray()).alsHex()
}

private fun JsonElement.kanonisch(): JsonElement = when (this) {
    is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { (key, value) ->
        key to value.kanonisch()
    })
    is JsonArray -> JsonArray(map(JsonElement::kanonisch))
    else -> this
}
