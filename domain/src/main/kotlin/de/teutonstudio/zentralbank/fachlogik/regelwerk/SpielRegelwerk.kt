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
            is SpielEreignis.ProzugBegonnen ->
                ProzugRegelwerk.beginnen(zustand, ereignis)
            is SpielEreignis.VerarbeitungAusgefuehrt ->
                ProzugRegelwerk.verarbeiten(zustand, ereignis)
            is SpielEreignis.VerwaltungsstandortVersorgt ->
                ProzugRegelwerk.verwaltungsstandortVersorgen(zustand, ereignis)
            is SpielEreignis.VerbindlichkeitBeglichen ->
                ProzugRegelwerk.verbindlichkeitBegleichen(zustand, ereignis)
            is SpielEreignis.ProzugErfolgreichAbgeschlossen ->
                ProzugRegelwerk.erfolgreichAbschliessen(zustand, ereignis)
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
            is SpielEreignis.AuslandsHandel ->
                HandelsRegelwerk.mitAuslandHandeln(zustand, ereignis)
            is SpielEreignis.AnleiheEmittiert ->
                AnleihenRegelwerk.anleiheEmittieren(zustand, ereignis)
            is SpielEreignis.AnleiheFreiwilligZurueckgekauft ->
                AnleihenRegelwerk.freiwilligZurueckkaufen(zustand, ereignis)
            is SpielEreignis.AnleiheGekauft ->
                AnleihenRegelwerk.anleiheKaufen(zustand, ereignis)
            is SpielEreignis.AnleiheVerkauft ->
                AnleihenRegelwerk.anleiheVerkaufen(zustand, ereignis)
            is SpielEreignis.AnleiheFaellig ->
                AnleihenRegelwerk.anleiheFaelligStellen(zustand, ereignis)
            is SpielEreignis.Expansion ->
                ExpansionsRegelwerk.expandieren(zustand, ereignis)
            is SpielEreignis.HauptbahnhofPlatziert ->
                KartenRegelwerk.hauptbahnhofPlatzieren(zustand, ereignis)
            is SpielEreignis.EckGebaeudeGebaut ->
                KartenRegelwerk.eckGebaeudeBauen(zustand, ereignis)
            is SpielEreignis.EckGebaeudeAufgewertet ->
                KartenRegelwerk.eckGebaeudeAufwerten(zustand, ereignis)
            is SpielEreignis.SchieneGebaut ->
                KartenRegelwerk.schieneBauen(zustand, ereignis)
            is SpielEreignis.NeutraleAnlageErrichtet ->
                KartenRegelwerk.neutraleAnlageErrichten(zustand, ereignis)
            is SpielEreignis.KartenBelegungEntfernt ->
                KartenRegelwerk.belegungEntfernen(zustand, ereignis)
            is SpielEreignis.KartenBauwerkZustandGeaendert ->
                KartenRegelwerk.bauwerkZustandAendern(zustand, ereignis)
            is SpielEreignis.FeldAnlagenZustandGeaendert ->
                KartenRegelwerk.anlagenZustandAendern(zustand, ereignis)
            is SpielEreignis.SeewegEingerichtet ->
                KartenRegelwerk.seewegEinrichten(zustand, ereignis)
            is SpielEreignis.SeewegEntfernt ->
                KartenRegelwerk.seewegEntfernen(zustand, ereignis)
            is SpielEreignis.KriegsEinheitEingesetzt ->
                KartenRegelwerk.kriegsEinheitEinsetzen(zustand, ereignis)
            is SpielEreignis.KriegsEinheitEntfernt ->
                KartenRegelwerk.kriegsEinheitEntfernen(zustand, ereignis)
            is SpielEreignis.KriegErklaert ->
                KonfliktRegelwerk.kriegErklaeren(zustand, ereignis)
            is SpielEreignis.KriegBeendet ->
                KonfliktRegelwerk.kriegBeenden(zustand, ereignis)
            is SpielEreignis.Schuldenstrich ->
                InsolvenzRegelwerk.schuldenstrichBuchen(zustand, ereignis)
            is SpielEreignis.RundenwerteAktualisiert -> zustand.copy(
                marktpreise = ereignis.marktpreise,
                leitzins = ereignis.leitzins,
            )
            SpielEreignis.ZugBeendet -> ZugRegelwerk.zugBeenden(zustand)
        }
    }
}
