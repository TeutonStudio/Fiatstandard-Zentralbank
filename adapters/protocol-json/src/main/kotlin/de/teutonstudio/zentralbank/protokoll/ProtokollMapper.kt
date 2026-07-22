package de.teutonstudio.zentralbank.protokoll

import de.teutonstudio.zentralbank.anwendung.stabilerHash
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitArt
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val protokollJson = Json {
    classDiscriminator = "art"
    encodeDefaults = true
}

fun SpielZustand.zuDto(): SpielZustandDto = SpielZustandDto(
    spieler = spieler.map { eintrag ->
        SpielerDto(
            id = eintrag.id.wert,
            name = eintrag.name,
            rohstoffe = eintrag.rohstoffe.mapKeys { (rohstoff, _) -> rohstoff.name },
            geldCent = eintrag.geldkonto.cent,
            anleihen = eintrag.anleihen.map { it.wert },
            bauteile = eintrag.bauteile.mapKeys { (bauteil, _) -> bauteil.name },
            spielstil = eintrag.spielstil.name,
        )
    },
    spielabschnitt = spielabschnitt.name,
    runde = rundenzähler,
    aktiverSpieler = aktiverSpieler?.wert,
    zug = zugStatus?.let { status ->
        ZugDto(
            zugId = status.zugId,
            spieler = status.spieler.wert,
            phase = when (status.phase) {
                ZugPhase.Prozug -> "PROZUG"
                ZugPhase.Epizug -> "EPIZUG"
            },
            prozugBegonnen = status.prozug.begonnen,
            prozugAbgeschlossen = status.prozug.erfolgreichAbgeschlossen,
        )
    },
    bankkontoCent = bankkonto.cent,
    auslandskontoCent = auslandskonto.cent,
    zustandsHash = stabilerHash(),
)

fun SpielAktion.zuDto(): SpielAktionDto = when (this) {
    is SpielAktion.ProzugBeginnen -> SpielAktionDto.ProzugBeginnen(zugId)
    is SpielAktion.VerarbeitungAusfuehren -> SpielAktionDto.VerarbeitungAusfuehren(
        zugId = zugId,
        feld = feld.zuDto(),
        laeufe = laeufe,
    )
    is SpielAktion.VerwaltungsstandortVersorgen ->
        SpielAktionDto.VerwaltungsstandortVersorgen(zugId, ecke.zuDto())
    is SpielAktion.VerbindlichkeitBegleichen ->
        SpielAktionDto.VerbindlichkeitBegleichen(zugId, verbindlichkeit.zuDto())
    is SpielAktion.ProzugAbschliessen -> SpielAktionDto.ProzugAbschliessen(zugId)
    SpielAktion.ZugBeenden -> SpielAktionDto.ZugBeenden
    else -> SpielAktionDto.ErweiterteAktion(
        protokollJson.encodeToString(SpielAktion.serializer(), this),
    )
}

fun SpielAktionDto.zuDomain(): SpielAktion = when (this) {
    is SpielAktionDto.ProzugBeginnen -> SpielAktion.ProzugBeginnen(zugId)
    is SpielAktionDto.VerarbeitungAusfuehren -> SpielAktion.VerarbeitungAusfuehren(
        zugId = zugId,
        feld = feld.zuDomain(),
        laeufe = laeufe,
    )
    is SpielAktionDto.VerwaltungsstandortVersorgen ->
        SpielAktion.VerwaltungsstandortVersorgen(zugId, ecke.zuDomain())
    is SpielAktionDto.VerbindlichkeitBegleichen ->
        SpielAktion.VerbindlichkeitBegleichen(zugId, verbindlichkeit.zuDomain())
    is SpielAktionDto.ProzugAbschliessen -> SpielAktion.ProzugAbschliessen(zugId)
    SpielAktionDto.ZugBeenden -> SpielAktion.ZugBeenden
    is SpielAktionDto.ErweiterteAktion -> protokollJson.decodeFromString(
        SpielAktion.serializer(),
        kodierung,
    )
}

fun SpielEreignis.zuDto(): SpielEreignisDto = SpielEreignisDto(
    typ = this::class.simpleName ?: "UnbekanntesEreignis",
    daten = protokollJson.encodeToJsonElement(SpielEreignis.serializer(), this),
)

private fun KartenFeld.zuDto() = KartenFeldDto(zeile, spalte, haelfte.name)

private fun KartenFeldDto.zuDomain() = KartenFeld(
    zeile = zeile,
    spalte = spalte,
    haelfte = DreieckHaelfte.valueOf(haelfte),
)

private fun KartenEcke.zuDto() = KartenEckeDto(x, y)

private fun KartenEckeDto.zuDomain() = KartenEcke(x = x, y = y)

private fun VerbindlichkeitId.zuDto() = VerbindlichkeitIdDto(
    anleiheId = anleihe.wert,
    zugId = zugId,
    art = art.name,
)

private fun VerbindlichkeitIdDto.zuDomain() = VerbindlichkeitId(
    anleihe = AnleiheId(anleiheId),
    zugId = zugId,
    art = VerbindlichkeitArt.valueOf(art),
)
