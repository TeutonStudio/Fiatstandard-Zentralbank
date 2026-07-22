package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@Serializable
enum class SpielerStil {
    VORSICHTIG,
    PRODUKTIONSORIENTIERT,
    SCHULDENFINANZIERT,
    HANDELSORIENTIERT,
    AGGRESSIV,
    OPPORTUNISTISCH,
    EXPANSIONISTISCH,
    DEFENSIVE_DOMINANZ,
}
