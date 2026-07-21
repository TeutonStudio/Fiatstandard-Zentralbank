# Legacy-Migration Android

Alle folgenden Typen liegen physisch unter
`apps/android/src/main/java/de/teutonstudio/zentralbank/legacy`. Die alten
Package-Namen bleiben vorübergehend erhalten, damit die Verschiebung keine
unnötige, gleichzeitige UI-Neuschreibung erzwingt. Neue Fachlogik darf nicht auf
diese Typen zugreifen. Dateiheader kennzeichnen den Bereich als Übergangscode.

| Legacy-Typ/-Datei | Aktueller Pfad | Domain-Ersatz | Noch vorhandene Benutzer | Notwendige Migration | Löschkriterium |
| --- | --- | --- | --- | --- | --- |
| `Spiel` | `legacy/laufzeit.kt` | `SpielZustand` + `SpielSitzung` | `LegacySpielKoordinator`, alte UI-Auswertungen, Room-Kompatibilitätsmapper und Charakterisierungstests | Runden-, Finanz- und Verlaufsanzeigen aus Domain-Read-Models speisen | Kein Produktionscode konstruiert oder mutiert `Spiel` |
| `Spieler` | `legacy/laufzeit.kt` | `Spieler` und `SpielerId` in `core/domain` | Legacy-Handel, Bau, Diagramme und UI-Auswahl | UI nur noch stabile `SpielerId` plus Presentation-DTO geben | Keine Signatur außerhalb `legacy` verwendet den Legacy-Typ |
| `Rohstoffe` | `legacy/konstanten.kt` | `Rohstoff` | ältere Compose-Eingaben, Preis-/Diagrammhelfer, Legacy-Persistenz | Auswahl- und Anzeigezuordnungen auf Domain-Enum umstellen; Farben bleiben in `LegacyFarbZuordnung.kt` | Keine Legacy-Enum-Referenz außerhalb `legacy` und Presentation-Mappern |
| `Bauteil` einschließlich Untertypen | `legacy/konstanten.kt` | `BauteilTyp`, Karten- und Belegungsmodelle | Bau-UI, 3D-Zuordnung, Legacy-Rundenberechnung | Bauaktionen und UI-Read-Models auf `BauteilTyp`/Domain-Belegung umstellen | Bau und Darstellung benötigen keine Legacy-Kostenmodelle mehr |
| `Zahlungsmittel` | `legacy/Zahlungsmittel.kt` | `Geld` | Finanzdiagramme, Legacy-Handel, Anleihen-UI und Kompatibilitätstests | Rechenpfade und Formatierung auf `Geld`; Mark-Anzeige ausschließlich Presentation | Keine Fachrechnung nutzt `Zahlungsmittel` |
| `Handelsregister` | `legacy/laufzeit.kt` | `SpielZustand.handelsdaten`, `Anleihe`, Handelsereignisse und Auswertungen | Marktpreis-, Liquiditäts-, Schulden- und Rundencaches | fehlende Domain-Read-Models ergänzen und Runde über Aktionen/Ereignisse fortschreiben | Alle Handels-/Finanzanzeigen lassen sich aus Domain-Zustand rekonstruieren |
| `Kriegsregister` | `legacy/laufzeit.kt` | `Konflikt` und Konfliktereignisse | Konfliktanzeige und Legacy-Rundenabschluss | Waffenstillstand/Frieden als vollständige Aktionen plus Domain-Projektion abbilden | Kein Konfliktstatus wird doppelt in Legacy gehalten |
| `FachmodellZuordnung.kt` | `legacy/FachmodellZuordnung.kt` | keiner; direkte Domain-Nutzung | Laden alter Tabellen, Legacy-Koordinator und Zuordnungstests | je Benutzer auf Domain-Read-Model oder einmaligen Importkonverter umstellen | Datei hat keine Aufrufer und alte Tabellen können nur noch importiert werden |

Weitere Übergangsdateien sind `LegacySpielKoordinator.kt`,
`LegacySpeicherZuordnung.kt`, `DatenbankHilfen.kt`, `TestSpiel.kt` und
`TestSpielKarte.kt`.

## Bereits getrennt

- `GameViewModel.kt` enthält 95 Zeilen und weder DAO- noch
  `AppDatabase`-Zugriffe.
- Room-Entitäten, DAOs, Migrationen und `RaumSpielAblage` liegen vollständig in
  `adapters/persistence-room`.
- Compose-`Color`-Zuordnungen liegen in `schnittstelle/LegacyFarbZuordnung.kt`;
  Domain-Anzeigetexte liegen in `schnittstelle/DomainAnzeigeTexte.kt`.
- Zentrale Prozug-, Zugende-, Warenkorb-, Rohstoffhandels- und
  Krieg/Frieden-Abläufe schicken `SpielAktion` an die gemeinsame Engine.

## Noch offene Risiken

- `LegacySpielKoordinator` ist mit knapp 1.000 Zeilen weiterhin groß. Er ist aus
  dem Lifecycle-ViewModel herausgelöst, enthält aber noch Bau-, Anleihen- und
  Rundenübergänge sowie UI-Callbacks.
- `KartenSpiel.kt` verwendet `SpielRegelwerk` noch für Bauvorschau und
  Planvalidierung. Diese nicht autoritativen UI-Prüfungen müssen mit den
  kommenden Bauaktionen auf `SpielEngine.pruefe` umgestellt werden.
- Beim Speichern nach Room werden der Fachverlauf und seine Formatversion
  erhalten; `engineVersion` und `seed` besitzen in der unverändert gehaltenen
  Datenbank v4 noch keine eigenen Spalten. Ein künftiges Schema v5 muss diese
  ergänzen, bevor Android zufallsabhängige Engine-Schritte persistiert.
- Die Legacy-Tabellen werden parallel zum maßgeblichen `FachSpielstand`
  gepflegt. Sie dürfen erst nach einer getesteten Import-/Upgrade-Migration
  entfernt werden.

## Nächste drei ausführbare Schritte

1. Bau- und Anleihenabsichten als `SpielAktion` ergänzen, gegen das bestehende
   Regelwerk testen und die entsprechenden Ereignisbrücken im
   `LegacySpielKoordinator` entfernen.
2. Domain-Read-Models für Runden-, Markt-, Liquiditäts- und Schuldenanzeigen in
   `core/application` ergänzen und die Compose-Aufrufer direkt darauf umstellen.
3. Room-Schema v5 mit `engineVersion`/`seed`, Migration 4→5 und einem getesteten
   einmaligen Importpfad für die normalisierten Legacy-Tabellen einführen.
