# Architekturziel

Stand: 17.07.2026.

## Ziel und Leitentscheidung

Für eine laufende Partie ist `SpielZustand` die einzige fachlich maßgebliche
Darstellung. Er ist unveränderlich, Android-frei und wird ausschließlich durch
`SpielEreignis` über das `SpielRegelwerk` fortgeschrieben.

Das vorhandene Gradle-Modul behält zunächst den technischen Namen `:domain`.
Innerhalb des Moduls lautet die fachliche Paketwurzel
`de.teutonstudio.zentralbank.fachlogik`. In Dokumentation und Gesprächen sind
`:domain` und `fachlogik` daher bis zu einer gesonderten, vollständig geprüften
Gradle-Migration dasselbe Modul.

## Benennungsentscheidungen

| Bisher | Ziel | Entscheidung |
| --- | --- | --- |
| `GameState` | `SpielZustand` | maßgeblicher unveränderlicher Zustand |
| `GameEvent` | `SpielEreignis` | einzige Eintrittsstelle für Fachänderungen |
| `GameEngine` | `SpielAblauf` | hält Zustand und Ereignisverlauf |
| `Reducer` | `SpielRegelwerk` | koordiniert fachliche Teilregelwerke |
| `GameViewModel` | zunächst Legacy-Name, später `SpielSitzungViewModel` | keine Umbenennung, solange die Klasse noch Legacy-Aufgaben bündelt |
| `GameUiState` | fachlich konkreter Zustand | kein pauschales `UiState`; je Bereich benennen |
| `Geld` | `Geld` | bereits präzise, `Long`-basiert und kürzer als `GeldBetrag` |
| `ZugAutomat` | `ZugAuswertung` und `ZugRegelwerk` | Lesen/Entscheiden wird von Zustandsänderungen getrennt |
| `ZentralbankSpeicher` | Übergangsfassade | nur noch für nicht migrierte Legacy-Schreibwege; fachliche Persistenz liegt in `RaumSpielAblage` |

Die Begriffe `Reduzierer`, `Geschäftslogik`, `Manager`, `Helper`, `Utils` und
vergleichbar unspezifische Sammelnamen werden nicht verwendet.

## Zielstruktur

Es werden nur Dateien angelegt, die eine aktuelle Verantwortung tragen.

```text
:domain  (fachlich: fachlogik)
└── de.teutonstudio.zentralbank.fachlogik
    ├── modell
    │   ├── SpielZustand.kt
    │   ├── Spielmodell.kt
    │   ├── Geld.kt
    │   ├── Finanzen.kt
    │   ├── Bauteile.kt
    │   ├── Konflikte.kt
    │   ├── Schuldenstrich.kt
    │   ├── Ueberschuldung.kt
    │   └── ZugZustand.kt
    ├── ereignis
    │   └── SpielEreignis.kt
    ├── regelwerk
    │   ├── SpielRegelwerk.kt
    │   ├── ZugRegelwerk.kt
    │   ├── RohstoffRegelwerk.kt
    │   ├── FinanzRegelwerk.kt
    │   ├── AnleihenRegelwerk.kt
    │   ├── HandelsRegelwerk.kt
    │   ├── ExpansionsRegelwerk.kt
    │   ├── KonfliktRegelwerk.kt
    │   └── InsolvenzRegelwerk.kt
    ├── ablauf
    │   ├── SpielAblauf.kt
    │   └── EreignisVerlauf.kt
    ├── auswertung
    │   ├── ZugAuswertung.kt
    │   ├── MarktAuswertung.kt
    │   └── AnleihenAuswertung.kt
    └── schnittstelle
        └── SpielAblage.kt
```

`ExpansionsRegelwerk` wird zusätzlich zum vorgegebenen Rahmen verwendet, weil
Expansion bereits ein eigenes Ereignis mit eigener Rohstoff- und Bauteilregel
ist. Sie im Rohstoff- oder allgemeinen Spielregelwerk zu verstecken wäre
fachlich unpräziser.

```text
:app
└── de.teutonstudio.zentralbank
    ├── daten
    │   ├── raumdatenbank
    │   │   ├── entitaet
    │   │   ├── zugriff
    │   │   └── ZentralbankDatenbank.kt
    │   ├── zuordnung
    │   │   ├── SpielstandZuordnung.kt
    │   │   └── FachmodellZuordnung.kt
    │   └── RaumSpielAblage.kt
    ├── spielsitzung
    │   ├── SpielSitzung.kt
    │   ├── SpielSitzungsZustand.kt
    │   ├── SpielMeldung.kt
    │   └── SpielSitzungsFabrik.kt
    ├── bereich
    │   ├── start
    │   ├── spiel
    │   ├── spielzug
    │   ├── marktplatz
    │   ├── anleihen
    │   ├── spieler
    │   ├── aussenhandel
    │   ├── expansion
    │   └── konflikt
    ├── navigation
    └── oberflaeche
        ├── baustein
        ├── eingabe
        ├── formatierung
        └── gestaltung
```

## Verantwortlichkeiten

### `SpielZustand`

- enthält den vollständigen fachlichen Stand einer laufenden Partie,
- verwendet nur `val` und unveränderliche Collection-Schnittstellen,
- enthält weder Android-/Compose-Typen noch Room-Entitäten,
- enthält Geld ausschließlich als `Geld` und Zinsen als `Basispunkte`,
- führt keine Zustandsänderung selbst aus.

Historische Daten, die Marktplatz und Anleihen benötigen, müssen vor deren
Migration als fachliche Historie oder aus dem Ereignisverlauf auswertbar sein.

### `SpielRegelwerk` und Teilregelwerke

`SpielRegelwerk` prüft die allgemeine Zugfreigabe und delegiert genau einmal an
das zuständige Teilregelwerk. Teilregelwerke sind zustandslos und bearbeiten nur
ihre eigenen Ereignisse. Gemeinsame Operationen erhalten fachliche Namen wie
`kontoAendern`, `rohstoffeBuchen` oder `spielerAendern` und bleiben intern.

### `SpielAblauf`

`SpielAblauf` besitzt:

- Startzustand,
- zwischengespeicherten aktuellen Zustand,
- angewandte Ereignisse,
- zurückgenommene Ereignisse.

Ein neues Ereignis berechnet nur den Folgezustand. Vollständiges Falten ist auf
Initialisierung mit Verlauf, Rückgängig, Wiederholen und explizite
Integritätsprüfung begrenzt. UI-Abfragen geben lediglich den Cache zurück.

### `SpielAblage`

Die Schnittstelle liegt im Fachmodul und kennt nur `SpielstandUebersicht`,
`GespeichertesSpiel`, `SpielZustand` und `SpielEreignis`. `RaumSpielAblage` liegt
in `:app`, koordiniert alle DAOs und führt Room-Entitäten über benannte
Zuordnungen in das Fachmodell über.

Die Legacy-Tabellenstruktur kann nicht verlustfrei in einen vollständigen
Ereignisverlauf zurückübersetzt werden. Die umgesetzte Kompatibilitätsstrategie
liest alte Tabellenstände als gekennzeichneten Startzustand; neue Speicherungen
persistieren Startzustand plus Verlauf. `FachSpielstand` wurde mit der
nicht-destruktiven Room-Migration 1→2 eingeführt.

### `SpielSitzung`

Die Sitzung orchestriert Ablauf und Ablage und bietet `StateFlow<SpielZustand>`
sowie einen Meldungs-Flow. Sie lädt, speichert, wendet Ereignisse an und bietet
Rückgängig/Wiederholen. Sie enthält keine Navigation und keinen lokalen
Bildschirmzustand.

Solange `GameViewModel` noch Room-Rekonstruktion und Legacy-Mutationen enthält,
wird es nicht nur kosmetisch umbenannt. Zuerst wird eine echte `SpielSitzung`
extrahiert; anschließend kann eine Android-ViewModel-Fassade sie besitzen oder
implementieren.

### Bereichs-ViewModels und Compose

Jeder migrierte Bereich erhält einen konkreten Zustand und konkrete Aktionen.
Die Route sammelt Flows und reicht Zustand/Callbacks an einen darstellenden
Bildschirm weiter. Bildschirm-Composables erhalten nie das vollständige
Legacy-`Spiel`.

Darstellungszustand wie ausgewählte Diagrammreihe, Textfelder und Dialogsicht-
barkeit bleibt im Bereich. Preise, Renditen, Handelbarkeit und Bestandsänderung
liegen in Auswertungen beziehungsweise Regelwerken.

## Abhängigkeitsrichtung

```text
Compose-Bildschirm
    ↓
Bereichs-ViewModel
    ↓
SpielSitzung
    ↓
SpielAblauf
    ↓
SpielRegelwerk
    ↓
SpielZustand
```

```text
SpielSitzung → SpielAblage ← RaumSpielAblage → Room-DAO
```

Das Fachmodul importiert niemals Android, Compose, Room oder App-Klassen.
Room-Entitäten verlassen den Datenbereich nicht. Navigation kennt Routen und
Bereichseinstiege, aber keine DAOs und keine Fachregeln.

## Migrationsreihenfolge

1. Ist- und Zielarchitektur auf den tatsächlichen Stand bringen.
2. Paketwurzel und zentrale Fachtypen im bestehenden `:domain` deutsch
   benennen; alle Aufrufer und Tests kontrolliert umstellen.
3. `SpielAblauf` auf gecachten Zustand umstellen und den Reducer in
   Teilregelwerke zerlegen.
4. Persistenzformat und nicht-destruktive Room-Migration festlegen;
   `SpielAblage`, `RaumSpielAblage` und Zuordnung gemeinsam integrieren.
   **Abgeschlossen am 17.07.2026.**
5. `SpielSitzung` extrahieren und Eventverlauf beim Laden/Speichern erhalten.
6. Marktplatz-Historie im `SpielZustand` beziehungsweise durch fachliche
   Auswertungen abbilden.
7. Marktplatz vollständig als Route, Bildschirm und Bereichs-ViewModel
   migrieren.
8. Danach jeweils einen vollständigen vertikalen Schnitt: Anleihen, Spielzug,
   Spieler, Außenhandel, Expansion, Konflikt.
9. Erst nach dem letzten produktiven Aufrufer das Legacy-`Spiel` und die
   Synchronisationsadapter entfernen.

## Übergangslösungen

- Das Gradle-Modul heißt vorerst `:domain`; nur seine Paketstruktur wird
  fachlich deutsch.
- Das alte `datenbank.Spiel` wird in Code und Dokumentation als Legacy-
  Übergangsmodell behandelt.
- Die App-seitigen Fachmodell- und Legacy-Zuordnungen bleiben bis zur Ablösung
  aller Legacy-Aufrufer erhalten.
- Bestehende Navigation und UI bleiben zunächst unverändert, greifen aber nach
  jeder Bereichsmigration nur noch auf den jeweiligen Bereichszustand zu.
- Alte Room-Spielstände werden nicht rückwirkend als erfundener Ereignisverlauf
  dargestellt, sondern als importierter Startzustand gekennzeichnet.

## Qualitätsregeln und Tests

- Jede Zustandsänderung hat mindestens einen Erfolgs- oder Ablehnungstest.
- Geldübertragungen prüfen die unveränderte Gesamtsumme.
- `SpielAblauf` prüft Cache, Laden mit Verlauf, Rückgängig und Wiederholen.
- `SpielAblage` wird gegen einen In-Memory-Testadapter und die Room-Zuordnung
  getestet.
- Auswertungen für Marktplatz und Anleihen sind reine JVM-Tests.
- Nach jeder abgeschlossenen Etappe laufen mindestens `./gradlew test` und
  `./gradlew assembleDebug`.
- Es wird kein absichtlich nicht kompilierender Zwischenstand committed.

## Risiken

- Die Legacy-Room-Tabellen enthalten nicht alle Informationen eines künftigen
  Ereignisverlaufs.
- Historische Diagramme hängen an Caches des Legacy-Modells; ihre Semantik muss
  durch Charakterisierungstests gesichert werden, bevor sie verschoben wird.
- Anleihen besitzen ein altes und ein neues Speicherformat.
- Künftige Room-Schemaänderungen müssen die bestehende nicht-destruktive
  Migrationskette ab Version 1 fortsetzen.
- Gleichzeitige Legacy- und Domain-Mutationen können beim Fehler zwischen zwei
  Schritten auseinanderlaufen; daher muss jeder vertikale Schnitt beide Wege
  vollständig ersetzen.
- Eine bloße Umbenennung des `GameViewModel` würde Verantwortung verschleiern,
  aber nicht reduzieren.
