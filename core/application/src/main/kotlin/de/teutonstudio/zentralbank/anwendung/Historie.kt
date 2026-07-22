@Serializable
enum class ZugPhase {
    PROZUG,
    EPIZUG;
}

@Serializable
enum class SpielstandHerkunft {
    MENSCHLICH,
    SIMULATION,
    AGENTENLIGA,
    IMPORTIERTE_EPISODE,
}

@Serializable
data class SpielstandMetadaten(
    val herkunft: SpielstandHerkunft,
    val szenarioId: String?,
    val seed: Long?,
    val erstelltAm: String,
    val agenten: List<String> = emptyList(),
    val abgeschlossen: Boolean = false,
    val truncated: Boolean = false,
)

@Serializable
data class SpielStandPosition(
    val positionIndex: Int,
    val ereignisIndexExklusiv: Int,
    val zugId: Long?,
    val runde: Int,
    val spielerId: SpielerId?,
    val spielerName: String?,
    val phase: ZugPhase,
    val istAktuell: Boolean,
    val anzahlZuegeBisAktuell: Int,
)

@Serializable
data class SpielHistorie(
    val spielId: Long,
    val startzustand: SpielZustand,
    val aktuelleZugId: Long,
)

@Serializable
data class HistorischerSpielstand(
    val spielId: Long,
    val position: SpielStandPosition,
    val zustand: SpielZustand,
)
