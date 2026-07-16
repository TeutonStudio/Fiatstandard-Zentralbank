package de.teutonstudio.zentralbank.fachlogik.ablauf

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis

data class EreignisVerlauf(
    val angewandteEreignisse: List<SpielEreignis>,
    val wiederholbareEreignisse: List<SpielEreignis>,
)
