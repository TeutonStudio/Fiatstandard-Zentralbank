package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

object SpielRegelwerk {
    fun wendeAn(
        zustand: SpielZustand,
        ereignis: SpielEreignis,
    ): Result<SpielZustand> = runCatching {
        ZugRegelwerk.pruefeFreigabe(zustand, ereignis)
        when (ereignis) {
            is SpielEreignis.WarenkorbGeaendert ->
                RohstoffRegelwerk.warenkorbAendern(zustand, ereignis)
            is SpielEreignis.RohstoffEinnahme ->
                RohstoffRegelwerk.rohstoffeEinnehmen(zustand, ereignis)
            is SpielEreignis.RohstoffAusgabe ->
                RohstoffRegelwerk.rohstoffeAusgeben(zustand, ereignis)
            is SpielEreignis.Transaktion ->
                FinanzRegelwerk.geldUebertragen(zustand, ereignis)
            is SpielEreignis.RohstoffHandel ->
                HandelsRegelwerk.rohstoffHandeln(zustand, ereignis)
            is SpielEreignis.AnleiheGekauft ->
                AnleihenRegelwerk.anleiheKaufen(zustand, ereignis)
            is SpielEreignis.AnleiheVerkauft ->
                AnleihenRegelwerk.anleiheVerkaufen(zustand, ereignis)
            is SpielEreignis.AnleiheFaellig ->
                AnleihenRegelwerk.anleiheFaelligStellen(zustand, ereignis)
            is SpielEreignis.Expansion ->
                ExpansionsRegelwerk.expandieren(zustand, ereignis)
            is SpielEreignis.KriegErklaert ->
                KonfliktRegelwerk.kriegErklaeren(zustand, ereignis)
            is SpielEreignis.KriegBeendet ->
                KonfliktRegelwerk.kriegBeenden(zustand, ereignis)
            is SpielEreignis.Schuldenstrich ->
                InsolvenzRegelwerk.schuldenstrichBuchen(zustand, ereignis)
            is SpielEreignis.SchrittAbgeschlossen ->
                ZugRegelwerk.schrittAbschliessen(zustand, ereignis)
            is SpielEreignis.PhaseAbgeschlossen ->
                ZugRegelwerk.phaseAbschliessen(zustand, ereignis)
            SpielEreignis.ZugBeendet -> ZugRegelwerk.zugBeenden(zustand)
        }
    }
}
