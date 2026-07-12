# Architektur-Ist

Stand: Etappe 0 des Umbaus aus `Umbau.md`.

## Projektstruktur

- `:app` ist aktuell das einzige Gradle-Modul.
- Paketwurzel: `de.teutonstudio.zentralbank`.
- UI: Jetpack Compose im App-Modul.
- Persistenz: Room im App-Modul.
- Domain-/Regellogik: ebenfalls im App-Modul unter `datenbank/`, nicht von Android getrennt.
- Reines Domain-Modul `:domain` existiert noch nicht.
- Netzwerk-Modul existiert noch nicht.

## Einstieg und Navigation

- `MainActivity` erstellt `GameViewModel` mit `GameViewModelFactory(application)` und ruft `Navigation(viewModel)` im Compose-Theme auf.
- `Navigation.kt` definiert die Routen:
  - `main_screen`
  - `new_game`
  - `load_game`
  - `game`
  - `edit_round`
  - `player_saldo`
  - `debt_saldo`
  - `market_saldo`
  - `foreign_saldo`
  - `price_index`
  - `new_trade`
  - `new_credit`
  - `new_build`
- Einige Routen enthalten noch auskommentierte oder leere Implementierungen, besonders `NewTrade`, `NewCredit` und `NewBuild`.

## Screens und wichtige Composables

### Kategorien

- `Hauptmenü.kt`
  - `SpielCard`
  - `Hauptmenü`
- `SpielErstellen.kt`
  - `SpielErstellen`
  - hält lokale Eingabeschritte mit `remember`, `mutableStateMapOf`, `mutableStateListOf`.
- `SpielLaden.kt`
  - `SpielLaden`
  - hält lokale Auswahl mit `remember`.
- `Spielmenü.kt`
  - `Spielmenü`
- `NeueRunde.kt`
  - `NeueRunde`
- `Marktplatz.kt`
  - `zeigeHafenPreis`
  - `zeigeMarktplatz`
  - hält lokale UI-/Dialogzustände für Handel, Chartauswahl und Eingaben.
- `Außenhandel.kt`
  - `zeigeAussenhandel`
- `Anleihen.kt`
  - `AnleihenRegister`
  - mehrere Chart-, Header- und Karten-Composables.

### Ausgabe

- `Handbuch.kt`
  - `zeigeHandbuch`
- `PDF.kt`
  - `PdfAnzeige`
  - `PdfAnzeigeMitLinks`
- `bauteil.kt`
  - `zeigeBauteil`
  - `zeigeBauteilPreis`
- `rohstoff.kt`
  - `bauteilIcon`
  - `zeigeRohstoff`
- `siedler.kt`
  - `SpielerBilanz`
  - `zeigeSpieler`
  - `spielerAuswahl`
  - `zeigeSpielerDaten`
- `anleihe.kt`
  - `zeigeAnleihe`
- `handel.kt`
  - `ZeigeHandel`

### Eingabe und Vorlagen

- `steuerung.kt`
  - `Titel`
  - `SteuerContainer`
- `runde.kt`
  - `definiereRunde`
  - `definiereLeitzinssatz`
  - `bearbeiteRunde`
- `spieler.kt`
  - `SpielerAnzahlAuswahl`
  - `SpielerDaten`
  - `definiereSpieler`
- `warenkorb.kt`
  - `definiereRohstoffMenge`
  - `definiereWarenkorb`
- `bauteile.kt`
  - `definiereBauteilMenge`
  - `definiereBauteile`
- `Rohstoff.kt`
  - `wähleRohstoff`
- `anleihe.kt`
  - `definiereRunde`
  - `wähleLaufzeit`
  - `definiereAnleihe`
- `zahlungsmittel.kt`
  - `ZahlungsmittelEingabe`
- `zins.kt`
  - `ZinsEingabe`
- `zentralbank.kt`
  - `definiereLeitzinsatzZiele`
- `StiftCard.kt`
  - `StiftCard`
- `ImageCard.kt`
  - `ImageCard`
- `vorlagen.kt`
  - allgemeine Layout- und Textbausteine wie `TextCard`, `GridByOrientation`, `FlowByOrientation`, `Titel`, `markBy`.

## Zustandshaltung

### ViewModel

- `GameViewModel` liegt unter `datenbank/verwalter.kt` und erbt von `AndroidViewModel`.
- Persistenzzugriff läuft über `ZentralbankSpeicher`.
- Exponierte Flows:
  - `spielDatenListe: StateFlow<Map<SpielDaten, List<SpeicherDaten>>>`
  - `spielSpeicher: StateFlow<Map<SpielDaten, Pair<Int, List<String>>>>`
- Zusätzlich werden `lateinit var aktuelleDaten` und `lateinit var aktuellesSpiel` als mutable aktueller Spielzustand gehalten.
- Das ViewModel initialisiert Room, lädt gespeicherte Spiele und fügt zusätzlich `TestSpiel` in die Speicherliste ein.

### Persistenz

- Room-Entities in `datenbank/database.kt`:
  - `SpielDaten`
  - `SpielerDaten`
  - `BauteilDaten`
  - `KontrolleDaten`
  - `RundeDaten`
  - `HandelsDaten`
  - `AnleiheDaten`
  - `VertragsDaten`
- DAOs und Speicherfassade in `datenbank/DataAccessObject.kt`.
- `AppDatabase` wird in `datenbank/erzeuger.kt` als Singleton mit `Room.databaseBuilder` erzeugt.
- Savegame ist aktuell Tabellenzustand, kein Event-Log.

### Laufzeitmodell

- Zentrale Klassen in `datenbank/laufzeit.kt`:
  - `Spiel`
  - `Runde`
  - `JuristischePerson`
  - `Geschäftsbank`
  - `Ausland`
  - `Spieler`
  - `Handelsregister`
  - `Handel`
  - `Anleihenhandel`
  - `Anleihe`
  - `RohstoffHandel`
  - `Kriegsregister`
  - `Vertrag`
  - `KonfliktStatus`
- `Spiel`, `Spieler`, `Handelsregister` und `Kriegsregister` verwenden intern mutable Listen und Cache-Listen.
- Zustandsänderungen passieren über `neueRundenDatenDefinieren(...)`; es gibt noch keinen Reducer und keine validierten Events.

### UI-lokaler Zustand

- Viele Eingabe- und Auswahlzustände liegen direkt in Composables über `remember`, `mutableStateOf`, `mutableIntStateOf`, `mutableFloatStateOf`, `mutableStateMapOf` und `mutableStateListOf`.
- Beispiele:
  - Spielanlage: Spieler, Warenkorb, Zentralbankziele, Bauwerke.
  - Spiel laden: gewählter Spielstand.
  - Marktplatz: Kauf-/Verkaufsdialoge, Rohstoff, Preis, Menge, Spieler.
  - Spieleransicht: Kriegs-/Militär-/Friedensauswahl.
  - Anleihenregister: Filter und Verwaltungsansicht.

## Geld, Rohstoffe und Zahlen

- Geld wird fachlich mit `Zahlungsmittel` modelliert (`datenbank/zahlungsmittel.kt`).
- `Zahlungsmittel` speichert intern Vorzeichen plus Stellenliste in Basis `1000`; es ist noch kein `Long` in kleinster Einheit.
- In Persistenz werden Geldbeträge überwiegend als `String` gespeichert (`preis`, `sondervermogen`, `unvermogen`).
- `Float` wird für Zinssätze, Inflation und UI-Eingaben verwendet:
  - `SpielDaten.inflationsziel`, `nAbweichung`, `sAbweichung`
  - `RundeDaten.leitzinssatz`
  - `Spiel.nächsterZinssatz`, `erhaltePreisinflationZurRunde`, `erhalteZinssatzSchritte`
  - mehrere Eingabe-Composables mit `MutableFloatState`
- Rohstoffe sind `enum class Rohstoffe` mit `str` und Compose-`Color`.
- Rohstoffmengen sind aktuell `Int` bzw. `EnumMap<Rohstoffe, Int>`.

## Rundenwechsel

- Der aktuelle Ablauf ist rundenbasiert, aber nicht als Spielerzug-Zustandsautomat modelliert.
- `Spiel.aktuelleRunde` ist `runden.size`.
- `Spiel.neueRundenDatenDefinieren(...)` hängt eine neue Runde an und übergibt pro Runde:
  - Bau-/Kontrolldaten pro Spieler
  - Handelsdaten
  - Konfliktdaten
- `bearbeiteRunde(...)` sammelt UI-Eingaben für neue Rundendaten; die Anbindung an Persistenz ist noch nicht vollständig.
- Es gibt keinen expliziten aktiven Spieler, keine Phasen `Einnahmen/Ausgaben/Aktionen`, keine erledigten Schritte und keine Sperrlogik.

## Abhängigkeiten

Aktuelle App-Abhängigkeiten aus `app/build.gradle.kts`:

- Android/Material:
  - `com.google.android.material:material`
  - `androidx.appcompat:appcompat`
  - `androidx.core:core-ktx`
  - `androidx.fragment:fragment-ktx`
- Compose:
  - Compose BOM
  - Activity Compose
  - Material 3
  - Material 3 adaptive navigation suite
  - UI, UI graphics, UI tooling preview, UI viewbinding
  - Foundation layout
  - Navigation Compose
- Persistenz:
  - Room runtime
  - Room KTX
  - Room compiler via KSP
- Sonstiges:
  - AndroidX PDF Viewer Fragment
  - Vico Compose/View Charting
  - `com.cheonjaeung.compose.grid:grid`
- Tests:
  - JUnit 4
  - AndroidX JUnit
  - Espresso
  - Compose UI Test

Der Versionskatalog enthält jetzt vorbereitete Einträge für:

- `org.jetbrains.kotlin.jvm`
- `org.jetbrains.kotlin.plugin.serialization`
- `kotlinx-coroutines-core`
- `kotlinx-serialization-json`

## Technische Risiken für Etappe 1

- `Rohstoffe` hängt aktuell wegen `Color` an Compose; ein reines Domain-Modul muss Farbe/Visualisierung vom Rohstofftyp trennen.
- `Zahlungsmittel` ist nicht `Long`-basiert. Die Umstellung sollte mit Charakterisierungstests abgesichert werden.
- `Spiel`, `Spieler`, `Handelsregister` und `Kriegsregister` sind mutable und cachebasiert; Extraktion in immutable `GameState` ist keine reine Dateiverschiebung.
- `AnleiheDaten` speichert den ersten Erwerber nicht; beim Laden wird aktuell "Zentralbank" als Standard angenommen.
- Einige ViewModel-Aktionen sind Stubs (`vernichteSpiel`, Kriegsfunktionen) oder in der Navigation auskommentiert.
- `Rohstoffe` und `Bauteil` verwenden Umlaute in Bezeichnern; Umbenennungen sollten vermieden werden, solange kein konkreter Nutzen entsteht.
