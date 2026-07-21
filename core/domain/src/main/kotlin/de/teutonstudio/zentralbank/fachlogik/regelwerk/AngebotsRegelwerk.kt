package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.AnleihenAngebot
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebot
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotStatus
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

internal object AngebotsRegelwerk {
    fun handelsangebotErstellen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.HandelsangebotErstellt,
    ): SpielZustand {
        val angebot = ereignis.angebot
        pruefeNeuesAngebot(zustand, angebot.anbieter, angebot.empfaenger)
        require(angebot.id.wert == zustand.naechsteAngebotsNummer) {
            "Die Handelsangebot-ID wurde nicht vom Spielkern vergeben."
        }
        require(angebot.angeboteneRohstoffe.values.all { it > 0 }) {
            "Angebotene Rohstoffmengen müssen positiv sein."
        }
        require(angebot.geforderteRohstoffe.values.all { it > 0 }) {
            "Geforderte Rohstoffmengen müssen positiv sein."
        }
        require(angebot.angebotenerGeldbetrag >= Geld.NULL && angebot.geforderterGeldbetrag >= Geld.NULL) {
            "Geldbeträge eines Angebots dürfen nicht negativ sein."
        }
        require(
            angebot.angeboteneRohstoffe.isNotEmpty() || angebot.angebotenerGeldbetrag > Geld.NULL,
        ) { "Ein Handelsangebot muss eine Leistung anbieten." }
        require(
            angebot.geforderteRohstoffe.isNotEmpty() || angebot.geforderterGeldbetrag > Geld.NULL,
        ) { "Ein Handelsangebot muss eine Gegenleistung fordern." }
        return zustand.copy(
            handelsAngebote = (zustand.handelsAngebote + angebot).sortedBy { it.id.wert },
            naechsteAngebotsNummer = zustand.naechsteAngebotsNummer + 1L,
        )
    }

    fun handelsangebotAnnehmen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.HandelsangebotAngenommen,
    ): SpielZustand {
        val angebot = zustand.handelsAngebote.firstOrNull { it.id == ereignis.angebot }
            ?: error("Das Handelsangebot existiert nicht.")
        pruefeAntwort(angebot, ereignis.angenommenVon)
        var danach = rohstoffeUebertragen(
            zustand,
            angebot.anbieter,
            ereignis.angenommenVon,
            angebot.angeboteneRohstoffe,
        )
        danach = rohstoffeUebertragen(
            danach,
            ereignis.angenommenVon,
            angebot.anbieter,
            angebot.geforderteRohstoffe,
        )
        if (angebot.angebotenerGeldbetrag > Geld.NULL) {
            danach = FinanzRegelwerk.geldUebertragen(
                danach,
                KontoId.Spieler(angebot.anbieter),
                KontoId.Spieler(ereignis.angenommenVon),
                angebot.angebotenerGeldbetrag,
            )
        }
        if (angebot.geforderterGeldbetrag > Geld.NULL) {
            danach = FinanzRegelwerk.geldUebertragen(
                danach,
                KontoId.Spieler(ereignis.angenommenVon),
                KontoId.Spieler(angebot.anbieter),
                angebot.geforderterGeldbetrag,
            )
        }
        return danach.handelsstatusAendern(angebot, HandelsAngebotStatus.ANGENOMMEN)
    }

    fun handelsangebotAblehnen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.HandelsangebotAbgelehnt,
    ): SpielZustand {
        val angebot = zustand.handelsAngebote.firstOrNull { it.id == ereignis.angebot }
            ?: error("Das Handelsangebot existiert nicht.")
        pruefeAntwort(angebot, ereignis.abgelehntVon)
        return zustand.handelsstatusAendern(angebot, HandelsAngebotStatus.ABGELEHNT)
    }

    fun handelsangebotZurueckziehen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.HandelsangebotZurueckgezogen,
    ): SpielZustand {
        val angebot = zustand.handelsAngebote.firstOrNull { it.id == ereignis.angebot }
            ?: error("Das Handelsangebot existiert nicht.")
        require(angebot.status == HandelsAngebotStatus.OFFEN) { "Das Angebot ist nicht mehr offen." }
        require(angebot.anbieter == ereignis.spieler) { "Nur der Anbieter darf zurückziehen." }
        return zustand.handelsstatusAendern(angebot, HandelsAngebotStatus.ZURUECKGEZOGEN)
    }

    fun anleihenangebotErstellen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AnleihenangebotErstellt,
    ): SpielZustand {
        val angebot = ereignis.angebot
        pruefeNeuesAngebot(zustand, angebot.anbieter, angebot.empfaenger)
        require(angebot.id.wert == zustand.naechsteAngebotsNummer) {
            "Die Anleihenangebot-ID wurde nicht vom Spielkern vergeben."
        }
        require(angebot.preis > Geld.NULL) { "Der Anleihepreis muss positiv sein." }
        require(angebot.anleihe in zustand.spieler.getValue(angebot.anbieter).anleihen) {
            "Der Anbieter besitzt die angebotene Anleihe nicht."
        }
        return zustand.copy(
            anleihenAngebote = (zustand.anleihenAngebote + angebot).sortedBy { it.id.wert },
            naechsteAngebotsNummer = zustand.naechsteAngebotsNummer + 1L,
        )
    }

    fun anleihenangebotAnnehmen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AnleihenangebotAngenommen,
    ): SpielZustand {
        val angebot = zustand.anleihenAngebote.firstOrNull { it.id == ereignis.angebot }
            ?: error("Das Anleihenangebot existiert nicht.")
        pruefeAntwort(angebot, ereignis.angenommenVon)
        val danach = AnleihenRegelwerk.anleiheVerkaufen(
            zustand,
            SpielEreignis.AnleiheVerkauft(
                anleihe = angebot.anleihe,
                verkaeufer = angebot.anbieter,
                kaeufer = KontoId.Spieler(ereignis.angenommenVon),
                preis = angebot.preis,
            ),
        )
        return danach.anleihenstatusAendern(angebot, HandelsAngebotStatus.ANGENOMMEN)
    }

    fun anleihenangebotAblehnen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AnleihenangebotAbgelehnt,
    ): SpielZustand {
        val angebot = zustand.anleihenAngebote.firstOrNull { it.id == ereignis.angebot }
            ?: error("Das Anleihenangebot existiert nicht.")
        pruefeAntwort(angebot, ereignis.abgelehntVon)
        return zustand.anleihenstatusAendern(angebot, HandelsAngebotStatus.ABGELEHNT)
    }

    fun anleihenangebotZurueckziehen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AnleihenangebotZurueckgezogen,
    ): SpielZustand {
        val angebot = zustand.anleihenAngebote.firstOrNull { it.id == ereignis.angebot }
            ?: error("Das Anleihenangebot existiert nicht.")
        require(angebot.status == HandelsAngebotStatus.OFFEN) { "Das Angebot ist nicht mehr offen." }
        require(angebot.anbieter == ereignis.spieler) { "Nur der Anbieter darf zurückziehen." }
        return zustand.anleihenstatusAendern(angebot, HandelsAngebotStatus.ZURUECKGEZOGEN)
    }

    fun angeboteAblaufen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AngeboteAbgelaufen,
    ): SpielZustand = zustand.copy(
        handelsAngebote = zustand.handelsAngebote.map { angebot ->
            if (angebot.id in ereignis.handelsangebote && angebot.status == HandelsAngebotStatus.OFFEN) {
                angebot.copy(status = HandelsAngebotStatus.ABGELAUFEN)
            } else angebot
        },
        anleihenAngebote = zustand.anleihenAngebote.map { angebot ->
            if (angebot.id in ereignis.anleihenangebote && angebot.status == HandelsAngebotStatus.OFFEN) {
                angebot.copy(status = HandelsAngebotStatus.ABGELAUFEN)
            } else angebot
        },
    )

    private fun pruefeNeuesAngebot(
        zustand: SpielZustand,
        anbieter: SpielerId,
        empfaenger: SpielerId?,
    ) {
        require(anbieter in zustand.spieler.map { it.id }) { "Unbekannter Anbieter." }
        require(empfaenger == null || empfaenger in zustand.spieler.map { it.id }) {
            "Unbekannter Empfänger."
        }
        require(empfaenger != anbieter) { "Ein Angebot kann nicht an den Anbieter selbst gehen." }
    }

    private fun pruefeAntwort(angebot: HandelsAngebot, spieler: SpielerId) {
        require(angebot.status == HandelsAngebotStatus.OFFEN) { "Das Angebot ist nicht mehr offen." }
        require(spieler != angebot.anbieter) { "Der Anbieter kann sein eigenes Angebot nicht annehmen." }
        require(angebot.empfaenger == null || angebot.empfaenger == spieler) {
            "Das Angebot richtet sich an einen anderen Spieler."
        }
    }

    private fun pruefeAntwort(angebot: AnleihenAngebot, spieler: SpielerId) {
        require(angebot.status == HandelsAngebotStatus.OFFEN) { "Das Angebot ist nicht mehr offen." }
        require(spieler != angebot.anbieter) { "Der Anbieter kann sein eigenes Angebot nicht annehmen." }
        require(angebot.empfaenger == null || angebot.empfaenger == spieler) {
            "Das Angebot richtet sich an einen anderen Spieler."
        }
    }

    private fun rohstoffeUebertragen(
        zustand: SpielZustand,
        von: SpielerId,
        an: SpielerId,
        mengen: Map<de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff, Int>,
    ): SpielZustand {
        if (mengen.isEmpty()) return zustand
        val nachAbgabe = RohstoffRegelwerk.rohstoffeBuchen(zustand, von, mengen, -1)
        return RohstoffRegelwerk.rohstoffeBuchen(nachAbgabe, an, mengen, 1)
    }

    private fun SpielZustand.handelsstatusAendern(
        angebot: HandelsAngebot,
        status: HandelsAngebotStatus,
    ): SpielZustand = copy(
        handelsAngebote = handelsAngebote.map { kandidat ->
            if (kandidat.id == angebot.id) kandidat.copy(status = status) else kandidat
        },
    )

    private fun SpielZustand.anleihenstatusAendern(
        angebot: AnleihenAngebot,
        status: HandelsAngebotStatus,
    ): SpielZustand = copy(
        anleihenAngebote = anleihenAngebote.map { kandidat ->
            if (kandidat.id == angebot.id) kandidat.copy(status = status) else kandidat
        },
    )

    private fun List<de.teutonstudio.zentralbank.fachlogik.modell.Spieler>.getValue(
        id: SpielerId,
    ) = firstOrNull { it.id == id } ?: error("Unbekannter Spieler: ${id.wert}.")
}
