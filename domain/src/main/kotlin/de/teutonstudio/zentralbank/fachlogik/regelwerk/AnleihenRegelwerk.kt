package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.auswertung.AnleihenAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

internal object AnleihenRegelwerk {
    fun anleiheKaufen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AnleiheGekauft,
    ): SpielZustand {
        require(ereignis.preis > Geld.NULL) { "Anleihepreis muss positiv sein." }
        require(ereignis.anleihe in zustand.anleihen.keys) {
            "Unbekannte Anleihe: ${ereignis.anleihe.wert}"
        }

        val nachZahlung = FinanzRegelwerk.geldUebertragen(
            zustand = zustand,
            von = KontoId.Spieler(ereignis.kaeufer),
            an = ereignis.verkaeufer,
            betrag = ereignis.preis,
        )
        return anleiheVerschieben(
            zustand = nachZahlung,
            anleihe = ereignis.anleihe,
            von = ereignis.verkaeufer,
            an = KontoId.Spieler(ereignis.kaeufer),
        )
    }

    fun anleiheVerkaufen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AnleiheVerkauft,
    ): SpielZustand {
        require(ereignis.preis > Geld.NULL) { "Anleihepreis muss positiv sein." }
        require(ereignis.anleihe in zustand.anleihen.keys) {
            "Unbekannte Anleihe: ${ereignis.anleihe.wert}"
        }

        val nachZahlung = FinanzRegelwerk.geldUebertragen(
            zustand = zustand,
            von = ereignis.kaeufer,
            an = KontoId.Spieler(ereignis.verkaeufer),
            betrag = ereignis.preis,
        )
        return anleiheVerschieben(
            zustand = nachZahlung,
            anleihe = ereignis.anleihe,
            von = KontoId.Spieler(ereignis.verkaeufer),
            an = ereignis.kaeufer,
        )
    }

    fun anleiheFaelligStellen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AnleiheFaellig,
    ): SpielZustand {
        val anleihe = zustand.anleihen[ereignis.anleihe]
            ?: error("Unbekannte Anleihe: ${ereignis.anleihe.wert}")
        val besitzer = AnleihenAuswertung.besitzer(zustand, ereignis.anleihe)
            ?: error("Anleihe ${ereignis.anleihe.wert} hat keinen Besitzer.")

        val nachZahlung = FinanzRegelwerk.geldUebertragen(
            zustand = zustand,
            von = KontoId.Spieler(anleihe.emittent),
            an = besitzer,
            betrag = anleihe.nennwert,
        )
        val ohneBesitzer = anleiheEntfernen(nachZahlung, ereignis.anleihe)
        return ohneBesitzer.copy(anleihen = ohneBesitzer.anleihen - ereignis.anleihe)
    }

    private fun anleiheVerschieben(
        zustand: SpielZustand,
        anleihe: AnleiheId,
        von: KontoId,
        an: KontoId,
    ): SpielZustand {
        require(von != an) { "Anleihe-Sender und Empfaenger muessen verschieden sein." }
        val ohneAbsender = anleiheVonKontoEntfernen(zustand, anleihe, von)
        return anleiheZuKontoHinzufuegen(ohneAbsender, anleihe, an)
    }

    private fun anleiheVonKontoEntfernen(
        zustand: SpielZustand,
        anleihe: AnleiheId,
        konto: KontoId,
    ): SpielZustand = when (konto) {
        KontoId.Bank -> {
            require(anleihe in zustand.bankAnleihen) {
                "Bank besitzt Anleihe ${anleihe.wert} nicht."
            }
            zustand.copy(bankAnleihen = zustand.bankAnleihen - anleihe)
        }
        is KontoId.Spieler -> SpielerRegelwerk.aendereSpieler(zustand, konto.id) { spieler ->
            require(anleihe in spieler.anleihen) {
                "${spieler.name} besitzt Anleihe ${anleihe.wert} nicht."
            }
            spieler.copy(anleihen = spieler.anleihen - anleihe)
        }
    }

    private fun anleiheZuKontoHinzufuegen(
        zustand: SpielZustand,
        anleihe: AnleiheId,
        konto: KontoId,
    ): SpielZustand {
        require(AnleihenAuswertung.besitzer(zustand, anleihe) == null) {
            "Anleihe ${anleihe.wert} hat bereits einen Besitzer."
        }
        return when (konto) {
            KontoId.Bank -> zustand.copy(bankAnleihen = zustand.bankAnleihen + anleihe)
            is KontoId.Spieler -> SpielerRegelwerk.aendereSpieler(zustand, konto.id) { spieler ->
                spieler.copy(anleihen = spieler.anleihen + anleihe)
            }
        }
    }

    fun anleiheEntfernen(
        zustand: SpielZustand,
        anleihe: AnleiheId,
    ): SpielZustand = zustand.copy(
        bankAnleihen = zustand.bankAnleihen - anleihe,
        spieler = zustand.spieler.map { spieler ->
            spieler.copy(anleihen = spieler.anleihen - anleihe)
        },
    )
}
