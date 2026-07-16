# Architektur-Ist

Stand: 17.07.2026, Branch `codex/deutsche-architektur`.

## Kurzfassung

Die Anwendung besteht aus den Gradle-Modulen `:app` und `:domain`. Das Modul
`:domain` ist bereits ein reines Kotlin/JVM-Modul ohne Android-, Compose- oder
Room-Abhängigkeit und wird von `:app` produktiv verwendet. Die Migration ist
jedoch nur teilweise vollzogen: Das alte veränderliche `Spiel` bleibt die
maßgebliche Datenquelle für große Teile der Oberfläche und der Speicherung,
während ein unveränderlicher `SpielZustand` parallel für Zuglogik und erste
Darstellungszustände geführt wird.

Damit bestehen für eine laufende Partie weiterhin drei Darstellungen:

1. `datenbank.Spiel` mit veränderlichen Listen und berechneten Caches,
2. `fachlogik.modell.SpielZustand` mit Ereignisverlauf in `SpielAblauf`,
3. Room-Entitäten und der zusätzliche Cache `aktuelleDaten` im `GameViewModel`.

`GameViewModel` synchronisiert diese Darstellungen in beide Richtungen nur
teilweise. Das ist derzeit das größte Konsistenzrisiko.

## Module und Abhängigkeiten

### `:domain`

- Kotlin/JVM 17, Coroutines und Kotlin Serialization.
- Keine Android-, Compose- oder Room-Imports.
- Fachliche Paketwurzel: `de.teutonstudio.zentralbank.fachlogik`.
- Enthält `SpielZustand`, Geld- und Fachtypen, `SpielEreignis`, fachliche
  Teilregelwerke, `SpielAblauf` und benannte Auswertungen.
- Enthält schnelle JVM-Tests für Geld, Serialisierung, Regeln, Ereignisablauf
  und Zugphasen.

### `:app`

- Android-Anwendung mit Compose, Navigation, Room und Vico-Diagrammen.
- Hängt von `:domain` ab.
- Enthält gleichzeitig UI, Room-Entitäten, DAOs, Rekonstruktion von
  Spielständen und das alte Laufzeitmodell.

Der technische Modulname `:domain` ist in Gradle und Build-Automatisierung
bereits etabliert. Fachlich entspricht dieses Modul dem künftigen Bereich
`fachlogik`; eine sofortige Gradle-Modulumbenennung bringt keinen fachlichen
Nutzen, würde aber alle Build-Verweise berühren.

## Fachmodell und Zustandsänderungen

### Neuer Zustand

`SpielZustand` ist eine serialisierbare Data Class mit ausschließlich `val` und
Kotlin-Schnittstellen für unveränderliche Listen, Maps und Sets. Der Zustand
enthält unter anderem:

- Spieler und Konten,
- Rohstoffe und Warenkorb,
- Anleihen und deren Besitzer,
- Konflikte und Überschuldungsstatus,
- aktuelle Marktpreise und Leitzins,
- Runde, aktiven Spieler und Zugstatus.

Der Geldtyp `Geld` speichert Cent als `Long`. Fachlogik verwendet damit keine
binären Gleitkommazahlen für Geldbeträge. `Basispunkte` hält Zinssätze als
Ganzzahl. Die Methoden `zuMarkString()` und `zuProzentString()` liegen derzeit
noch im Fachmodul und vermischen Fachwert und deutsche UI-Formatierung.

### Ereignisse und Regeln

`SpielEreignis` deckt Warenkorb, Rohstoffbuchungen, Transaktionen, Anleihenhandel,
Rohstoffhandel, Expansion, Krieg, Schuldenstrich und Zugübergänge ab.

`SpielRegelwerk` koordiniert nur noch. Die Regeln sind aufgeteilt in:

- `ZugRegelwerk`,
- `RohstoffRegelwerk`,
- `FinanzRegelwerk`,
- `AnleihenRegelwerk`,
- `HandelsRegelwerk`,
- `ExpansionsRegelwerk`,
- `KonfliktRegelwerk`,
- `InsolvenzRegelwerk`.

Ein internes `SpielerRegelwerk` bündelt die von mehreren Bereichen benötigte,
validierte Ersetzung eines Spielers im unveränderlichen Zustand. Lesende
Berechnungen liegen in `FinanzAuswertung`, `MarktAuswertung`,
`AnleihenAuswertung` und `ZugAuswertung`.

### Ablauf

`SpielAblauf` hält angewandte und zurückgenommene Ereignisse in einem
`EreignisVerlauf`. Der aktuelle `SpielZustand` wird gecacht. Ein neues Ereignis
berechnet nur den Folgezustand. Vollständiges Falten erfolgt bei Initialisierung
mit einem Verlauf, bei Rückgängig und bei expliziter Integritätsprüfung;
Wiederholen wendet nur das nächste Ereignis auf den Cache an.

## Altes Laufzeitmodell

`datenbank/laufzeit.kt` enthält weiterhin das große Modell `Spiel` sowie
`Spieler`, `Handelsregister`, `Kriegsregister`, `Anleihe` und weitere Typen.
Das Modell führt Rundenlisten, veränderliche Caches und zahlreiche abgeleitete
Zeitreihen. Es wird von Marktplatz, Anleihen, Außenhandel, Spieleransicht,
Dialogen und Navigation direkt gelesen.

Dieses Modell ist ein Übergangsmodell. Neue Fachregeln dürfen nicht darauf
aufgebaut werden. Eine sofortige Entfernung ist noch nicht möglich, weil der
neue `SpielZustand` historische Markt-, Handels-, Bau- und Anleihenprojektionen
noch nicht vollständig ausdrückt.

## `GameViewModel`

`datenbank/GameViewModel.kt` hat 1.335 Zeilen und erbt von
`AndroidViewModel`. Es übernimmt gleichzeitig:

- Erzeugung und Laden der Room-Datenbank,
- Koordination über `ZentralbankSpeicher`,
- Cache aller Room-Datensätze,
- Rekonstruktion des alten `Spiel` aus acht Entitätstypen,
- Halten von `aktuellesSpiel` und `aktuelleDaten`,
- Halten von `SpielAblauf`, `spielZustand` und `spielUebersicht`,
- Anwenden von `SpielEreignis`,
- Synchronisierung von Legacy-Änderungen zurück in `SpielZustand`,
- Rundenwechsel und Teilpersistenz,
- Handel und Anleihenhandel,
- globale Fehlermeldungen.

Der Abschnitt ab ungefähr Zeile 736 besteht überwiegend aus auskommentiertem
Altcode. Drei leere Konfliktmethoden werden noch von der Navigation aufgerufen;
ihre Entfernung wäre deshalb ohne gleichzeitige Bereichsmigration nicht sicher.
`vernichteSpiel` ist ein produktiv verdrahteter `TODO()`.

Die Methode `synchronisiereSpielZustandNachLegacyAenderung()` bildet das mutierte
Legacy-Spiel erneut ab und übernimmt anschließend ausgewählte Felder aus dem
bisherigen Domain-Zustand. Dabei wird der Ereignisverlauf verworfen. Dies ist
eine konkrete Stelle, an der mehrere Wahrheiten zusammengeführt statt
vermieden werden.

## Persistenz

Room liegt vollständig in `:app`. `AppDatabase` enthält acht Entitätstypen:

- `SpielDaten`,
- `SpielerDaten`,
- `BauteilDaten`,
- `KontrolleDaten`,
- `RundeDaten`,
- `HandelsDaten`,
- `AnleiheDaten`,
- `VertragsDaten`.

`ZentralbankSpeicher` koordiniert acht DAOs und kapselt bereits einen Teil der
Room-Transaktionen. Seine Schnittstelle verwendet jedoch ausschließlich
Room-/Legacy-Typen und ist deshalb noch keine fachliche `SpielAblage`.

Der gespeicherte Stand ist ein Satz historischer Tabellenzeilen, kein
serialisierter `SpielZustand` und kein Ereignisverlauf. Die Rekonstruktion befindet
sich im `GameViewModel`; insbesondere alte Anleihendatensätze benötigen eine
Kompatibilitätsannahme über den ersten Besitzer.

## Navigation und Oberfläche

`Navigation.kt` erhält ein konkretes `GameViewModel` und reicht für fast alle
Spielbereiche das vollständige Legacy-`Spiel` weiter. Sie liest zusätzlich
`spielZustand` und `spielUebersicht` für Zuganzeige und Ausgabendialog. Navigation,
Sitzungszustand und Bereichszustände sind damit noch nicht getrennt.

### Marktplatz

`Marktplatz.kt` hat 642 Zeilen. Die zentrale Composable erhält das vollständige
`Spiel` und bereitet darin unter anderem auf:

- Marktpreis- und Warenkorbzeitreihen,
- Handelsdifferenzen,
- Bauwerkbewertungen,
- Diagrammreihen und Legenden,
- Kauf-/Verkaufseingaben und Dialogzustände.

Der Warenkorb wird bereits über einen Callback geändert. Der restliche Bereich
ist noch keine Route/Bildschirm-Trennung und hängt für historische Daten am
Legacy-Modell. Auskommentierte Handelsaufrufe zeigen eine unvollständige
frühere Anbindung.

### Anleihen

`Anleihen.kt` hat 1.674 Zeilen und enthält Fachauswertungen, Projektionen,
Diagrammaufbereitung, Filterzustände, Tabellen und Dialoge in einer Datei.
Geld wird dort teilweise für Diagramme in `Double` umgerechnet; die eigentlichen
Bestände bleiben im Legacy-Typ `Zahlungsmittel`. Der Bereich erhält ebenfalls
das vollständige `Spiel`.

## Tests und Ausgangsprüfung

Vor Beginn dieser Überarbeitung waren erfolgreich:

- `./gradlew test`
- `./gradlew assembleDebug`

Vorhandene Fachtests prüfen unter anderem gültige und ungültige Ereignisse,
Geldsummenneutralität, Rohstoffänderungen, Zugphasen, Rückgängig/Wiederholen,
Anleihen, Expansion, Konflikte, Überschuldung, Ablaufcache, Markt- und
Anleihenauswertungen sowie Serialisierung. App-Tests
decken Teile der Legacy-Finanzberechnungen, Zuordnungen und Anzeigen ab.

Es fehlen noch Tests einer fachlichen Ablageschnittstelle, einer zentralen
Spielsitzung und state-hoisted Bereichs-ViewModels.

## Festgestellte Regelverletzungen und Risiken

- Das Legacy-`Spiel` und `SpielZustand` sind gleichzeitig produktive Wahrheiten.
- `GameViewModel` rekonstruiert und koordiniert Persistenz direkt.
- Der Ereignisverlauf geht bei Legacy-Synchronisation und Rundenbeginn verloren.
- Compose-Bereiche berechnen umfangreiche fachliche Auswertungen aus `Spiel`.
- Formatierungsmethoden liegen im Fachmodul.
- `GameViewModel.vernichteSpiel` ist trotz produktivem Aufrufer nicht
  implementiert.
- Die vorhandene Room-Struktur kann einen vollständigen `SpielZustand` samt
  Ereignisverlauf nicht verlustfrei speichern.
- Eine vorschnelle Marktplatz-Migration würde historische Zeitreihen verlieren
  oder eine neue vierte Zwischenwahrheit erzeugen.

## Lokaler Git-Hinweis

Zu Beginn lag bereits eine nicht zugehörige Änderung vor:
`app/BEREINIGUNGSPLAN.md` ist im Index als leere neue Datei vorgemerkt und im
Arbeitsbaum gelöscht (`AD`). Diese Änderung wird im Umbau weder wiederhergestellt
noch gestaged oder committed.
