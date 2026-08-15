package de.teutonstudio.zentralbank.netzwerk

import de.teutonstudio.zentralbank.anwendung.AKTUELLE_ENGINE_VERSION
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.anwendung.SpielDienst
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.BeobachtungsAuswertung
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil
import de.teutonstudio.zentralbank.fachlogik.modell.pruefePasswort
import de.teutonstudio.zentralbank.protokoll.API_VERSION
import de.teutonstudio.zentralbank.protokoll.AktionAusfuehrenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.AktionErgebnisDto
import de.teutonstudio.zentralbank.protokoll.ErlaubteAktionenDto
import de.teutonstudio.zentralbank.protokoll.SpielBeitretenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.SpielBeobachtungAntwortDto
import de.teutonstudio.zentralbank.protokoll.SpielErstellenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.SpielErstelltDto
import de.teutonstudio.zentralbank.protokoll.SpielLadenAntwortDto
import de.teutonstudio.zentralbank.protokoll.SpielSitzungDto
import de.teutonstudio.zentralbank.protokoll.zuDomain
import de.teutonstudio.zentralbank.protokoll.zuDto
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Transportneutrale, serverautoritative Mehrspieler-Fassade.
 *
 * Eine Sitzung bindet genau einen Spieler an ein Spiel. Netzwerkclients senden nur
 * [SpielAktion]-Absichten; die gemeinsame Engine auf dem Host validiert und verändert den Zustand.
 */
class SpielNetzwerkDienst(
    private val ablage: SpielAblage,
    engine: SpielEngine = StandardSpielEngine(),
    ersteSpielId: Long = 1,
) {
    private data class Sitzung(val spielId: Long, val spieler: SpielerId)

    private val spielDienst = SpielDienst(ablage, engine)
    private val naechsteId = AtomicLong(ersteSpielId)
    private val sitzungen = ConcurrentHashMap<String, Sitzung>()
    private val spielSperren = ConcurrentHashMap<Long, Mutex>()
    private val beantworteteKommandos = ConcurrentHashMap<String, AktionErgebnisDto>()
    private val zufall = SecureRandom()

    suspend fun erstellen(anfrage: SpielErstellenAnfrageDto): SpielErstelltDto {
        pruefeVersion(anfrage.version)
        val namen = anfrage.spielerNamen.map(String::trim)
        require(namen.isNotEmpty()) { "Mindestens ein Spieler ist erforderlich." }
        require(namen.all(String::isNotBlank)) { "Spielernamen dürfen nicht leer sein." }
        require(namen.distinct().size == namen.size) { "Spielernamen müssen eindeutig sein." }
        require(anfrage.spielstile.isEmpty() || anfrage.spielstile.size == namen.size) {
            "Spielstile müssen entweder leer sein oder für jeden Spieler angegeben werden."
        }
        val stile = if (anfrage.spielstile.isEmpty()) {
            List(namen.size) { SpielerStil.VORSICHTIG }
        } else {
            anfrage.spielstile.map { SpielerStil.valueOf(it) }
        }
        val id = freieId()
        val startzustand = SpielZustand(
            spieler = namen.mapIndexed { index, name ->
                Spieler(id = SpielerId(name), name = name, spielstil = stile[index])
            },
        )
        val gespeichert = spielDienst.spielErstellen(id, startzustand, anfrage.seed)
        return SpielErstelltDto(
            spielId = gespeichert.id.toString(),
            zustand = gespeichert.aktuellerZustand().zuDto(),
            revision = gespeichert.ereignisse.size.toLong(),
        )
    }

    suspend fun laden(id: Long): SpielLadenAntwortDto {
        val gespeichert = spielDienst.spielLaden(id) ?: throw NetzwerkSpielNichtGefunden(id)
        return SpielLadenAntwortDto(
            spielId = id.toString(),
            engineVersion = gespeichert.engineVersion.ifBlank { AKTUELLE_ENGINE_VERSION },
            zustand = gespeichert.aktuellerZustand().zuDto(),
        )
    }

    suspend fun beitreten(id: Long, anfrage: SpielBeitretenAnfrageDto): SpielSitzungDto {
        pruefeVersion(anfrage.version)
        val name = anfrage.spielerName.trim()
        require(name.isNotBlank()) { "Spielername darf nicht leer sein." }
        val gespeichert = spielDienst.spielLaden(id) ?: throw NetzwerkSpielNichtGefunden(id)
        val spieler = gespeichert.aktuellerZustand().spieler.singleOrNull {
            it.name == name || it.id.wert == name
        } ?: throw IllegalArgumentException("Spieler '$name' gehört nicht zu Spiel $id.")
        if (spieler.passwortHash.isNotBlank() && !spieler.pruefePasswort(anfrage.passwort)) {
            throw UngueltigeSpielerAnmeldung()
        }
        val token = neuesToken()
        sitzungen[token] = Sitzung(id, spieler.id)
        return SpielSitzungDto(
            spielId = id.toString(),
            spielerId = spieler.id.wert,
            sessionToken = token,
            revision = gespeichert.ereignisse.size.toLong(),
        )
    }

    suspend fun erlaubteAktionen(id: Long, sessionToken: String): ErlaubteAktionenDto {
        val sitzung = pruefeSitzung(id, sessionToken)
        val gespeichert = spielDienst.spielLaden(id) ?: throw NetzwerkSpielNichtGefunden(id)
        return ErlaubteAktionenDto(
            spielId = id.toString(),
            spieler = sitzung.spieler.wert,
            aktionen = spielDienst.erlaubteAktionen(id, sitzung.spieler).map { it.zuDto() },
            revision = gespeichert.ereignisse.size.toLong(),
        )
    }

    suspend fun beobachten(id: Long, sessionToken: String): SpielBeobachtungAntwortDto {
        val sitzung = pruefeSitzung(id, sessionToken)
        val gespeichert = spielDienst.spielLaden(id) ?: throw NetzwerkSpielNichtGefunden(id)
        val zustand = gespeichert.aktuellerZustand()
        require(zustand.spieler.any { it.id == sitzung.spieler }) {
            "Der Spieler der Sitzung ist nicht mehr Teil des Spiels."
        }
        return SpielBeobachtungAntwortDto(
            spielId = id.toString(),
            spieler = sitzung.spieler.wert,
            revision = gespeichert.ereignisse.size.toLong(),
            beobachtung = BeobachtungsAuswertung.fuerSpieler(zustand, sitzung.spieler),
        )
    }

    suspend fun aktionAusfuehren(
        id: Long,
        sessionToken: String,
        anfrage: AktionAusfuehrenAnfrageDto,
    ): AktionErgebnisDto {
        pruefeVersion(anfrage.version)
        val sitzung = pruefeSitzung(id, sessionToken)
        val kommandoSchluessel = anfrage.commandId
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { "$sessionToken:$it" }
        kommandoSchluessel?.let { beantworteteKommandos[it] }?.let { return it }

        return spielSperren.computeIfAbsent(id) { Mutex() }.withLock {
            kommandoSchluessel?.let { beantworteteKommandos[it] }?.let { return@withLock it }
            val gespeichert = spielDienst.spielLaden(id) ?: throw NetzwerkSpielNichtGefunden(id)
            val revision = gespeichert.ereignisse.size.toLong()
            anfrage.expectedRevision?.let { erwartet ->
                if (erwartet != revision) throw RevisionKonflikt(erwartet, revision)
            }

            val zustand = gespeichert.aktuellerZustand()
            val aktion = anfrage.aktion.zuDomain()
            pruefeAkteur(zustand, aktion, sitzung.spieler)
            val schritt = spielDienst.aktionAusfuehren(id, aktion).getOrThrow()
            val neueRevision = spielDienst.spielLaden(id)
                ?.ereignisse
                ?.size
                ?.toLong()
                ?: throw NetzwerkSpielNichtGefunden(id)
            val antwort = AktionErgebnisDto(
                spielId = id.toString(),
                zustand = schritt.zustand.zuDto(),
                ereignisse = schritt.ereignisse.map { it.zuDto() },
                revision = neueRevision,
                commandId = anfrage.commandId.trim().takeIf(String::isNotEmpty),
            )
            kommandoSchluessel?.let { beantworteteKommandos[it] = antwort }
            antwort
        }
    }

    private fun pruefeAkteur(zustand: SpielZustand, aktion: SpielAktion, sitzungsSpieler: SpielerId) {
        val akteur = when (aktion) {
            is SpielAktion.HauptbahnhofPlatzieren -> aktion.spieler
            is SpielAktion.EckGebaeudeBauen -> aktion.spieler
            is SpielAktion.EckGebaeudeAufwerten -> aktion.spieler
            is SpielAktion.SchieneBauen -> aktion.spieler
            is SpielAktion.AnlageErrichten -> aktion.spieler
            is SpielAktion.BelegungAbreissen -> aktion.spieler
            is SpielAktion.SeewegEinrichten -> aktion.spieler
            is SpielAktion.SeewegEntfernen -> aktion.spieler
            is SpielAktion.KriegsEinheitBauen -> aktion.spieler
            is SpielAktion.KriegsEinheitEinsetzen -> aktion.spieler
            is SpielAktion.KriegsEinheitBewegen -> aktion.spieler
            is SpielAktion.KriegsEinheitenBewegen -> aktion.spieler
            is SpielAktion.VerwaltungsruineReparieren -> aktion.spieler
            is SpielAktion.VerwaltungsruineAbreissen -> aktion.spieler
            is SpielAktion.AnleiheEmittieren -> aktion.spieler
            is SpielAktion.AnleiheFreiwilligZurueckkaufen -> aktion.spieler
            is SpielAktion.AnleiheAufstocken -> aktion.spieler
            is SpielAktion.SchuldenstrichDurchfuehren -> aktion.spieler
            is SpielAktion.HandelsangebotErstellen -> aktion.spieler
            is SpielAktion.HandelsangebotAnnehmen -> aktion.spieler
            is SpielAktion.HandelsangebotAblehnen -> aktion.spieler
            is SpielAktion.HandelsangebotZurueckziehen -> aktion.spieler
            is SpielAktion.AnleihenangebotErstellen -> aktion.spieler
            is SpielAktion.AnleihenangebotAnnehmen -> aktion.spieler
            is SpielAktion.AnleihenangebotAblehnen -> aktion.spieler
            is SpielAktion.AnleihenangebotZurueckziehen -> aktion.spieler
            is SpielAktion.ZahlungsunfaehigkeitFeststellen -> aktion.spieler
            is SpielAktion.MitAuslandHandeln -> aktion.spieler
            is SpielAktion.KriegErklaeren -> aktion.aggressor
            is SpielAktion.KriegsAllianzBeitreten -> aktion.spieler
            is SpielAktion.WaffenstillstandAnbieten -> aktion.spieler
            is SpielAktion.WaffenstillstandAnnehmen -> aktion.spieler
            is SpielAktion.KriegKapitulieren -> aktion.spieler
            is SpielAktion.FriedensvertragVorschlagen -> aktion.spieler
            is SpielAktion.FriedensvertragAnnehmen -> aktion.spieler
            is SpielAktion.UnabhaengigenFriedenSchliessen -> aktion.spieler
            is SpielAktion.RessourcenUebertragen -> aktion.spieler
            is SpielAktion.RohstoffHandeln -> throw IllegalArgumentException(
                "Direkter bilateraler Rohstoffhandel ist über WLAN nicht zulässig; " +
                    "Handelsangebot erstellen und von der Gegenpartei annehmen lassen.",
            )
            is SpielAktion.ProzugBeginnen,
            is SpielAktion.VerarbeitungAusfuehren,
            is SpielAktion.VerwaltungsstandortVersorgen,
            is SpielAktion.VerbindlichkeitBegleichen,
            is SpielAktion.ProzugAbschliessen,
            SpielAktion.ZugBeenden,
            is SpielAktion.WarenkorbAendern -> zustand.aktiverSpieler
                ?: throw IllegalStateException("Es ist kein Spieler aktiv.")
        }
        require(akteur == sitzungsSpieler) {
            "Die Aktion gehört zu Spieler ${akteur.wert}, die Sitzung aber zu ${sitzungsSpieler.wert}."
        }
    }

    private fun pruefeSitzung(id: Long, token: String): Sitzung {
        val normalisiert = token.trim()
        if (normalisiert.isEmpty()) throw UngueltigeSpielSitzung()
        val sitzung = sitzungen[normalisiert] ?: throw UngueltigeSpielSitzung()
        if (sitzung.spielId != id) throw UngueltigeSpielSitzung()
        return sitzung
    }

    private suspend fun freieId(): Long {
        while (true) {
            val kandidat = naechsteId.getAndIncrement()
            if (ablage.spielLaden(kandidat) == null) return kandidat
        }
    }

    private fun neuesToken(): String {
        val bytes = ByteArray(32)
        zufall.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun pruefeVersion(version: Int) {
        require(version == API_VERSION) {
            "API-Version $version wird nicht unterstützt; erwartet wird $API_VERSION."
        }
    }
}

class NetzwerkSpielNichtGefunden(val spielId: Long) : NoSuchElementException(
    "Spielstand $spielId wurde nicht gefunden.",
)

class UngueltigeSpielSitzung : SecurityException("Ungültige oder abgelaufene Spielsitzung.")

class UngueltigeSpielerAnmeldung : SecurityException("Spielername oder Passwort ist ungültig.")

class RevisionKonflikt(
    val erwartet: Long,
    val aktuell: Long,
) : IllegalStateException("Veralteter Spielstand: Revision $erwartet erwartet, aktuell ist $aktuell.")
