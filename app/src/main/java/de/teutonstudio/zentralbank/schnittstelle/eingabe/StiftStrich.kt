package de.teutonstudio.zentralbank.schnittstelle.eingabe

import kotlinx.serialization.Serializable

@Serializable
data class StiftStrich(
    val punkte: List<StiftPunkt>,
    val dickeDp: Float = 3f
)
