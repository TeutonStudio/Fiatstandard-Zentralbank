package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class KriegId(val wert: String) {
    init {
        require(wert.isNotBlank()) { "Eine Krieg-ID darf nicht leer sein." }
    }
}

@JvmInline
@Serializable
value class FriedensvertragId(val wert: String) {
    init {
        require(wert.isNotBlank()) { "Eine Friedensvertrag-ID darf nicht leer sein." }
    }
}

@Serializable
enum class KriegsSeite {
    AGGRESSOREN,
    VERTEIDIGER,
}

@Serializable
enum class KriegsStatus {
    AKTIV,
    FRIEDEN_ANGEBOTEN,
    BEENDET,
}

@Serializable
data class SpielerPaar(
    val erster: SpielerId,
    val zweiter: SpielerId,
) {
    init {
        require(erster != zweiter) { "Ein Spielerpaar braucht zwei verschiedene Spieler." }
        require(erster.wert < zweiter.wert) { "Ein Spielerpaar muss kanonisch sortiert sein." }
    }

    fun enthaelt(spieler: SpielerId): Boolean = spieler == erster || spieler == zweiter

    companion object {
        fun aus(a: SpielerId, b: SpielerId): SpielerPaar =
            if (a.wert < b.wert) SpielerPaar(a, b) else SpielerPaar(b, a)
    }
}

@Serializable
data class WaffenstillstandsAngebot(
    val von: SpielerId,
    val an: SpielerId,
    val runde: Int,
) {
    init {
        require(von != an) { "Ein Waffenstillstand kann nicht sich selbst angeboten werden." }
        require(runde >= 0) { "Die Angebotsrunde darf nicht negativ sein." }
    }
}

/**
 * Der bestehende Konflikttyp ist der eine formale Kriegs-Aggregatzustand. Seine beiden
 * historischen Felder bleiben zwecks lesbarer Replays erhalten; die tatsächlichen Seiten
 * stehen in [aggressoren] und [verteidiger].
 */
@Serializable
data class Konflikt(
    val spielerA: SpielerId,
    val spielerB: SpielerId,
    val id: KriegId = KriegId(kanonischeKriegId(spielerA, spielerB)),
    val aggressoren: Set<SpielerId> = setOf(spielerA),
    val verteidiger: Set<SpielerId> = setOf(spielerB),
    val waffenstillstandsAngebote: List<WaffenstillstandsAngebot> = emptyList(),
    val waffenstillstaende: Set<SpielerPaar> = emptySet(),
    val kapitulationen: Set<SpielerId> = emptySet(),
    val status: KriegsStatus = KriegsStatus.AKTIV,
    val begonnenInRunde: Int = 0,
) {
    init {
        require(spielerA != spielerB) { "Konfliktparteien müssen verschieden sein." }
        require(aggressoren.isNotEmpty() && verteidiger.isNotEmpty()) {
            "Ein Krieg braucht auf beiden Seiten mindestens einen Teilnehmer."
        }
        require(aggressoren.intersect(verteidiger).isEmpty()) {
            "Ein Spieler darf nicht auf beiden Kriegsseiten stehen."
        }
        require(begonnenInRunde >= 0) { "Die Kriegsrunde darf nicht negativ sein." }
    }

    val teilnehmer: Set<SpielerId> get() = aggressoren + verteidiger

    fun betrifft(a: SpielerId, b: SpielerId): Boolean =
        a != b && a in teilnehmer && b in teilnehmer && aufVerschiedenenSeiten(a, b)

    fun aufVerschiedenenSeiten(a: SpielerId, b: SpielerId): Boolean =
        (a in aggressoren && b in verteidiger) || (a in verteidiger && b in aggressoren)

    fun seiteVon(spieler: SpielerId): KriegsSeite? = when (spieler) {
        in aggressoren -> KriegsSeite.AGGRESSOREN
        in verteidiger -> KriegsSeite.VERTEIDIGER
        else -> null
    }

    fun hatWaffenstillstand(a: SpielerId, b: SpielerId): Boolean =
        SpielerPaar.aus(a, b) in waffenstillstaende

    fun vollstaendigImWaffenstillstand(): Boolean = aggressoren.all { aggressor ->
        verteidiger.all { verteidiger -> hatWaffenstillstand(aggressor, verteidiger) }
    }
}

@Serializable
data class SchuldUebertragung(
    val verlierer: SpielerId,
    val betrag: Geld,
    val marktwertVorFrieden: Geld,
    val rundungsRestCent: Long = 0L,
)

@Serializable
data class Friedensvertrag(
    val id: FriedensvertragId,
    val krieg: KriegId,
    val beteiligteSpieler: Set<SpielerId>,
    val gewinner: Set<SpielerId> = emptySet(),
    val verlierer: Set<SpielerId> = emptySet(),
    val unentschiedeneTeilnehmer: Set<SpielerId> = emptySet(),
    val ausscheidendeTeilnehmer: Set<SpielerId> = emptySet(),
    /** Spieler, die diesen Vertrag durch Kriegskapitulation ausgelöst haben. */
    val kapitulationen: Set<SpielerId> = emptySet(),
    val schuldUebertragungen: List<SchuldUebertragung> = emptyList(),
    val entstehendeAnleihen: List<AnleiheId> = emptyList(),
    val schuldenstrichDanach: Set<SpielerId> = emptySet(),
    val angenommenVon: Set<SpielerId> = emptySet(),
    val abgeschlossenInRunde: Int? = null,
) {
    init {
        require(beteiligteSpieler.isNotEmpty()) { "Ein Friedensvertrag braucht Beteiligte." }
        require(gewinner.intersect(verlierer).isEmpty()) {
            "Ein Spieler kann nicht zugleich Gewinner und Verlierer sein."
        }
        require(gewinner + verlierer + unentschiedeneTeilnehmer == beteiligteSpieler) {
            "Jeder Vertragsbeteiligte muss genau als Gewinner, Verlierer oder unentschieden eingeordnet sein."
        }
        require(ausscheidendeTeilnehmer inhaltlichTeilmengeVon beteiligteSpieler) {
            "Nur Vertragsbeteiligte können durch den Vertrag aus einem Krieg ausscheiden."
        }
        require(kapitulationen inhaltlichTeilmengeVon verlierer) {
            "Eine Kapitulation muss als Verliererrolle im Vertrag ausgewiesen sein."
        }
    }
}

private infix fun Set<SpielerId>.inhaltlichTeilmengeVon(andere: Set<SpielerId>): Boolean =
    all { it in andere }

@Serializable
data class Belagerung(
    val standort: KartenEcke,
    val verteidiger: SpielerId,
    val beteiligteBelagerer: Map<SpielerId, Int>,
    val begonnenInRunde: Int,
    val fortschrittRunden: Int = 0,
    val fuehrenderBelagerer: SpielerId,
    val fuehrungsBeginnRunde: Int = begonnenInRunde,
    val gespeicherterErtrag: Map<Rohstoff, Int> = emptyMap(),
) {
    init {
        require(beteiligteBelagerer.isNotEmpty()) { "Eine Belagerung braucht Belagerer." }
        require(beteiligteBelagerer.values.all { it > 0 }) {
            "Belagerungsstärken müssen positiv sein."
        }
        require(fuehrenderBelagerer in beteiligteBelagerer) {
            "Der führende Belagerer muss beteiligt sein."
        }
        require(fortschrittRunden >= 0 && begonnenInRunde >= 0) {
            "Belagerungsrunden dürfen nicht negativ sein."
        }
    }
}

@Serializable
data class ZentralbankGeldschoepfung(
    val spieler: SpielerId,
    val betrag: Geld,
    val runde: Int,
    val grund: String,
)

private fun kanonischeKriegId(a: SpielerId, b: SpielerId): String =
    listOf(a.wert, b.wert).sorted().joinToString(prefix = "krieg-", separator = "-")
