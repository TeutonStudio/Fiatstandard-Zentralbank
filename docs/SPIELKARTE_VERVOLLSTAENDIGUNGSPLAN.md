# Plan zur Vervollständigung der Spielkarte

Stand: 17.07.2026

Umsetzungsstand: Die sechs Etappen sind implementiert. Die automatisierten Domain-
und App-Tests sowie Debug- und Release-Builds prüfen den Stand; die instrumentierten
Tests sind kompilierbar und müssen für eine Geräteprüfung auf einem Android-Gerät
oder Emulator ausgeführt werden.

## Zielbild

Die Spielkarte erhält zwei klar getrennte Arbeitsweisen:

1. **Bauen** bearbeitet ausschließlich die dauerhafte Kartengrundlage. Dabei werden
   Dreiecksfelder als Wasser oder als ein bestimmter Geländetyp angelegt, geändert
   und entfernt.
2. **Spielen** verändert ausschließlich die Belegung einer laufenden Partie. Dabei
   wird festgelegt, was auf einer Ecke, einer Kante oder einem Feld steht. Das
   Gelände kann in diesem Modus nicht verändert werden.

Der Modus ist ein Zustand der Oberfläche und wird nicht als fachlicher Zustand der
Partie gespeichert. Fachlich werden eine wiederverwendbare Kartenvorlage und die
veränderliche Belegung einer konkreten Partie unterschieden.

```text
Kartenvorlage
└── Dreiecksfelder: Wasser oder Gelände

Laufende Spielkarte
├── unveränderte Kartenvorlage
└── Belegung
    ├── Ecken
    ├── Kanten
    └── Felder
```

## Grundlage aus der Bedienungsanleitung

Maßgeblich sind insbesondere die Kapitel „Spielfeld und Aufbau der Welt“,
„Gebäude, Infrastruktur und Einheiten“, „Eigentum, Kontrolle und neutrale
Anlagen“, „Spielvorbereitung und Beginn des Spiels“ sowie „Krieg“ aus
[`docs/handbuch.tex`](handbuch.tex).

Aus der Anleitung ergeben sich folgende feste Anforderungen:

- Das Dreieck ist die Grundeinheit. Es gibt keine aus sechs Dreiecken gebildeten
  Hexfelder.
- Jedes Dreieck ist Wasser oder Gelände. Nur Geländefelder können einen
  Rohstoffbezug und eine Abbaueinheit tragen.
- Auf **Ecken** stehen Hauptbahnhof, Bahnhof, Großbahnhof, Hafen oder Großhafen.
- Auf **Kanten** stehen Schienen. Eine Schiene ist nur zwischen zwei
  Geländefeldern zulässig.
- Auf einem **Geländefeld** steht entweder eine Abbaueinheit oder eine
  Geschäftsbank. Beide sind neutral und haben keinen dauerhaften Spielerbesitzer.
- Ein Hafen oder Großhafen ist nur an einer Ecke mit mindestens zwei angrenzenden
  Wasserfeldern und zwei angrenzenden Geländefeldern zulässig.
- In Runde 0 werden Hauptbahnhöfe mit mindestens drei Kanten Abstand zueinander
  platziert.
- Eine eigene Schiene, ein eigener Bahnhof oder ein eigener Großbahnhof neben
  einer Abbaueinheit bilden unterschiedlich starke Anschlüsse.
- Frachtschiffe verbinden zwei Häfen und liegen deshalb nicht auf einer normalen
  Dreieckskante. Panzer und Kriegsschiffe sind nur während eines Krieges auf dem
  Brett.
- Nach erfolgreicher Belagerung werden Eckgebäude zu einer zerstörten neutralen
  Variante.

Noch offene Regeln der Anleitung dürfen nicht unbemerkt als feste Regeln in den
Code eingehen. Das betrifft vor allem:

- die Zuordnung von Geländetypen zu Rohstoffen,
- die endgültigen Bau- und Fixkosten,
- den Drei-Kanten-Abstand nach Runde 0,
- Mehrfachanschluss beziehungsweise Kontrolle neutraler Anlagen,
- die Platzierung der Startschienen und Start-Abbaueinheiten,
- genaue Kriegsbewegungen und Belagerungswerte.

Diese Punkte werden über benannte Regelparameter oder zunächst bewusst nicht
automatisiert. Für Runde 0 wird nur die bereits festgelegte Abstandsregel
verbindlich umgesetzt.

## Festgestellter Ausgangszustand

- `Spielkarte` speichert derzeit Abmessungen, Geländedreiecke und sechs Dreiecke
  große `Spezialfeld`-Gruppen.
- Der Karteneditor besitzt Gelände- und Spezialwerkzeuge, kennt aber noch keinen
  Spielmodus und keine Belegung von Ecken, Kanten oder einzelnen Feldern.
- Die 3D-Geometrie kann ein Dreieck und dessen nächste Ecke treffen. Eine
  kanonische Identität für Ecken und Kanten sowie ein Kantentreffer fehlen.
- Die 3D-Darstellung zeichnet nur Dreiecksauflagen. Modelle für Gebäude, Schienen
  und Feldanlagen fehlen.
- Die Kartenansicht einer laufenden Partie ist nur lesbar.
- Die laufende Karte liegt bereits in `SpielZustand`; fachliche Änderungen werden
  grundsätzlich über `SpielEreignis`, `SpielRegelwerk` und `SpielAblauf`
  gespeichert.
- `SpielEreignis.Expansion` zählt ein Bauteil beim Spieler, enthält aber keinen
  Kartenort. Dadurch können Bestand, Kosten und Brettbelegung auseinanderlaufen.
- `BauteilTyp` und die Rohstoffliste im Domain-Modul sind umfangreicher als die
  aktuelle Fassung der Bedienungsanleitung. Außerdem fehlt dort der
  Hauptbahnhof.
- Die Vorlage `inselreich.json` enthält `STADT` und `HAFEN` als sechs Dreiecke
  große Spezialfelder. Diese Darstellung passt nicht zu den aktuellen Regeln.

## Fachliches Datenmodell

### 1. Kartengrundlage und Partiebelegung trennen

Die bisherigen Daten werden in zwei Verantwortungen geteilt:

```kotlin
KartenVorlage(
    formatVersion,
    id,
    name,
    startZeile,
    startSpalte,
    zeilen,
    spalten,
    gelaendefelder,
)

Spielkarte(
    vorlage,
    belegung,
)

KartenBelegung(
    ecken,
    kanten,
    felder,
    seewege,
    kriegseinheiten,
)
```

`KartenVorlage` wird im Modus **Bauen** bearbeitet. `Spielkarte` ist die Instanz
im `SpielZustand` und wird im Modus **Spielen** fortgeschrieben. Beim Erstellen
einer Partie wird die gewählte Vorlage mit einer leeren Belegung kopiert.

Die serialisierten Belegungen werden als sortierte Listen gespeichert. Für
schnelle Abfragen stellt das Modell abgeleitete Maps bereit. Strukturierte
Objekte sollen nicht direkt als JSON-Schlüssel verwendet werden.

### 2. Stabile Ortskennungen einführen

Die Karte benötigt drei voneinander unabhängige, serialisierbare Ortsarten:

- `KartenFeld`: vorhandene Dreieckskoordinate; ersetzt beziehungsweise präzisiert
  `KartenDreieck`.
- `KartenEcke`: kanonische Gitterkoordinate eines gemeinsamen Eckpunkts. Dieselbe
  physische Ecke muss unabhängig vom berührten Nachbardreieck dieselbe Kennung
  erhalten.
- `KartenKante`: kanonisch sortiertes Paar zweier `KartenEcke`-Werte. Die
  Reihenfolge des Antippens darf keine zweite Kante erzeugen.

`SpielbrettGeometrie` liefert anschließend zentral:

- die drei Ecken und drei Kanten eines Feldes,
- die an einer Ecke angrenzenden Felder,
- die an einer Kante angrenzenden ein oder zwei Felder,
- benachbarte Ecken und kürzeste Kantenabstände,
- die Konvertierung zwischen Fachkoordinate und 3D-Position,
- einen Treffer mit Zielart `FELD`, `KANTE` oder `ECKE`.

Geometrische Fließkommawerte werden nur für Darstellung und Trefferprüfung
verwendet, niemals als persistierte Identität.

### 3. Typisierte Belegung modellieren

Eine Ortsart akzeptiert nur die dafür erlaubten Inhalte:

| Ort | Inhalt | Besitzer/Zustand |
| --- | --- | --- |
| Ecke | Hauptbahnhof, Bahnhof, Großbahnhof, Hafen, Großhafen | Spieler; zusätzlich intakt, belagert oder zerstört |
| Kante | Schiene | Spieler; zusätzlich intakt oder zerstört |
| Geländefeld | Abbaueinheit mit Rohstoff oder Geschäftsbank | neutral; aktiv, verlassen oder zerstört nur soweit fachlich nötig |
| Seeweg | Frachtschiff zwischen zwei Hafen-Ecken | Spieler sowie Import-/Exportrichtung |
| Krieg | Panzer oder Kriegsschiff | Spieler, Konflikt und nur temporär während des Krieges |

Pro Ecke, Kante und Feld ist zunächst höchstens eine Belegung zulässig. Auf
Wasserfeldern gibt es keine Feldbelegung. Seewege und Kriegseinheiten werden als
eigene, spätere Ausbaustufe modelliert und nicht künstlich in eine normale
Kante oder ein Feld gezwungen.

Die vorhandenen Mengen in `Spieler.bauteile` werden nicht parallel als zweite
Wahrheit gepflegt. Sie werden entweder aus der Kartenbelegung ausgewertet oder
in derselben fachlichen Operation konsistent aktualisiert. Bevorzugt wird die
Auswertung aus der Karte.

## Verhalten der beiden Modi

### Modus Bauen

Der bestehende `KartenEditorDialog` wird zu einem Kartenbildschirm mit sichtbarer
Moduswahl weiterentwickelt. In **Bauen** stehen nur diese Werkzeuge zur Verfügung:

- Wasser,
- Ebene,
- Wald,
- Gebirge,
- Wüste,
- Sumpf,
- Kartenbereich in alle Richtungen erweitern beziehungsweise kontrolliert
  verkleinern,
- Kartenname ändern,
- Rückgängig und Wiederholen innerhalb des noch nicht gespeicherten Entwurfs.

Ein Tipp bearbeitet immer genau ein Dreieck. Das Setzen von Wasser entfernt das
Geländefeld. Die Oberfläche zeigt vor dem Verkleinern an, wie viele Geländefelder
entfallen. Eine belegte Karte aus einer laufenden Partie kann nicht im Baumodus
geöffnet werden.

Die bisherigen Werkzeuge `HEXAGON`, `STADT`, `HAFEN` und
`SPEZIAL_ENTFERNEN` entfallen aus dem Baumodus, weil sie nicht der aktuellen
Bedienungsanleitung entsprechen.

### Modus Spielen

**Spielen** ist nur für die Karte des geladenen Spielstands verfügbar. Die
Werkzeugleiste zeigt drei Zielgruppen:

- **Ecke:** Hauptbahnhof, Bahnhof, Großbahnhof, Hafen, Großhafen, aufwerten,
  beschädigen/reparieren oder entfernen.
- **Kante:** Schiene setzen, beschädigen/reparieren oder entfernen.
- **Feld:** Abbaueinheit mit Rohstoff oder Geschäftsbank setzen, Zustand ändern
  oder entfernen.

Der Ablauf einer Eingabe ist:

1. Zielgruppe und Aktion wählen.
2. Falls erforderlich den handelnden Spieler beziehungsweise den Rohstoff wählen.
3. Passende Ecke, Kante oder passendes Feld antippen.
4. Ziel hervorheben und die fachliche Vorprüfung samt Kosten und Regelbegründung
   anzeigen.
5. Erst nach Bestätigung ein `SpielEreignis` auslösen.
6. Nach Erfolg den neuen `SpielZustand` darstellen; bei Ablehnung bleibt die
   Karte unverändert und zeigt die konkrete Regelverletzung.

Die Zielgruppe bestimmt die Trefferzone. Dadurch wird ein Tipp nahe einer Ecke
nicht zugleich als Feldänderung interpretiert. Kamera drehen, verschieben und
zoomen bleiben unabhängig vom Kartenmodus verfügbar.

Im Spielmodus werden Spielergebäude und Schienen in der jeweiligen Spielerfarbe,
neutrale Anlagen in der Zentralbankfarbe und zerstörte Belegungen eindeutig
abweichend dargestellt. Auswahl und mögliche Ziele erhalten eine kurzlebige
Hervorhebung, die nicht gespeichert wird.

## Fachereignisse und Regeln

### Ereignisse

Das ortslose `SpielEreignis.Expansion` wird durch ortsbezogene Absichten ersetzt
oder kompatibel erweitert. Benötigt werden mindestens:

- `KartenBauteilGebaut(spieler, ort, bauteil)` einschließlich atomarer
  Kostenbuchung,
- `KartenBauteilAufgewertet(spieler, ort, von, zu)`,
- `KartenBauteilEntfernt(ort, grund)`,
- `KartenBauteilZustandGeaendert(ort, zustand, grund)`,
- `NeutraleAnlageErrichtet(feld, anlage)`,
- `NeutraleAnlageEntfernt(feld, grund)`,
- ein eigenes, kostenfreies Ereignis für die Hauptbahnhof-Platzierung in Runde 0.

Die Ereignisse drücken fachliche Absichten aus. Ein allgemeines
`BelegungErsetzt` wird nicht als UI-Einstieg angeboten, weil es Kosten, Besitz und
Platzierungsregeln umgehen könnte.

### Kartenregelwerk

Ein Android-freies `KartenRegelwerk` im Domain-Modul prüft und verändert die
Belegung. `SpielRegelwerk` delegiert die neuen Kartenereignisse dorthin.
Mindestens folgende Regeln werden automatisiert:

- Ort liegt innerhalb des Kartenbereichs.
- Ein Feld kann nur belegt werden, wenn es Gelände ist.
- Pro Ecke, Kante oder Feld gibt es höchstens eine zulässige Belegung.
- Eine Schiene liegt nur an einer Kante zwischen zwei Geländefeldern.
- Ein Hafen liegt nur an einer Ecke mit mindestens zwei Wasser- und zwei
  Geländefeldern.
- Eckgebäude und Schienen haben einen gültigen Spieler aus dem Spielstand.
- Neutrale Feldanlagen besitzen keinen Spieler.
- Hauptbahnhöfe werden nur in Runde 0 gesetzt und halten mindestens drei Kanten
  Abstand zu anderen belegten Runde-0-Ecken.
- Nur erlaubte Aufwertungen sind möglich, beispielsweise Bahnhof zu Großbahnhof
  und Hafen zu Großhafen.
- Kostenprüfung, Abbuchung und Platzierung sind eine atomare Zustandsänderung.
- Eine Zerstörung oder Entfernung löst später die in der Anleitung beschriebene
  Prüfung nicht mehr verbundener Schienen aus.

Reine Abfragen kommen in eine `KartenAuswertung`, darunter Hafenstandorte,
angeschlossene Spieler, beste Anschlussart, verlassene Anlagen, Erträge und
Kontrolle. Damit bleiben Darstellung und Regelwerk frei von doppelter
Nachbarschaftslogik.

Für Runde 0 ist ein ausdrücklicher Spielabschnitt erforderlich. Die aktuelle
Kombination aus `rundenzähler == 0` und einer normalen Einnahmenphase reicht nicht
aus, um Startplatzierung und ersten regulären Zug eindeutig zu unterscheiden.

## 3D-Darstellung und Bedienung

`Spielbrett3DModell` wird um getrennte, unveränderliche Darstellungslisten
erweitert:

- Geländeprismen für Felder,
- Eckmodelle für Gebäude,
- schmale Kantenmodelle für Schienen,
- Feldmodelle für Abbaueinheiten und Geschäftsbanken,
- Auswahl- und Gültigkeitsmarkierungen.

Die erste Umsetzung darf einfache, klar unterscheidbare geometrische Modelle
verwenden. Fachtyp, Spielerfarbe und Zustand müssen dennoch ohne Öffnen eines
Dialogs erkennbar sein. Rendering-Typen und Compose-Farben bleiben im App-Modul;
das Domain-Modell kennt nur fachliche Werte und `SpielerId`.

Die Trefferprüfung erhält Schwellenwerte in Bildschirm- beziehungsweise
Weltkoordinaten:

- im Eckwerkzeug wird die nächste Ecke gewählt,
- im Kantenwerkzeug die nächste Dreieckskante,
- im Feldwerkzeug das getroffene Dreieck.

Ein Treffer außerhalb des zulässigen Abstands führt zu keiner Änderung.

## Speicherung

1. Karten werden ausschließlich im aktuellen, radiusbasierten Hexagonformat
   gespeichert und geladen.
2. Veraltete eigene Kartendateien werden ignoriert und weder deserialisiert noch
   verändert. Gebündelte Vorlagen müssen das aktuelle Format besitzen.
3. Die gebündelte Vorlage `inselreich.json` enthält ausschließlich Gelände im
   aktuellen Format.
4. Eine ausgewählte Vorlage wird beim Spielstart mit neuer Spiel-ID und leerer
   Belegung in den `SpielZustand` kopiert. Spätere Kartenänderungen werden nur als
   Spielereignisse gespeichert, nicht zurück in die Vorlagendatei geschrieben.
5. Startzustand und Kartenereignisse bleiben über Speichern, Laden, Rückgängig und
   Wiederholen rekonstruierbar.

## Abgleich von Anleitung und bestehendem Fachmodell

Vor der Implementierung der automatischen Kosten- und Ertragsregeln sind drei
fachliche Entscheidungen festzuhalten:

1. Die Anleitung nennt Öl, Stahl, Ziegel und Nahrung; `Rohstoff` enthält derzeit
   zehn Rohstoffe. Es muss eine einzige verbindliche Liste geben.
2. Die Anleitung spricht von einer Abbaueinheit je Rohstoff und einer
   Geschäftsbank; `BauteilTyp` enthält dagegen konkrete Wirtschaftsregionen wie
   Viehhof, Mine und Raffinerie. Es ist zu entscheiden, ob diese konkrete Anlage
   die Abbaueinheit darstellt oder ob die Anleitung das Zielmodell vorgibt.
3. Baukosten sind in der Anleitung offen, im Code aber teilweise vorbelegt. Bis
   zur Entscheidung zeigt die Kartenoberfläche keine erfundenen „endgültigen“
   Werte an. Vorläufige Codewerte müssen als solche zentral gekennzeichnet sein.

Diese Entscheidungen ändern nicht die Ortsstruktur: Eck-, Kanten- und
Feldbelegung können vorher vollständig umgesetzt werden.

## Umsetzungsetappen

### Etappe 1 – Koordinaten und Kartenvorlage

- [x] `KartenEcke` und `KartenKante` als kanonische Domain-Typen einführen.
- [x] Nachbarschaft, Randfälle, Abstand und Konvertierung zentral testen.
- [x] `KartenVorlage` und leere `KartenBelegung` modellieren.
- [x] `Spielkarte` als Grundlage plus Belegung in `SpielZustand` integrieren.
- [x] Aktuelles Hexagonformat serialisieren und veraltete Formate ausschließen.

**Abnahme:** Jede physische Ecke und Kante hat unabhängig vom Ausgangsdreieck
genau eine stabile Kennung; gespeicherte Karten verwenden nur das aktuelle Format.

### Etappe 2 – Baumodus bereinigen

- [x] Moduswahl und getrennte Werkzeuglisten einführen.
- [x] Baumodus auf Wasser und Geländetypen beschränken.
- [x] Spezialfeldwerkzeuge und Altformat-Hinweise entfernen.
- [x] Rückgängig/Wiederholen für den Vorlagenentwurf ergänzen.
- [x] Warnung vor verlustbehaftetem Verkleinern ergänzen.

**Abnahme:** Im Baumodus lässt sich ausschließlich das Gelände einzelner
Dreiecksfelder verändern; es entsteht keine Spielbelegung.

### Etappe 3 – Belegung und Kartenregeln

- [x] Belegungstypen für Ecke, Kante und Feld ergänzen.
- [x] `KartenRegelwerk` und `KartenAuswertung` implementieren.
- [x] Ortsbezogene Kartenereignisse in `SpielEreignis` und `SpielRegelwerk`
  integrieren.
- [x] `Expansion` migrieren, damit es keine ortslosen Neubauten mehr erzeugt.
- [x] Runde 0 und Hauptbahnhof-Platzierung eindeutig modellieren.
- [x] Kosten und Spielerbestände atomar mit der Belegung ändern.

**Abnahme:** Jede bestätigte Spielaktion ist ein validiertes, rückgängig machbares
und speicherbares Fachereignis; eine ungültige Aktion verändert keinen Zustand.

### Etappe 4 – Spielmodus und Interaktion

- [x] Eck-, Kanten- und Feldtreffer getrennt berechnen.
- [x] Spielwerkzeugleiste mit Spieler-, Bauteil- und Rohstoffauswahl bauen.
- [x] Vorschau, Zielhervorhebung, Bestätigung und konkrete Fehlermeldung ergänzen.
- [x] Kartenroute mit dem aktuellen `SpielZustand` und Ereignis-Callbacks
  verbinden.
- [x] Geländeänderungen im Spielmodus technisch ausschließen.

**Abnahme:** Auf der laufenden Karte lassen sich regelkonforme Belegungen auf
Ecken, Kanten und Feldern setzen, ändern und entfernen, ohne dass Gelände oder
Vorlage verändert werden.

### Etappe 5 – 3D-Modelle und Spielerfarben

- [x] Darstellungsmodelle für Eckgebäude, Schienen und Feldanlagen ergänzen.
- [x] Besitzer, neutralen Zustand, Belagerung und Zerstörung sichtbar machen.
- [x] Große und kleine Bildschirmbreiten sowie Kameraoperationen prüfen.
- [x] Ausreichenden Farbkontrast und zusätzliche Formunterscheidung sicherstellen.

**Abnahme:** Ort, Typ, Besitzer und wesentlicher Zustand jeder Belegung sind in
der Kartenansicht erkennbar.

### Etappe 6 – Regelableitungen und Sonderobjekte

- [x] Ertrag und Kontrolle aus der tatsächlichen Nachbarschaft auswerten.
- [x] Verlassene neutrale Anlagen ableiten.
- [x] Hafenverbindungen und Frachtschiffe als Seewege modellieren.
- [x] Temporäre Panzer und Kriegsschiffe an den Konfliktzustand anbinden.
- [x] Belagerungsfolgen und Abriss unverbundener Schienen umsetzen, sobald die
  offenen Zahlenregeln festgelegt sind.

**Abnahme:** Wirtschaftliche und militärische Auswertungen lesen die Karte als
maßgebliche Quelle und benötigen keine manuell parallel gepflegten Bauteilmengen.

## Testplan

### Domain-JVM-Tests

- Serialisierungs-Rundreise von Vorlage, Belegung und allen Ortsarten.
- Veraltete eigene Karten werden ohne Deserialisierung ignoriert.
- Eindeutigkeit von Ecken und Kanten auch bei negativen Koordinaten.
- Nachbarschaften an Innenpunkten und Kartenrändern.
- Schiene nur zwischen zwei Geländefeldern.
- Hafen nur bei mindestens zwei Wasser- und zwei Geländefeldern.
- Ausschluss doppelter oder ortsfremder Belegung.
- Runde-0-Abstand der Hauptbahnhöfe.
- erlaubte und verbotene Aufwertungen.
- atomare Kostenbuchung: bei fehlenden Rohstoffen weder Abbuchung noch Belegung.
- Ereignisrekonstruktion, Rückgängig und Wiederholen mit Kartenaktionen.

### App-JVM- und Compose-Tests

- Bauwerkzeug verändert exakt ein Dreieck und keine Belegung.
- Spielwerkzeug verändert die passende Ortsart und kein Gelände.
- Treffer derselben Ecke beziehungsweise Kante aus mehreren Nachbarfeldern ergibt
  dieselbe Fachkoordinate.
- Auswahlvorschau und Regelablehnung zeigen verständliche Zustände.
- 3D-Zuordnung verwendet richtige Spieler- und Neutralfarben.

### Instrumentierte Tests

- Eine Vorlage bauen, speichern, neu laden und für ein Spiel auswählen.
- Runde 0 mit drei Spielern und gültigen Hauptbahnhofabständen durchführen.
- Schiene, Bahnhof und Abbaueinheit platzieren, App neu starten und Belegung
  unverändert wiederfinden.
- Ungültigen Hafenstandort antippen und unveränderten Spielstand bestätigen.
- Kartenereignis rückgängig machen und wiederholen.
- Drehen, Verschieben und Zoomen in beiden Modi ohne unbeabsichtigte Änderung.

## Definition of Done

Die Spielkarte gilt als vervollständigt, wenn:

- Bauen und Spielen in der Oberfläche eindeutig getrennt und jederzeit sichtbar
  sind,
- der Baumodus nur Wasser und Geländefelder verändert,
- der Spielmodus nur Belegungen auf Ecke, Kante und Feld verändert,
- alle Belegungen stabile fachliche Koordinaten besitzen,
- die festen Kartenregeln der Bedienungsanleitung im Domain-Modul geprüft werden,
- jede Spieländerung über den Ereignisablauf gespeichert sowie rückgängig und
  wiederholbar ist,
- Vorlagen und laufende Partien nicht gegenseitig verändert werden,
- ausschließlich aktuelle Karten- und Spielstandformate geladen werden,
- die 3D-Ansicht Typ, Besitzer und Zustand verständlich darstellt,
- Domain-, App- und Instrumentierungstests die beschriebenen Kernabläufe
  abdecken,
- keine zweite, manuell gepflegte Wahrheit für Brettbelegung und Bauteilbestand
  bestehen bleibt.
