# Umbau-Log

## 17.07.2026 - Architektur neu bewertet und Ziel festgelegt

Durchgeführte Änderungen:

- Veraltete Ist-Dokumentation an den tatsächlich vorhandenen Stand mit den
  Modulen `:app` und `:domain` angepasst.
- `GameViewModel`, Legacy-`Spiel`, `GameState`, Ereignisse, Reducer,
  `GameEngine`, Domain-Zuordnung, Navigation, Marktplatz, Anleihen sowie
  Room-Entitäten und DAO-Fassade untersucht.
- `docs/ARCHITEKTUR_ZIEL.md` mit Zielpaketen, Verantwortlichkeiten,
  Abhängigkeitsrichtung, Benennungen und Migrationsreihenfolge angelegt.

Architekturentscheidung:

- Der Gradle-Modulname `:domain` bleibt vorerst bestehen; fachlich und in der
  künftigen Paketwurzel heißt das Modul `fachlogik`.
- `Geld` bleibt als präziser Long-basierter Fachtyp erhalten.
- `GameViewModel` wird nicht vorzeitig nur kosmetisch umbenannt. Eine
  `SpielSitzung` entsteht erst zusammen mit einer echten `SpielAblage`.
- Alte Room-Stände sollen später als importierter Startzustand behandelt
  werden; ein nicht vorhandener Ereignisverlauf wird nicht rekonstruiert oder
  erfunden.

Entfernte Altstruktur:

- In dieser Dokumentationsetappe wurde noch kein produktiver Code entfernt.

Verbleibende Übergangslösung:

- Legacy-`Spiel`, `GameState` und Room-Daten sind noch parallele Darstellungen.
- Die Marktplatz- und Anleihenoberfläche benötigt historische Daten aus dem
  Legacy-Modell.

Ausgeführte Tests:

- `./gradlew test` erfolgreich.
- `./gradlew assembleDebug` erfolgreich.

Offene Probleme:

- `GameEngine` faltet den gesamten Verlauf bei jedem Zustandszugriff.
- `Reducer` bündelt alle Regelbereiche in einer Datei.
- Die konkrete Persistenzmigration zu `SpielAblage` benötigt ein versioniertes,
  nicht-destruktives Room-Schema.
- `GameViewModel.vernichteSpiel` ist ein produktiv verdrahteter `TODO()`;
  Konfliktmethoden sind produktiv verdrahtete leere Methoden.

## Etappe 0 - Bestandsaufnahme & Absicherung

- Architektur-Ist dokumentiert: Screens/Composables, Zustandshaltung, Persistenz, Geld-/Rohstofftypen, Rundenwechsel und Abhängigkeiten.
- Charakterisierungstests für zentrale Geldoperationen und Testspiel-Basisdaten ergänzt.
- Versionskatalog für die kommenden Module um Kotlin/JVM, Serialization und Coroutines vorbereitet.
- `./gradlew build` wird als Abschlussprüfung der Etappe verwendet.

Offene Punkte:

- Kein Regelfragen-Blocker für Etappe 0.
- Für Etappe 1 muss `Rohstoffe` von Compose-`Color` entkoppelt werden, bevor der Typ in ein reines Kotlin-Modul wandern kann.
- `Zahlungsmittel` muss in späteren Etappen auf `Long` in kleinster Einheit migriert werden; die aktuellen Tests halten nur das bestehende Verhalten fest.

## Etappe 1 - Domain-Modul und Geldbasis

- Neues reines Kotlin/JVM-Modul `:domain` angelegt.
- `:app` hängt von `:domain` ab; `:domain` hat keine Android- oder Compose-Abhängigkeit.
- Serialisierbaren Geldtyp `Geld` eingeführt: kleinste gespeicherte und berechnete Einheit ist Cent, also `100L == 1 Mark`.
- Erste serialisierbare Domain-Typen ergänzt: `SpielerId`, `KontoId`, `Rohstoff`, `RohstoffMenge`, `Spieler`, `Anleihe`, `GameState`.
- Domain-Tests für Cent-basierte Geldrechnung ergänzt.
- Android-freie Bauteiltypen (`BauteilTyp`, `BauteilArt`) mit Kosten, Ertrag und Verbrauch ergänzt.
- App-seitigen Adapter vom bestehenden `Spiel` in den neuen `GameState` ergänzt, damit die neue Domain-Struktur gegen reale Bestandsdaten getestet werden kann.
- Adaptertests für Geld-, Marktpreis-, Spieler- und Bauteilabbildung ergänzt.
- `GameState` um Warenkorb und Leitzins in Basispunkten erweitert.
- JSON-Rundlauf-Test für `GameState` ergänzt, damit das Domain-Modell als spätere Event-Log-/Savegame-Basis serialisierbar bleibt.
- `GameViewModel` exponiert zusätzlich `domainState: StateFlow<GameState?>`; bestehendes `aktuellesSpiel` und neuer Domain-State werden beim Erstellen/Laden gemeinsam aktualisiert.

Offene Punkte:

- Bestehende App-Modelle verwenden noch `Zahlungsmittel`; die Migration auf `Geld` erfolgt schrittweise.
- Bestehende App-UI nutzt weiterhin `Rohstoffe` mit Compose-`Color`; die Domain nutzt bereits Android-freie `Rohstoff`- und `BauteilTyp`-Typen.

## Etappe 2 - Events, Reducer und Engine

- `GameEvent` als serialisierbares sealed Interface angelegt, inklusive Rohstoffbuchungen, Geldtransaktion, Rohstoffhandel, Anleihe-, Expansion-, Kriegs- und Zugereignissen.
- Erste Reducer-Scheibe implementiert:
  - `RohstoffEinnahme`
  - `RohstoffAusgabe`
  - `Transaktion`
  - `RohstoffHandel`
- Reducer lehnt negative Rohstoffbestände, Unterdeckung und nicht-positive Beträge/Mengen ab.
- Summenneutralität von `Transaktion` und `RohstoffHandel` durch Tests verankert.
- `GameEngine` mit Event-Log, `apply`, `undo` und `redo` ergänzt.
- `GameState` um `bankAnleihen` erweitert, damit Anleihenbesitz bei Bank/Nicht-Spieler-Konten abbildbar ist.
- Reducer-Logik für `AnleiheGekauft`, `AnleiheVerkauft` und `AnleiheFaellig` ergänzt.
- Tests verankern summenneutralen Anleihenverkauf und Entfernung fälliger Anleihen aus Portfolios und offenem Bestand.
- `GameState` um einfache `Konflikt`-Menge erweitert.
- Reducer-Logik für `Expansion`, `KriegErklaert` und `KriegBeendet` ergänzt.
- Tests verankern Rohstoffverbrauch bei Expansion und Kriegserklärung/-ende.

Offene Punkte:

- Zugphasen-Events sind definiert, aber noch nicht reducer-seitig implementiert.
- Aktive-Spieler-/Phasenprüfung folgt mit dem Zustandsautomaten in Etappe 3.

## Etappe 3 - Zugautomat

- Domain-Typen für `SchrittTyp`, `Phase`, `ZugStatus`, `SchrittZustand` und `SchrittInfo` ergänzt.
- `GameState` trägt nun optionalen `zugStatus`, standardmäßig für den aktiven Spieler in der Einnahmen-Phase.
- `ZugAutomat.schritte`, `kannPhaseAbschliessen`, `kannZugBeenden` und Phasenübergang ergänzt.
- Reducer verarbeitet `SchrittAbgeschlossen`, `PhaseAbgeschlossen` und `ZugBeendet`.
- Tests verankern verfügbare/gesperrte Schritte, Pflichtschritt-Gates und Spielerwechsel beim Zugende.
- Fachliche Events werden gegen `ZugAutomat.schritte()` gegated und bei phasenfremder Nutzung abgelehnt.
- Reducer prüft fuer gegatete Events den aktiven Spieler anhand des primaeren Event-Spielers.
- Situativ entfallende Pflichtschritte umgesetzt:
  - `ROHSTOFF_EINNAHMEN` bleibt immer manuell, da der Ertrag vom physischen Spielfeld kommt.
  - `ROHSTOFF_AUSGABEN` ist automatisch erledigt, wenn der aktive Spieler keine verbrauchenden Bauteile/Regionen hat.
  - `FINANZ_AUSGABEN` ist automatisch erledigt, wenn keine eigene Anleihe bei Bank oder anderen Spielern liegt.
- `Schuldenstrich` als Finanz-Ausgaben-Event ergänzt.
- Schuldenstrich baut normale Bahnhöfe/Häfen ab, stuft Großbahnhöfe/Großhäfen zu normalen Gebäuden zurück und entfernt die vom Nutzer angegebene Anzahl Eisenbahnlinien.
- Bankgehaltene eigene Anleihen werden gelöscht; von anderen Spielern gehaltene eigene Anleihen werden durch frisches Geld zum Nennwert ausgezahlt und anschließend gelöscht.
- Nach einem Schuldenstrich wird der aktuelle Zug direkt zum nächsten Spieler weitergeschaltet.
- Reducer-Tests verankern Auszahlung, Bauteilabbau, Anleihenlöschung, Zugübersprung und Ablehnung im Krieg.
- `UeberschuldungsStatus` ergänzt und am Ende des eigenen Zuges aktualisiert.
- Überschuldung zählt nur bankgehaltene eigene Anleihen, inklusive Nennwert und aller Laufzeit-Zinsen.
- Marktwert wird aus Einheiten/Bauwerken über vorhandene Marktpreise berechnet; Rohstoffe ohne Preis gehen mit 0 ein.
- Friedliche Überschuldungsserien werden bei aktiver Kriegsbeteiligung unterbrochen, ab dem dritten betroffenen Zug gewarnt und nach mehr als drei betroffenen Zügen als schuldenstrichfällig markiert.
- Reducer-Tests verankern Warn-/Fälligkeitsstatus, Bankanleihen-Filter und Kriegsunterbrechung.
- Ein automatisch fälliger Schuldenstrich hält den Spieler am Zugende in der Aktionsphase und blockiert weitere Events, bis der Schuldenstrich mit der im Dialog eingegebenen Bahnweg-Anzahl gebucht wurde.
- Reducer-Tests verankern den blockierenden Eingabezustand und die anschließende automatische Zugweitergabe nach dem Schuldenstrich.

Offene Punkte:

- Kein Regelfragen-Blocker für Etappe 3.

## Etappe 4 - ViewModel/MVI-Anbindung

- `GameViewModel` initialisiert beim Laden/Erstellen eines Spiels eine `GameEngine` aus dem gemappten `GameState`.
- `GameViewModel.onEvent(GameEvent)` als zentrale Domain-Event-Einstiegsstelle ergänzt.
- Erfolgreiche Domain-Events aktualisieren `domainState`; abgelehnte Events werden über `domainFehler` als `SharedFlow<String>` gemeldet.
- `Navigation` sammelt `domainFehler` und zeigt Reducer-/Domain-Fehler über einen globalen Material-`SnackbarHost`.
- Das Spielmenü liest den aktuellen Spieler und die Phase aus `domainState` und zeigt sie im bestehenden unteren Textfeld an.
- App-seitiger `ZugAnzeige`-Mapper ergänzt, damit Domain-State-Formatierung nicht direkt in `Navigation` liegt; Test für Spieler-/Phasenanzeige ergänzt.
- App-seitiger `SpielerAnzeige`-Mapper ergänzt, der Domain-Spieler für künftige state-hoisted Spielerlisten verdichtet; Test für Geld-, Anleihe- und Bauteilaggregation ergänzt.
- `GameUiState` fasst abgeleitete Zug- und Spieleranzeigen zusammen; `GameViewModel` exponiert ihn als `domainUiState` parallel zum rohen `domainState`.
- Bestehende `aktuellesSpiel`-UI bleibt unverändert lauffähig, während Screens schrittweise auf `domainState` und Events umgehängt werden.

Offene Punkte:

- Bestehende Composables lesen größtenteils noch aus `aktuellesSpiel`; das Umhängen auf State-Hoisting und `onEvent` folgt schrittweise.

## Zug- und Rundenübergang

- Der zentrale Menüknopf schließt Einnahmen und Ausgaben ab und beendet anschließend den Zug des aktiven Spielers.
- Nach einem Zug wird in fester Reihenfolge zum nächsten Spieler gewechselt; erst nach dem letzten Spieler steigt der Rundenzähler und der erste Spieler beginnt wieder.
- Der alte Rundenwechsel-Dialog mit manueller Spieler-, Runden- und Leitzinswahl wurde entfernt.
- Eine vollständige Runde wird automatisch als `RundeDaten` gespeichert.
- Der Leitzins der neuen Runde wird aus der Warenkorbpreisinflation bestimmt: oberhalb des Zielbereichs steigt er, unterhalb sinkt er; ohne belastbaren vorherigen Warenkorbpreis bleibt er unverändert.
- Die Spielanzeige nennt aktuelle Runde, aktuellen Leitzins, aktiven Spieler und die als Nächstes abschließbare Phase.

## Ablaufdarstellungen und Zinsgewinne

- Der Anleihenablauf verwendet die Spalten Runde, Zahlungsempfänger und Betrag. Bei Emissionen erhält der Herausgeber das Geld, bei Handel der bisherige Halter und bei Zins/Rückzahlung der in der jeweiligen Runde aktuelle Halter.
- Der Spielerablauf zeigt Rohstoffhandel mit Runde, Geschäftspartner, Rohstoff einschließlich Anzahl und vorzeichenbehaftetem Preis.
- Anleihekäufe, Anleiheverkäufe und erhaltene Zinszahlungen erscheinen als eigene Ablaufzeilen.
- Ein Tipp auf eine Rundenzelle klappt alle sichtbaren Runden gemeinsam ein oder wieder aus; die kompakten Gruppenzeilen bleiben zum Aufklappen sichtbar.
- In der globalen Schuldenansicht ersetzt die kumulierte Kurve `Zinsgewinne` die globalen Zinsschulden. Sie zählt nur Zinszahlungen an die Geschäftsbank; Zahlungen an andere Spieler sind kein Bankgewinn.

## Zugausgaben und Anleihenhandel

- Die globale Bilanz zeigt nur noch eine Schuldenkurve. `Globale Schulden` verwendet Nennwerte plus verbleibende kumulierte Zinsverbindlichkeiten aller Spieler.
- `Globales Barvermögen` summiert ausschließlich die Barbestände der Spieler und trägt keinen M2-Zusatz mehr.
- Anleihen-Cards behalten ihre feste Größe; der Ablauf scrollt in einem separaten Dialog.
- Beim Eintritt in die Ausgabenphase erscheint ein Plan der fälligen Zins-/Rückzahlungen und des Rohstoffverbrauchs je Gebäude. Schließen wechselt automatisch in die Aktionsphase.
- Der aktive Spieler ist beim Anleihedialog fest vorgegeben. Über denselben Auswahlknopf sind Neuemissionen, Verkäufe eigener Bestände und Rückkäufe selbst emittierter Anleihen möglich.
- Folgegeschäfte einer Anleihe werden am bestehenden Datensatz gespeichert; nur der aktuelle Besitzer darf verkaufen.
- Die Bedienungsanleitung ist nur noch über den Floating-Button im Spielmenü erreichbar.
- Im Anleihenablauf richtet sich die Farbe jeder Zeile ausschließlich nach ihrer Runde: vergangene Runden sind vergangen, die ausgewählte aktuelle Runde ist fällig und zukünftige Runden sind offen. Dies gilt auch für eingeklappte Rundengruppen.
- Anleihen- und Spielerabläufe ersetzen nicht mehr die zweite Cardseite, sondern öffnen in einem eigenen scrollbaren Dialog mit Schließen-„×“ rechts oben. Die Cards zeigen dauerhaft ihre normale Vorderseite.
- Der Spielerablauf ist absteigend nach Runde sortiert und auf Runde, Geschäftspartner, Rohstoff/Vorgang sowie Preis verdichtet. Partner und Rohstoff/Vorgang sind über die Kopfzeile filterbar, Rohstoffmengen stehen direkt beim Rohstoff und eingeklappte Runden zeigen ihren gefilterten Saldo.
- Der Anleihenablauf verwendet ebenfalls ein schmaleres Dialogfenster und zeigt zukünftige Runden zuerst. Ein Schalter wechselt die Empfängerspalte zum Buchungssatz „Zahlungspflichtiger an Zahlungsempfänger“; der Ablauf enthält stets alle geplanten Zahlungen bis zur Tilgung.
- Eingeklappte Ablaufzeilen sind kompakter. `minRunde` startet mit 0 und zeigt dann keine Saldozeile. Bei einer positiven Untergrenze ersetzt genau eine kumulierte Saldozeile die Einzelbuchungen dieser Runde; eine Untergrenze oberhalb aller Daten lässt die Tabelle leer.
- Eine Anleihelaufzeit umfasst die Zinsrunden nach der Emission; die Tilgung folgt in einer eigenen Runde. Bernds in Runde 2 emittierte Fünf-Runden-Anleihe wird deshalb in Runde 8 getilgt. Handel nach der Fälligkeit wird abgelehnt.
- Der Handelsdialog verwendet eine kategorisierte Handelsgut-Auswahl für Rohstoffe und die vom gewählten Verkäufer gehaltenen, noch handelbaren Anleihen. Letzter Marktpreis und relative Vertragsabweichung werden mit Vorzeichenfarbe angezeigt.
- Der Anleihedialog zeigt den aus Zinsbetrag und Nennwert berechneten Prozentzins sowie die relative Abweichung zum Leitzins; positive, negative und neutrale Abweichungen erscheinen grün, rot beziehungsweise grau.
- Der Marktplatz ergänzt den Graph-Tab `Handelsdifferenz`. Er zeigt je auswählbarem Rohstoff die kumulierte Stückdifferenz eines exklusiv gewählten Spielers als vertikale Balken; auch die übrigen Stückdiagramme verwenden Balken statt Kurven.
- Der bei Spielbeginn festgelegte Preisinflationswarenkorb bleibt von späteren Änderungen des normalen Warenkorbs unberührt und erscheint als zweite Kurve im bestehenden Warenkorb-Graphen.
- Die Bauwerk-Legende ist in Land, Ecken und Linien gegliedert. In der Spielmenü-Karte mit Runde und Leitzins zeigen kompakte Spielerkarten den aktiven Spieler und die verbleibenden Züge bis zur nächsten Marktpreisberechnung.
- Spielgebundene Navigationsziele prüfen den geladenen Spielzustand. Stellt Android nach einem Prozessneustart eine Spielroute ohne initialisiertes Spiel wieder her, führt die Navigation sicher ins Hauptmenü zurück, statt auf ein uninitialisiertes `lateinit`-Feld zuzugreifen.
- Die Schuldenbilanz besitzt links in der Legendenzeile einen stufenlosen Y-Achsenregler. Der linke Anschlag ist linear; weiter rechts wächst die Achse zunehmend exponentiell, während ihre Beschriftung weiterhin die tatsächlichen Markbeträge zeigt.
- Bei mehreren Anleihenereignissen in derselben Runde steht die Zinszahlung im absteigend dargestellten Ablauf über dem zeitlich vorherigen Anleihehandel.
- Sämtliche vertikalen Stückachsen verwenden ganzzahlige Tick-Abstände und zeigen ausschließlich ganzzahlige `Stk`-Beschriftungen.
