package de.teutonstudio.zentralbank.ui.eingabe

import kotlinx.serialization.Serializable

@Serializable
data class StiftZeichnung(
    val striche: List<StiftStrich> = emptyList()
)
