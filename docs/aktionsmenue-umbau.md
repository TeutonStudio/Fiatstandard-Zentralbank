# Aktionsmenü: Architektur und Performance

## Historischer Ausgangszustand

Der Menübutton `KI / Konflikt` öffnete unmittelbar `KiEntwickleroberflaeche`. Diese
Composable rief während ihrer Komposition `AktionsAuswertung.erlaubteAktionen` und
`BeobachtungsAuswertung.fuerSpieler` auf. Damit wurden für die gesamte Karte Bau-,
Karten- und Truppenaktionen sowie eine vollständige KI-Beobachtung erzeugt. Die Kosten
wuchsen mit der Kartengröße; insbesondere erzeugte die Gruppierung von Truppen über
`teilmengenAbZwei()` exponentiell viele Kandidaten. Die globale Aktionsauswertung und
deren JSON-basierter Sortierschlüssel bleiben für Simulation, Server und Debugger
erhalten, gehören aber nicht mehr zum normalen UI-Pfad.

## Aktueller Ablauf

`SpielmenueBereich.AKTIONEN` öffnet einen Dialog über den einzigen
`AktionsMenueZustand` des `GameViewModel`/Koordinators. Beim Öffnen berechnet
`AktionsMenueAuswertung.uebersicht` nur Spieler-, Zug-, Konflikt-, Angebots- und
Einheitenzählungen. Es erzeugt weder eine Kartenaktionsliste noch eine Beobachtung.

Die Bereiche Konflikt, Diplomatie, Truppen und Zug laden erst nach Auswahl auf
`Dispatchers.Default`. Ein Bereichswechsel, Schließen oder ein neuer Spielzustand
bricht die laufende Berechnung ab und macht verspätete Ergebnisse ungültig. Handel und
Anleihen schließen das Menü kontrolliert und delegieren an die vorhandenen Dialoge.

Konflikt enthält Kriegserklärung und Kapitulation; Diplomatie enthält
Waffenstillstand, Friedensannahme, unabhängigen Frieden und Allianzbeitritt. Eine
konkrete Aktion wird erst nach Auswahl und Bestätigung aus dem aktuellen Zustand
erzeugt und durch die Engine geprüft.

Truppen zeigen zunächst nur Stapel. Erst nach Auswahl eines Stapels werden die
geometrisch lokalen Nachbarkanten bestimmt; es gibt dabei keine Potenzmenge und keine
globale Kartendurchsuchung für Kandidaten.

## KI und Debugger

Der normale KI-Bereich verwaltet ausschließlich den persistierten `SpielerStil` über
`SpielerStilSetzen`/`SpielerStilGesetzt`. Der Stil ist damit im Ereignisverlauf,
Replay, Server und der Simulation identisch. Es gibt keinen nicht persistierten
Compose-Schalter und keinen erfundenen Agententyp.

`KiDebugOberflaeche` darf bewusst vollständige Aktionsräume und Beobachtungen
erzeugen, ist aber nur über die explizite Schaltfläche `KI-Debug öffnen` in
`BuildConfig.DEBUG` erreichbar. Der Domain-Code kennt `BuildConfig` nicht; ein
Release-Build enthält keinen sichtbaren Debug-Einstieg.

## Messung im Debug-Build

Logcat-Tags `AktionsMenue` enthalten Dauer, Kandidatenzahl und vollständig geprüfte
Kandidaten für Übersicht, Detailbereich und Truppenziele. Beim normalen Öffnen ist
`vollständigGeprüft=0` und es entstehen keine Kartenaktionskandidaten.

Tag `Spielbrett3D` zählt Aufbauten von `Spielbrett3DModell`. Das Modell ist in
`KartenSpielBildschirm` mit fachlich relevanten Karten-, Spieler- und
Hervorhebungs-Schlüsseln gecacht; bloßes Öffnen oder Schließen des Aktionsdialogs
erzeugt keinen neuen Aufbau. Entwicklungsziele sind unter 100 ms für Übersicht und
Konflikt sowie unter 150 ms für Diplomatie und Truppenübersicht.
