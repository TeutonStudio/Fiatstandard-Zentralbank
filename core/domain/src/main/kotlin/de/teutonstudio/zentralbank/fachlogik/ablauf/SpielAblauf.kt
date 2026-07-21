package de.teutonstudio.zentralbank.fachlogik.ablauf

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.regelwerk.SpielRegelwerk

class SpielAblauf(
    val startzustand: SpielZustand,
    ereignisse: List<SpielEreignis> = emptyList(),
) {
    private val angewandteEreignisse = ereignisse.toMutableList()
    private val wiederholbareEreignisse = mutableListOf<SpielEreignis>()
    private var aktuellerZustand = rekonstruiere(angewandteEreignisse)

    val zustand: SpielZustand
        get() = aktuellerZustand

    val ereignisVerlauf: EreignisVerlauf
        get() = EreignisVerlauf(
            angewandteEreignisse = angewandteEreignisse.toList(),
            wiederholbareEreignisse = wiederholbareEreignisse.toList(),
        )

    fun ereignisAnwenden(ereignis: SpielEreignis): Result<SpielZustand> =
        SpielRegelwerk.wendeAn(aktuellerZustand, ereignis).onSuccess { folgezustand ->
            aktuellerZustand = folgezustand
            angewandteEreignisse += ereignis
            wiederholbareEreignisse.clear()
        }

    fun rueckgaengig(): SpielZustand {
        require(angewandteEreignisse.isNotEmpty()) {
            "Es gibt kein Ereignis zum Zuruecknehmen."
        }
        wiederholbareEreignisse += angewandteEreignisse.removeLast()
        aktuellerZustand = rekonstruiere(angewandteEreignisse)
        return aktuellerZustand
    }

    fun wiederholen(): Result<SpielZustand> {
        val ereignis = wiederholbareEreignisse.removeLastOrNull()
            ?: return Result.failure(
                IllegalStateException("Es gibt kein Ereignis zum Wiederholen."),
            )
        return SpielRegelwerk.wendeAn(aktuellerZustand, ereignis)
            .onSuccess { folgezustand ->
                aktuellerZustand = folgezustand
                angewandteEreignisse += ereignis
            }
            .onFailure {
                wiederholbareEreignisse += ereignis
            }
    }

    fun integritaetPruefen(): Result<Unit> = runCatching {
        val rekonstruierterZustand = rekonstruiere(angewandteEreignisse)
        check(rekonstruierterZustand == aktuellerZustand) {
            "Der gecachte Spielzustand weicht vom Ereignisverlauf ab."
        }
    }

    private fun rekonstruiere(ereignisse: List<SpielEreignis>): SpielZustand =
        ereignisse.fold(startzustand) { zustand, ereignis ->
            SpielRegelwerk.wendeAn(zustand, ereignis).getOrThrow()
        }
}
