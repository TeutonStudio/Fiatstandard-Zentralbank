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
            is SpielEreignis.HandelsangebotErstellt ->
                AngebotsRegelwerk.handelsangebotErstellen(zustand, ereignis)
            is SpielEreignis.HandelsangebotAngenommen ->
                AngebotsRegelwerk.handelsangebotAnnehmen(zustand, ereignis)
            is SpielEreignis.HandelsangebotAbgelehnt ->
                AngebotsRegelwerk.handelsangebotAblehnen(zustand, ereignis)
            is SpielEreignis.HandelsangebotZurueckgezogen ->
                AngebotsRegelwerk.handelsangebotZurueckziehen(zustand, ereignis)
            is SpielEreignis.AnleihenangebotErstellt ->
                AngebotsRegelwerk.anleihenangebotErstellen(zustand, ereignis)
            is SpielEreignis.AnleihenangebotAngenommen ->
                AngebotsRegelwerk.anleihenangebotAnnehmen(zustand, ereignis)
            is SpielEreignis.AnleihenangebotAbgelehnt ->
                AngebotsRegelwerk.anleihenangebotAblehnen(zustand, ereignis)
            is SpielEreignis.AnleihenangebotZurueckgezogen ->
                AngebotsRegelwerk.anleihenangebotZurueckziehen(zustand, ereignis)
            is SpielEreignis.AngeboteAbgelaufen ->
                AngebotsRegelwerk.angeboteAblaufen(zustand, ereignis)
            is SpielEreignis.SpielerAusgeschieden ->
                PartieRegelwerk.spielerAusscheiden(zustand, ereignis)
            is SpielEreignis.PartieBeendet -> PartieRegelwerk.partieBeenden(zustand, ereignis)
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
            is SpielEreignis.AnleiheAufgestockt ->
                AnleihenRegelwerk.anleiheAufstocken(zustand, ereignis)
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
            is SpielEreignis.SeewegRouteGeaendert ->
                KartenRegelwerk.seewegRouteAendern(zustand, ereignis)
            is SpielEreignis.KriegsEinheitEingesetzt ->
                KartenRegelwerk.kriegsEinheitEinsetzen(zustand, ereignis)
            is SpielEreignis.KriegsEinheitGebaut ->
                KartenRegelwerk.kriegsEinheitBauen(zustand, ereignis)
            is SpielEreignis.KriegsEinheitBewegt ->
                KartenRegelwerk.kriegsEinheitBewegen(zustand, ereignis)
            is SpielEreignis.KriegsEinheitenBewegt ->
                KartenRegelwerk.kriegsEinheitenBewegen(zustand, ereignis)
            is SpielEreignis.KriegsEinheitEntfernt ->
                KartenRegelwerk.kriegsEinheitEntfernen(zustand, ereignis)
            is SpielEreignis.KampfAufgeloest ->
                KonfliktRegelwerk.kampfAufloesen(zustand, ereignis)
            is SpielEreignis.KriegErklaert ->
                KonfliktRegelwerk.kriegErklaeren(zustand, ereignis)
            is SpielEreignis.KriegsAllianzBeigetreten ->
                KonfliktRegelwerk.allianzBeitreten(zustand, ereignis)
            is SpielEreignis.WaffenstillstandAngeboten ->
                KonfliktRegelwerk.waffenstillstandAnbieten(zustand, ereignis)
            is SpielEreignis.WaffenstillstandGeschlossen ->
                KonfliktRegelwerk.waffenstillstandSchliessen(zustand, ereignis)
            is SpielEreignis.KriegKapituliert ->
                KonfliktRegelwerk.kapitulieren(zustand, ereignis)
            is SpielEreignis.FriedensvertragVorgeschlagen ->
                KonfliktRegelwerk.friedenVorschlagen(zustand, ereignis)
            is SpielEreignis.FriedensvertragAngenommen ->
                KonfliktRegelwerk.friedenAnnehmen(zustand, ereignis)
            is SpielEreignis.FriedensvertragAbgeschlossen ->
                KonfliktRegelwerk.friedenAbschliessen(zustand, ereignis)
            is SpielEreignis.Schuldenstrich ->
                InsolvenzRegelwerk.schuldenstrichBuchen(zustand, ereignis)
            is SpielEreignis.ZentralbankgeldGeschoepft ->
                InsolvenzRegelwerk.zentralbankgeldProtokollieren(zustand, ereignis)
            is SpielEreignis.VerwaltungsruineRepariert ->
                KartenRegelwerk.verwaltungsruineReparieren(zustand, ereignis)
            is SpielEreignis.VerwaltungsruineAbgerissen ->
                KartenRegelwerk.verwaltungsruineAbreissen(zustand, ereignis)
            is SpielEreignis.BelagerungAktualisiert ->
                BelagerungsRegelwerk.aktualisieren(zustand, ereignis)
            is SpielEreignis.RessourcenUebertragen ->
                HandelsRegelwerk.ressourcenUebertragen(zustand, ereignis)
            is SpielEreignis.RundenwerteAktualisiert -> zustand.copy(
                marktpreise = ereignis.marktpreise,
                leitzins = ereignis.leitzins,
            )
            is SpielEreignis.RundeBegonnen -> {
                require(ereignis.runde == zustand.rundenzähler) {
                    "Die Rundenwerte gehören nicht zur laufenden Runde."
                }
                zustand.copy(
                    marktpreise = ereignis.marktpreise,
                    leitzins = ereignis.leitzins,
                    marktpreisBeobachtungen = emptyMap(),
                    rundenwerte = zustand.rundenwerte +
                        de.teutonstudio.zentralbank.fachlogik.modell.Rundenwerte(
                            runde = ereignis.runde,
                            marktpreise = ereignis.marktpreise,
                            leitzins = ereignis.leitzins,
                            preisinflation = ereignis.preisinflation,
                        ),
                )
            }
            SpielEreignis.ZugBeendet -> ZugRegelwerk.zugBeenden(zustand)
        }
    }
}
