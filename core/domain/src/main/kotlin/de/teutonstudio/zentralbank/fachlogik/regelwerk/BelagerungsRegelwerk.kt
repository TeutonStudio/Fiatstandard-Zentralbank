package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.auswertung.ErreichbarkeitsAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Belagerung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

internal object BelagerungsRegelwerk {
    fun aktualisieren(
        zustand: SpielZustand,
        ereignis: SpielEreignis.BelagerungAktualisiert,
    ): SpielZustand {
        val karte = requireNotNull(zustand.karte) { "Eine Belagerung benötigt eine Karte." }
        val standort = karte.belegung.eckenNachPosition[ereignis.standort]
            ?: return zustand.copy(
                belagerungen = zustand.belagerungen.filterNot { it.standort == ereignis.standort },
            )
        val verteidiger = standort.besitzer ?: return zustand
        val beteiligte = ErreichbarkeitsAuswertung.blockierendePanzerNachSpieler(
            karte,
            standort.position,
            verteidiger,
            zustand.konflikte,
        )
        val bisher = zustand.belagerungen.firstOrNull { it.standort == standort.position }
        if (beteiligte.isEmpty()) {
            return zustand.copy(
                karte = if (standort.zustand == BauwerkZustand.BELAGERT) karte.copy(
                    belegung = karte.belegung.copy(
                        ecken = karte.belegung.ecken.map {
                            if (it.position == standort.position) it.copy(zustand = BauwerkZustand.INTAKT)
                            else it
                        },
                    ),
                ) else karte,
                belagerungen = zustand.belagerungen.filterNot { it.standort == standort.position },
            )
        }
        val max = beteiligte.values.max()
        val gleichauf = beteiligte.filterValues { it == max }.keys
        val fuehrer = bisher?.fuehrenderBelagerer?.takeIf { it in gleichauf }
            ?: gleichauf.minBy { it.wert }
        // Der Belagerungsbestand ist der theoretische reguläre Ertrag. Deshalb werden für
        // diese eine Berechnung die Blockade und der technische BELAGERT-Zustand ausgeblendet;
        // tatsächlich ausgezahlt wird währenddessen ausschließlich an den Belagerungsbestand.
        val theoretischeKarte = karte.copy(
            belegung = karte.belegung.copy(
                ecken = karte.belegung.ecken.map {
                    if (it.position == standort.position) it.copy(zustand = BauwerkZustand.INTAKT)
                    else it
                },
            ),
        )
        val ertrag = KartenAuswertung.abbauErtragNachVerwaltungsstandort(
            theoretischeKarte,
            verteidiger,
            emptySet(),
        )[standort.position].orEmpty()
        val gespeichert = (bisher?.gespeicherterErtrag.orEmpty().keys + ertrag.keys).associateWith {
            bisher?.gespeicherterErtrag.orEmpty().getOrDefault(it, 0) +
                if (ereignis.rundeFortschreiben) ertrag.getOrDefault(it, 0) else 0
        }.filterValues { it > 0 }
        val fortschritt = (bisher?.fortschrittRunden ?: 0) +
            if (ereignis.rundeFortschreiben) 1 else 0
        val erforderlich = erforderlicheRunden(standort.typ)
        if (fortschritt < erforderlich) {
            val belagerung = Belagerung(
                standort = standort.position,
                verteidiger = verteidiger,
                beteiligteBelagerer = beteiligte,
                begonnenInRunde = bisher?.begonnenInRunde ?: zustand.rundenzähler,
                fortschrittRunden = fortschritt,
                fuehrenderBelagerer = fuehrer,
                fuehrungsBeginnRunde = if (bisher?.fuehrenderBelagerer == fuehrer) {
                    bisher.fuehrungsBeginnRunde
                } else zustand.rundenzähler,
                gespeicherterErtrag = gespeichert,
            )
            return zustand.copy(
                karte = karte.copy(
                    belegung = karte.belegung.copy(
                        ecken = karte.belegung.ecken.map {
                            if (it.position == standort.position) it.copy(zustand = BauwerkZustand.BELAGERT)
                            else it
                        },
                    ),
                ),
                belagerungen = zustand.belagerungen.filterNot { it.standort == standort.position } +
                    belagerung,
            )
        }
        val ruinenKarte = karte.copy(
            belegung = karte.belegung.copy(
                ecken = karte.belegung.ecken.map {
                    if (it.position == standort.position) it.copy(
                        zustand = BauwerkZustand.ZERSTOERT,
                        besitzer = null,
                    ) else it
                },
            ),
        )
        val mitRuine = zustand.copy(
            karte = ruinenKarte,
            belagerungen = zustand.belagerungen.filterNot { it.standort == standort.position },
        )
        return if (gespeichert.isEmpty()) mitRuine else RohstoffRegelwerk.rohstoffeBuchen(
            mitRuine,
            fuehrer,
            gespeichert,
            1,
        )
    }

    fun erforderlicheRunden(typ: EckGebaeudeTyp): Int = when (typ) {
        EckGebaeudeTyp.BAHNHOF, EckGebaeudeTyp.HAFEN -> 3
        EckGebaeudeTyp.GROSSBAHNHOF, EckGebaeudeTyp.GROSSHAFEN -> 5
        EckGebaeudeTyp.HAUPTBAHNHOF -> 7
    }
}
