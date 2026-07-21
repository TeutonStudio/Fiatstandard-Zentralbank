package de.teutonstudio.zentralbank.schnittstelle.eingabe

import kotlinx.serialization.Serializable

@Serializable
data class StiftPunkt(
    val x: Float,
    val y: Float
)
