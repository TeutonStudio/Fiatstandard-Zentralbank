package de.teutonstudio.zentralbank.daten.zuordnung

import de.teutonstudio.zentralbank.daten.raumdatenbank.entitaet.SpielstandEntitaet
import de.teutonstudio.zentralbank.datenbank.Anleihe
import de.teutonstudio.zentralbank.datenbank.AnleiheDaten
import de.teutonstudio.zentralbank.datenbank.Anleihenhandel
import de.teutonstudio.zentralbank.datenbank.Ausland
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.BauteilDaten
import de.teutonstudio.zentralbank.datenbank.Geschäftsbank
import de.teutonstudio.zentralbank.datenbank.Handel
import de.teutonstudio.zentralbank.datenbank.HandelsDaten
import de.teutonstudio.zentralbank.datenbank.Handelsregister
import de.teutonstudio.zentralbank.datenbank.JuristischePerson
import de.teutonstudio.zentralbank.datenbank.KontrolleDaten
import de.teutonstudio.zentralbank.datenbank.Kriegsregister
import de.teutonstudio.zentralbank.datenbank.RohstoffHandel
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Runde
import de.teutonstudio.zentralbank.datenbank.RundeDaten
import de.teutonstudio.zentralbank.datenbank.SpeicherDaten
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.SpielDaten
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.SpielerDaten
import de.teutonstudio.zentralbank.datenbank.Vertrag
import de.teutonstudio.zentralbank.datenbank.VertragsDaten
import de.teutonstudio.zentralbank.datenbank.Vertragsart
import de.teutonstudio.zentralbank.datenbank.Wirtschaftsregionen
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.datenbank.fromString
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.schnittstelle.GespeichertesSpiel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val AKTUELLE_FORMAT_VERSION = 1
private const val AUSLAND_KENNUNG = "-ausland-"

private val spielstandJson = Json {
    classDiscriminator = "art"
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private val ereignisListe = ListSerializer(SpielEreignis.serializer())

fun GespeichertesSpiel.zuEntitaet(): SpielstandEntitaet = SpielstandEntitaet(
    spielId = id,
    formatVersion = AKTUELLE_FORMAT_VERSION,
    startzustandJson = spielstandJson.encodeToString(SpielZustand.serializer(), startzustand),
    ereignisseJson = spielstandJson.encodeToString(ereignisListe, ereignisse),
    ausLegacyDatenImportiert = ausLegacyDatenImportiert,
)

fun SpielstandEntitaet.zuGespeichertemSpiel(): GespeichertesSpiel {
    require(formatVersion == AKTUELLE_FORMAT_VERSION) {
        "Spielstand $spielId verwendet die nicht unterstützte Formatversion $formatVersion."
    }
    return GespeichertesSpiel(
        id = spielId,
        startzustand = spielstandJson.decodeFromString(SpielZustand.serializer(), startzustandJson),
        ereignisse = spielstandJson.decodeFromString(ereignisListe, ereignisseJson),
        ausLegacyDatenImportiert = ausLegacyDatenImportiert,
    )
}

fun List<SpeicherDaten>.zuLegacySpiel(daten: SpielDaten): Spiel {
    val runden = filterIsInstance<RundeDaten>()
        .sortedBy { it.index }
        .map { Runde(it.index, it.leitzinssatz) }
        .toMutableList()

    require(runden.isNotEmpty()) {
        "Spiel ${daten.spielID} enthält keine Rundendaten."
    }
    require(runden.map { it.index } == runden.indices.toList()) {
        "Rundendaten von Spiel ${daten.spielID} sind nicht lückenlos bei 0 beginnend."
    }

    val rundenAnzahl = runden.size
    val bauDaten = filterIsInstance<BauteilDaten>()
    val kontrollDaten = filterIsInstance<KontrolleDaten>()
    val spieler = filterIsInstance<SpielerDaten>()
        .sortedBy { it.spielerID }
        .map { spielerDaten ->
            val gebaut = MutableList<Map<out Bauteil, Int>>(rundenAnzahl) { emptyMap() }
            val kontrolle = MutableList<Map<Wirtschaftsregionen, Int>>(rundenAnzahl) { emptyMap() }

            bauDaten
                .filter { it.erbauer == spielerDaten.spielerName }
                .groupBy { it.runde }
                .forEach { (runde, einträge) ->
                    require(runde in gebaut.indices) {
                        "Baudaten für unbekannte Runde $runde bei ${spielerDaten.spielerName}."
                    }
                    gebaut[runde] = einträge
                        .groupBy { it.bauteil.zuBauteilOderFehler() }
                        .mapValues { (_, werte) -> werte.sumOf { it.delta } }
                }

            kontrollDaten
                .filter { it.besatzer == spielerDaten.spielerName }
                .groupBy { it.runde }
                .forEach { (runde, einträge) ->
                    require(runde in kontrolle.indices) {
                        "Kontrolldaten für unbekannte Runde $runde bei ${spielerDaten.spielerName}."
                    }
                    kontrolle[runde] = einträge
                        .groupBy { it.region.zuWirtschaftsregionOderFehler() }
                        .mapValues { (_, werte) -> werte.sumOf { it.delta } }
                }

            Spieler(
                name = spielerDaten.spielerName,
                gebaut = gebaut,
                kontrolle = kontrolle,
            )
        }

    val handelsEinträge = MutableList<Set<Handel>>(rundenAnzahl) { emptySet() }
    filterIsInstance<HandelsDaten>().forEach { handelsDaten ->
        require(handelsDaten.runde in handelsEinträge.indices) {
            "Handelsdaten für unbekannte Runde ${handelsDaten.runde}."
        }
        val handel = RohstoffHandel(
            besitzer = findeJuristischePerson(handelsDaten.besitzer, spieler),
            erwerber = findeJuristischePerson(handelsDaten.erwerber, spieler),
            betrag = handelsDaten.preis.toZahlungsmittel(),
            anzahl = handelsDaten.menge,
            rohstoff = handelsDaten.rohstoff.zuRohstoffOderFehler(),
        )
        handelsEinträge[handelsDaten.runde] = handelsEinträge[handelsDaten.runde] + handel
    }

    filterIsInstance<AnleiheDaten>().forEach { anleiheDaten ->
        require(anleiheDaten.emittiert in handelsEinträge.indices) {
            "Anleihedaten für unbekannte Emissionsrunde ${anleiheDaten.emittiert}."
        }
        val anleihe = Anleihe(
            schuldiger = findeJuristischePerson(anleiheDaten.emittent, spieler),
            sondervermögen = anleiheDaten.sondervermogen.toZahlungsmittel(),
            unvermögen = anleiheDaten.unvermogen.toZahlungsmittel(),
            laufzeit = anleiheDaten.laufzeit,
        )
        val neuesFormat = anleiheDaten.handel
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.split("#") }

        if (neuesFormat.isNotEmpty() && neuesFormat.all { it.size == 4 }) {
            neuesFormat
                .map { teile ->
                    GespeicherterAnleihehandel(
                        besitzer = teile[0],
                        erwerber = teile[1],
                        preis = teile[2].toZahlungsmittel(),
                        runde = teile[3].toInt(),
                    )
                }
                .sortedBy { it.runde }
                .forEach { eintrag ->
                    require(eintrag.runde in handelsEinträge.indices) {
                        "Anleihehandel für unbekannte Runde ${eintrag.runde}."
                    }
                    handelsEinträge[eintrag.runde] = handelsEinträge[eintrag.runde] +
                        Anleihenhandel(
                            besitzer = findeJuristischePerson(eintrag.besitzer, spieler),
                            erwerber = findeJuristischePerson(eintrag.erwerber, spieler),
                            anleihe = anleihe,
                            preis = eintrag.preis,
                        )
                }
        } else {
            // Alte Datensätze enthalten weder den ersten Erwerber noch die Vorbesitzer.
            var aktuellerBesitzer: JuristischePerson = Geschäftsbank
            handelsEinträge[anleiheDaten.emittiert] = handelsEinträge[anleiheDaten.emittiert] +
                Anleihenhandel(
                    besitzer = anleihe.schuldiger,
                    erwerber = aktuellerBesitzer,
                    anleihe = anleihe,
                    preis = anleihe.sondervermögen,
                )
            anleiheDaten.handel
                .split("/")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { eintrag ->
                    val teile = eintrag.split("#")
                    require(teile.size == 3) { "Ungültiger Anleihe-Handelseintrag: $eintrag" }
                    GespeicherterAnleihehandel(
                        besitzer = aktuellerBesitzer.name,
                        erwerber = teile[0],
                        preis = teile[1].toIntOrNull()?.toZahlungsmittel()
                            ?: teile[1].toZahlungsmittel(),
                        runde = teile[2].toInt(),
                    )
                }
                .sortedBy { it.runde }
                .forEach { eintrag ->
                    require(eintrag.runde in handelsEinträge.indices) {
                        "Anleihehandel für unbekannte Runde ${eintrag.runde}."
                    }
                    val erwerber = findeJuristischePerson(eintrag.erwerber, spieler)
                    handelsEinträge[eintrag.runde] = handelsEinträge[eintrag.runde] +
                        Anleihenhandel(
                            besitzer = aktuellerBesitzer,
                            erwerber = erwerber,
                            anleihe = anleihe,
                            preis = eintrag.preis,
                        )
                    aktuellerBesitzer = erwerber
                }
        }
    }

    val vertragsEinträge = MutableList<Set<Vertrag>>(rundenAnzahl) { emptySet() }
    filterIsInstance<VertragsDaten>().forEach { vertragsDaten ->
        require(vertragsDaten.runde in vertragsEinträge.indices) {
            "Vertragsdaten für unbekannte Runde ${vertragsDaten.runde}."
        }
        val vertrag = Vertrag(
            vertragsannehmer = listOf(vertragsDaten.vertragsannehmer),
            vertragsanbieter = listOf(vertragsDaten.vertragsanbieter),
            vertragsart = vertragsDaten.vertragsart.zuVertragsartOderFehler(),
        )
        vertragsEinträge[vertragsDaten.runde] = vertragsEinträge[vertragsDaten.runde] + vertrag
    }

    return Spiel(
        runden = runden,
        spieler = spieler,
        warenkorb = daten.warenkorb.zuWarenkorb(),
        inflationsziel = Triple(daten.inflationsziel, daten.nAbweichung, daten.sAbweichung),
        handel = Handelsregister(handelsEinträge),
        konflikt = Kriegsregister(vertragsEinträge),
    )
}

private fun String.zuWarenkorb(): Map<Rohstoffe, Int> {
    if (isBlank()) return emptyMap()
    return split("/")
        .filter { it.isNotBlank() }
        .associate { eintrag ->
            val teile = eintrag.split("#")
            require(teile.size == 2) { "Ungültiger Warenkorb-Eintrag: $eintrag" }
            teile[0].zuRohstoffOderFehler() to teile[1].toInt()
        }
}

private data class GespeicherterAnleihehandel(
    val besitzer: String,
    val erwerber: String,
    val preis: Zahlungsmittel,
    val runde: Int,
)

private fun findeJuristischePerson(name: String, spieler: List<Spieler>): JuristischePerson =
    spieler.firstOrNull { it.name == name } ?: when (name) {
        Ausland.name, AUSLAND_KENNUNG -> Ausland
        Geschäftsbank.name, "Zentralbank" -> Geschäftsbank
        else -> error("Unbekannte juristische Person: $name")
    }

private fun String.zuRohstoffOderFehler(): Rohstoffe {
    val text = trim()
    return Rohstoffe.entries.firstOrNull { it.name == text || it.str == text }
        ?: error("Unbekannter Rohstoff: $text")
}

private fun String.zuBauteilOderFehler(): Bauteil {
    val text = trim()
    return Bauteil.fromString(text)
        ?: Bauteil.entries.firstOrNull { it.toString() == text }
        ?: error("Unbekanntes Bauteil: $text")
}

private fun String.zuWirtschaftsregionOderFehler(): Wirtschaftsregionen {
    val text = trim()
    return Wirtschaftsregionen.entries.firstOrNull { it.name == text || it.str == text }
        ?: error("Unbekannte Wirtschaftsregion: $text")
}

private fun String.zuVertragsartOderFehler(): Vertragsart {
    val text = trim()
    return Vertragsart.entries.firstOrNull { it.name == text || it.str == text }
        ?: error("Unbekannte Vertragsart: $text")
}
