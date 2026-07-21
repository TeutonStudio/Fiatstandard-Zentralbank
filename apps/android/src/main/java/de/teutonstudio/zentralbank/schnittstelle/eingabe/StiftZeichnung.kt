package de.teutonstudio.zentralbank.schnittstelle.eingabe

import kotlinx.serialization.Serializable

@Serializable
data class StiftZeichnung(
    val striche: List<StiftStrich> = emptyList()
)
