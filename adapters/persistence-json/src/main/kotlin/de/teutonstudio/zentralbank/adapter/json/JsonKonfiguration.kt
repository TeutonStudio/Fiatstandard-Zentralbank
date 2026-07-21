package de.teutonstudio.zentralbank.adapter.json

import kotlinx.serialization.json.Json

val spielstandJson: Json = Json {
    classDiscriminator = "art"
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}
