package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.ereignis.TransaktionsGrund
import de.teutonstudio.zentralbank.fachlogik.auswertung.ZugAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus

internal object ZugRegelwerk {
    fun pruefeFreigabe(
        zustand: SpielZustand,
        ereignis: SpielEreignis,
    ) {
        if (ereignis is SpielEreignis.WarenkorbGeaendert) return
        if (
            zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL &&
            ereignis.istRundeNullPlatzierung()
        ) return

        val zug = zustand.zugStatus ?: return
        val faelligerSchuldenstrich =
            InsolvenzRegelwerk.faelligerSchuldenstrichSpieler(zustand)
        if (faelligerSchuldenstrich != null) {
            require(
                ereignis is SpielEreignis.Schuldenstrich &&
                    ereignis.spieler == faelligerSchuldenstrich
            ) {
                "Zuerst muss der faellige Schuldenstrich fuer " +
                    "${faelligerSchuldenstrich.wert} gebucht werden."
            }
            return
        }

        val schritt = schrittTyp(ereignis) ?: return
        val schrittInfo = ZugAuswertung.schritte(zustand).first { info -> info.typ == schritt }
        require(schrittInfo.zustand == SchrittZustand.VERFUEGBAR) {
            schrittInfo.begruendung ?: "Schritt $schritt ist nicht verfuegbar."
        }
        primaererSpieler(ereignis)?.let { spieler ->
            require(spieler == zug.spieler) {
                "Nur der aktive Spieler ${zug.spieler.wert} darf diesen Schritt ausfuehren."
            }
        }
    }

    private fun SpielEreignis.istRundeNullPlatzierung(): Boolean =
        this is SpielEreignis.HauptbahnhofPlatziert ||
            this is SpielEreignis.EckGebaeudeGebaut ||
            this is SpielEreignis.SchieneGebaut ||
            this is SpielEreignis.NeutraleAnlageErrichtet

    fun schrittAbschliessen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.SchrittAbgeschlossen,
    ): SpielZustand {
        val zug = zustand.zugStatus ?: error("Es ist kein Zug aktiv.")
        val schrittInfo = ZugAuswertung.schritte(zustand)
            .first { info -> info.typ == ereignis.schritt }
        require(
            schrittInfo.zustand == SchrittZustand.VERFUEGBAR ||
                schrittInfo.zustand == SchrittZustand.ERLEDIGT
        ) {
            schrittInfo.begruendung ?: "Schritt ist nicht verfuegbar."
        }
        return zustand.copy(
            zugStatus = zug.copy(erledigteSchritte = zug.erledigteSchritte + ereignis.schritt),
        )
    }

    fun phaseAbschliessen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.PhaseAbgeschlossen,
    ): SpielZustand {
        val zug = zustand.zugStatus ?: error("Es ist kein Zug aktiv.")
        require(zug.phase == ereignis.phase) {
            "Falsche Phase: aktueller Zug ist in ${zug.phase}."
        }
        require(ZugAuswertung.kannPhaseAbschliessen(zustand)) {
            "Nicht alle Pflichtschritte der Phase sind erledigt."
        }
        val naechstePhase = ZugAuswertung.naechstePhase(zug.phase)
            ?: error("Aktions-Phase wird mit ZugBeendet abgeschlossen.")
        return zustand.copy(zugStatus = ZugStatus(zug.spieler, naechstePhase))
    }

    fun zugBeenden(zustand: SpielZustand): SpielZustand {
        val zug = zustand.zugStatus ?: error("Es ist kein Zug aktiv.")
        require(ZugAuswertung.kannZugBeenden(zustand)) {
            "Zug kann erst in der Aktions-Phase beendet werden."
        }
        val nachPruefung = InsolvenzRegelwerk.ueberschuldungAktualisieren(
            zustand = zustand,
            spieler = zug.spieler,
        )
        return if (InsolvenzRegelwerk.istSchuldenstrichFaellig(nachPruefung, zug.spieler)) {
            nachPruefung
        } else {
            naechsterZug(nachPruefung, zug.spieler)
        }
    }

    fun naechsterZug(
        zustand: SpielZustand,
        aktuellerSpieler: SpielerId,
    ): SpielZustand {
        val aktuellerIndex = zustand.spieler.indexOfFirst { spieler ->
            spieler.id == aktuellerSpieler
        }
        require(aktuellerIndex >= 0) { "Aktiver Spieler ist unbekannt." }
        val naechsterSpieler = zustand.spieler[(aktuellerIndex + 1) % zustand.spieler.size]
        val neueRunde = if (aktuellerIndex == zustand.spieler.lastIndex) {
            zustand.rundenzähler + 1
        } else {
            zustand.rundenzähler
        }
        return zustand.copy(
            aktiverSpieler = naechsterSpieler.id,
            rundenzähler = neueRunde,
            zugStatus = ZugStatus(naechsterSpieler.id, Phase.Einnahmen),
        )
    }

    private fun schrittTyp(ereignis: SpielEreignis): SchrittTyp? = when (ereignis) {
        is SpielEreignis.WarenkorbGeaendert -> null
        is SpielEreignis.RohstoffEinnahme -> SchrittTyp.ROHSTOFF_EINNAHMEN
        is SpielEreignis.RohstoffAusgabe -> SchrittTyp.ROHSTOFF_AUSGABEN
        is SpielEreignis.Transaktion -> when (ereignis.grund) {
            TransaktionsGrund.ROHSTOFFHANDEL -> SchrittTyp.ROHSTOFF_HANDEL
            TransaktionsGrund.ANLEIHENHANDEL -> SchrittTyp.ANLEIHEN_HANDEL
            else -> SchrittTyp.FINANZ_AUSGABEN
        }
        is SpielEreignis.AnleiheGekauft,
        is SpielEreignis.AnleiheVerkauft,
        is SpielEreignis.AnleiheFaellig -> SchrittTyp.ANLEIHEN_HANDEL
        is SpielEreignis.RohstoffHandel -> SchrittTyp.ROHSTOFF_HANDEL
        is SpielEreignis.Expansion -> SchrittTyp.EXPANSION
        is SpielEreignis.HauptbahnhofPlatziert -> null
        is SpielEreignis.EckGebaeudeGebaut,
        is SpielEreignis.EckGebaeudeAufgewertet,
        is SpielEreignis.SchieneGebaut,
        is SpielEreignis.NeutraleAnlageErrichtet,
        is SpielEreignis.KartenBelegungEntfernt,
        is SpielEreignis.KartenBauwerkZustandGeaendert,
        is SpielEreignis.FeldAnlagenZustandGeaendert -> SchrittTyp.EXPANSION
        is SpielEreignis.SeewegEingerichtet,
        is SpielEreignis.SeewegEntfernt -> SchrittTyp.EXPANSION
        is SpielEreignis.KriegsEinheitEingesetzt,
        is SpielEreignis.KriegsEinheitEntfernt -> SchrittTyp.KRIEG
        is SpielEreignis.KriegErklaert,
        is SpielEreignis.KriegBeendet -> SchrittTyp.KRIEG
        is SpielEreignis.Schuldenstrich -> SchrittTyp.FINANZ_AUSGABEN
        is SpielEreignis.SchrittAbgeschlossen,
        is SpielEreignis.PhaseAbgeschlossen,
        SpielEreignis.ZugBeendet -> null
    }

    private fun primaererSpieler(ereignis: SpielEreignis): SpielerId? = when (ereignis) {
        is SpielEreignis.WarenkorbGeaendert -> null
        is SpielEreignis.RohstoffEinnahme -> ereignis.spieler
        is SpielEreignis.RohstoffAusgabe -> ereignis.spieler
        is SpielEreignis.Transaktion -> when (val von = ereignis.von) {
            is KontoId.Spieler -> von.id
            KontoId.Bank -> (ereignis.an as? KontoId.Spieler)?.id
        }
        is SpielEreignis.AnleiheGekauft -> ereignis.kaeufer
        is SpielEreignis.AnleiheVerkauft -> ereignis.verkaeufer
        is SpielEreignis.RohstoffHandel -> ereignis.kaeufer
        is SpielEreignis.Expansion -> ereignis.spieler
        is SpielEreignis.HauptbahnhofPlatziert -> ereignis.spieler
        is SpielEreignis.EckGebaeudeGebaut -> ereignis.spieler
        is SpielEreignis.EckGebaeudeAufgewertet -> ereignis.spieler
        is SpielEreignis.SchieneGebaut -> ereignis.spieler
        is SpielEreignis.NeutraleAnlageErrichtet -> ereignis.errichter
        is SpielEreignis.KartenBelegungEntfernt -> ereignis.spieler
        is SpielEreignis.KartenBauwerkZustandGeaendert -> ereignis.spieler
        is SpielEreignis.FeldAnlagenZustandGeaendert -> ereignis.spieler
        is SpielEreignis.SeewegEingerichtet -> ereignis.spieler
        is SpielEreignis.SeewegEntfernt -> ereignis.spieler
        is SpielEreignis.KriegsEinheitEingesetzt -> ereignis.spieler
        is SpielEreignis.KriegsEinheitEntfernt -> ereignis.spieler
        is SpielEreignis.KriegErklaert -> ereignis.aggressor
        is SpielEreignis.KriegBeendet -> ereignis.spielerA
        is SpielEreignis.Schuldenstrich -> ereignis.spieler
        is SpielEreignis.AnleiheFaellig,
        is SpielEreignis.SchrittAbgeschlossen,
        is SpielEreignis.PhaseAbgeschlossen,
        SpielEreignis.ZugBeendet -> null
    }
}
