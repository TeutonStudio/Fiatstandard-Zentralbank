# Legacy-Migration Android

Stand: 21. Juli 2026. Die Dateien liegen physisch unter
`apps/android/src/main/java/de/teutonstudio/zentralbank/legacy`; ihre alten
Package-Namen bleiben vorübergehend bestehen. Neue Regeln dürfen nicht davon
abhängen.

| Legacy-Typ/-Datei | Aktueller Pfad | Domain-Ersatz | Produktive Benutzer | Noch notwendige Migration | Löschkriterium |
| --- | --- | --- | --- | --- | --- |
| `Spiel` | `legacy/laufzeit.kt` | `SpielZustand` + `SpielSitzung` | `LegacySpielKoordinator`, Navigation, Finanz-/Handels-/Anleihenansichten und ältere Diagramme | Bereichsspezifische Read Models aus Domain-Zustand/Ereignissen bereitstellen und UI-Signaturen umstellen | Kein Produktionscode konstruiert, liest oder mutiert `Spiel` |
| `Spieler` | `legacy/laufzeit.kt` | Domain-`Spieler`, `SpielerId` | Handelsdialoge, Spieleransicht, Diagramme und Legacy-Persistenz | UI-Auswahl auf stabile IDs und Presentation-Modelle umstellen | Keine Signatur außerhalb `legacy` nutzt den Typ |
| `Rohstoffe` | `legacy/konstanten.kt` | `Rohstoff` | ältere Compose-Auswahl, Markt-/Diagrammformatierung und Mapper | Presentation-Auswahl direkt aus Domain-Enum erzeugen | Keine Referenz außerhalb Legacy-/Presentation-Mappern |
| `Bauteil` | `legacy/konstanten.kt` | `BauteilTyp` und Kartenbelegung | 3D-/Bauanzeige und Legacy-Auswertungen | Darstellung aus Domain-Belegung ableiten; Kosten nur noch aus Domain | Keine Fachrechnung oder UI-Aktion benötigt Legacy-Bauteile |
| `Zahlungsmittel` | `legacy/Zahlungsmittel.kt` | `Geld` | Handels-/Anleihendialoge, Finanzdiagramme und Charakterisierungstests | Rechenpfade auf `Geld`, Formatierung in Presentation | Keine Fachrechnung verwendet `Zahlungsmittel` |
| `Handelsregister` | `legacy/laufzeit.kt` | Handels-/Anleihenereignisse, Angebote, `marktpreisBeobachtungen` und `rundenwerte` | alte Markt-, Liquiditäts-, Schulden- und Verlaufsanzeigen | Historienprojektionen aus Ereignisverlauf ergänzen | Alle Registeransichten sind aus Domain-Verlauf ableitbar |
| `Kriegsregister` | `legacy/laufzeit.kt` | `SpielZustand.konflikte`, Kartenbelegung und Konfliktereignisse | ältere Konflikt- und Spieleranzeige | Anzeige vollständig auf Domain-Projektion umstellen | Kein Konfliktstatus wird doppelt gehalten |
| `FachmodellZuordnung.kt` | `legacy/FachmodellZuordnung.kt` | kein Laufzeitersatz; nur Import alter Daten | Legacy-Koordinator, Laden normalisierter Tabellen und Zuordnungstests | Auf einmaligen Importpfad reduzieren | Datei hat keine Laufzeitaufrufer mehr |

Weitere Übergangsdateien sind `LegacySpielKoordinator.kt`,
`LegacySpeicherZuordnung.kt`, `DatenbankHilfen.kt`, `TestSpiel.kt` und
`TestSpielKarte.kt`.

## Abgeschlossene vertikale Schnitte

- `GameViewModel` umfasst 98 Zeilen, initialisiert kein Room und greift weder auf
  DAO noch `AppDatabase` zu.
- Runden- und Spielerwechsel, Marktpreise, Leitzins, Angebotsablauf und neuer
  Prozug entstehen vollständig in Domain-Ereignissen. Der Produktivpfad ruft
  `Spiel.beginneNaechsteRunde()` nicht mehr auf.
- Prozug, Zugende, Krieg/Frieden und Kartenbau werden als `SpielAktion` an die
  gemeinsame Engine gegeben. Die Karten-UI liefert vorübergehend Ereignisformen,
  die der Koordinator vor der Zustandsänderung in Aktionen übersetzt.
- Room-Entitäten, DAOs, Migrationen und `RaumSpielAblage` liegen im Adaptermodul.
- Compose-Farben und Domain-Anzeigetexte liegen im Presentation-Bereich.

## Aktive Legacy-Schreibwege

- `LegacySpielKoordinator` hält neben `SpielSitzung` weiterhin ein altes `Spiel`,
  aktualisiert dessen aktiven Spieler und pflegt normalisierte Legacy-Tabellen.
- Der Android-Rohstoffhandel und die vorhandene Anleihen-UI erzeugen noch
  `SpielEreignis` und benutzen die ausdrücklich private Prüf-/Übernahmebrücke.
  Der Domain-Agentenpfad verwendet dagegen Handelsangebote und deterministische
  Anleihe-IDs.
- `KartenSpiel.kt` ruft `SpielRegelwerk.wendeAn` noch für Vorschau und
  Bauplanberechnung auf. Autoritative Übernahme läuft danach über Aktionen.
- `GameViewModel.aktuellesSpielOderNull` reicht das alte Modell an noch nicht
  migrierte Bildschirme weiter. Der fachliche StateFlow ist bereits
  `SpielZustand`, aber die Darstellung ist noch nicht vollständig umgestellt.
- Die normalisierten Tabellen werden zusätzlich zum maßgeblichen
  `FachSpielstand` geschrieben. Sie dürfen ohne getesteten Importpfad nicht
  entfernt werden.

## Risiken und konservative Entscheidungen

- Das alte `Spiel` bleibt produktiv benötigt; seine sofortige Entfernung würde
  Finanz-, Handels- und Diagrammbildschirme gleichzeitig neu schreiben.
- Room-Schema 4 hat keine separaten Spalten für Regelversion und Seed. Das
  Fachspielstandformat 3 serialisiert den aktuellen Domain-Zustand, ein späteres
  Schema muss die Metadaten explizit aufnehmen, bevor zufällige Android-Schritte
  eingeführt werden.
- Direkter bilateraler Android-Handel bildet noch nicht den neuen Angebotsdialog
  ab. Eine UI-Migration muss Angebot und Annahme als getrennte Interaktionen zeigen.

## Nächste drei ausführbare Schritte

1. Handels- und Anleihendialoge auf `Handelsangebot*` beziehungsweise
   `Anleihenangebot*` umstellen und danach `pruefeFachEreignis` sowie
   `uebernehmeFachEreignis` aus dem Koordinator entfernen.
2. Domain-/Application-Read-Models für Spieler-, Markt-, Außenhandels- und
   Anleihenregister ergänzen; diese Navigationen von `Spiel` auf die Read Models
   umstellen und `aktuellesSpielOderNull` entfernen.
3. Room-Schema 5 mit Regelversion/Seed und getesteter Migration 4→5 einführen;
   normalisierte Tabellen danach nur noch als einmalige Importquelle öffnen.
