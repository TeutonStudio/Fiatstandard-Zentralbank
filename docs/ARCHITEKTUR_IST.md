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
3. der kanonische Room-Spielstand sowie Legacy-Entitäten und der zusätzliche
   Cache `aktuelleDaten` im `GameViewModel`.

`GameViewModel` synchronisiert diese Darstellungen in beide Richtungen nur
teilweise. Das ist derzeit das größte Konsistenzrisiko.

## Module und Abhängigkeiten

### `:domain`

- Kotlin/JVM 17, Coroutines und Kotlin Serialization.
- Keine Android-, Compose- oder Room-Imports.
- Fachliche Paketwurzel: `de.teutonstudio.zentralbank.fachlogik`.
- Enthält `SpielZustand`, Geld- und Fachtypen, `SpielEreignis`, fachliche
  Teilregelwerke, `SpielAblauf`, benannte Auswertungen und die Android-freie
  Schnittstelle `SpielAblage`.
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

`datenbank/GameViewModel.kt` hat nach Entfernung von Altcode und ausgelagerter
Spielstandrekonstruktion 571 Zeilen und erbt von
`AndroidViewModel`. Es übernimmt gleichzeitig:

- Erzeugung und Laden der Room-Datenbank,
- Übergangsschreibzugriffe über `ZentralbankSpeicher`,
- einen internen Cache der Legacy-Room-Datensätze,
- Laden, Speichern und Löschen über `SpielAblage`,
- Halten von `aktuellesSpiel` und `aktuelleDaten`,
- Halten von `SpielAblauf`, `spielZustand` und `spielUebersicht`,
- Anwenden von `SpielEreignis`,
- Synchronisierung von Legacy-Änderungen zurück in `SpielZustand`,
- Rundenwechsel und parallele Legacy-Persistenz,
- Handel und Anleihenhandel,
- globale Fehlermeldungen.

Der frühere, knapp 600 Zeilen lange auskommentierte DAO-/Cache-Block ist
entfernt. Die Zuordnung der Wirtschaftstabellen liegt in
`daten/zuordnung/SpielstandZuordnung.kt`. Spielstände werden nach Bestätigung
transaktional gelöscht. Noch nicht verfügbare Konfliktaktionen melden dies
ausdrücklich.

Die Methode `synchronisiereSpielZustandNachTabellenAenderung()` bildet das mutierte
Tabellenspiel erneut ab und übernimmt anschließend ausgewählte Felder aus dem
bisherigen Domain-Zustand. Dabei wird der Ereignisverlauf verworfen. Dies ist
eine konkrete Stelle, an der mehrere Wahrheiten zusammengeführt statt
vermieden werden.

## Persistenz

Room liegt vollständig in `:app`. `AppDatabase` verwendet Schema-Version 4.
Neben den acht Wirtschaftstabellen

- `SpielDaten`,
- `SpielerDaten`,
- `BauteilDaten`,
- `KontrolleDaten`,
- `RundeDaten`,
- `HandelsDaten`,
- `AnleiheDaten`,
- `VertragsDaten`

existiert `SpielstandEntitaet` in der Tabelle `FachSpielstand`. Sie speichert
versioniert den Startzustand und den angewandten Ereignisverlauf. Es gibt keine
Schema- oder Datenmigration für ältere Speicherstände; bei einer veralteten
Room-Version wird die Datenbank vollständig neu angelegt.

Die Android-freie Schnittstelle `SpielAblage` definiert Beobachten, Laden,
Speichern und Löschen mit `SpielstandUebersicht` und `GespeichertesSpiel`.
`RaumSpielAblage` implementiert sie in `:app` und liest ausschließlich das
kanonische `FachSpielstand`-Format. Reine Altbestände aus den Wirtschaftstabellen
werden nicht importiert.
Die Ladeoberfläche verwendet nur noch `SpielstandUebersicht` und keine
Room-Entitäten.

`ZentralbankSpeicher` bleibt als Tabellenfassade für die derzeit von der
Oberfläche benötigten Wirtschaftsdaten bestehen. Neue fachliche Speicherungen
erhalten Startzustand und Ereignisse. Gleichzeitig gestartete Speicheraufträge
werden pro Spielstand geordnet.

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

Ein Ablagevertragstest prüft Beobachten, Speichern, Laden und Löschen; weitere
Tests sichern JSON-/Room-Rundlauf, Formatversionsfehler und die
Legacy-Rekonstruktion. Es fehlen noch Tests einer zentralen Spielsitzung und
state-hoisted Bereichs-ViewModels.

## Festgestellte Regelverletzungen und Risiken

- Das Legacy-`Spiel` und `SpielZustand` sind gleichzeitig produktive Wahrheiten.
- `GameViewModel` schreibt für nicht migrierte Bereiche weiterhin parallel in
  die Legacy-Fassade, obwohl Laden, Speichern und Löschen bereits über
  `SpielAblage` laufen.
- Der Ereignisverlauf geht bei Legacy-Synchronisation und Rundenbeginn verloren.
- Compose-Bereiche berechnen umfangreiche fachliche Auswertungen aus `Spiel`.
- Formatierungsmethoden liegen im Fachmodul.
- Konfliktaktionen sind bis zu ihrer vollständigen vertikalen Migration
  gesperrt.
- Legacy-Tabellen können keinen ursprünglichen Ereignisverlauf liefern; ein
  solcher Stand bleibt deshalb als Import ohne vollständigen Verlauf markiert.
- Eine vorschnelle Marktplatz-Migration würde historische Zeitreihen verlieren
  oder eine neue vierte Zwischenwahrheit erzeugen.

## Lokaler Git-Hinweis

Zu Beginn lag bereits eine nicht zugehörige Änderung vor:
`app/BEREINIGUNGSPLAN.md` ist im Index als leere neue Datei vorgemerkt und im
Arbeitsbaum gelöscht (`AD`). Diese Änderung wird im Umbau weder wiederhergestellt
noch gestaged oder committed.
