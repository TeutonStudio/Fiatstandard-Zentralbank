# Beobachtungsschema 2

`SpielBeobachtung.beobachtungsVersion` ist `2`. Die Projektion wird ausschließlich
durch `BeobachtungsAuswertung.fuerSpieler` aus dem Fachzustand erzeugt und mit
`kotlinx.serialization` deterministisch serialisiert. Es gibt in SpielerKIModel
v1 keine geheimen bereits eingetretenen Zustände; Passwort-Hashes werden niemals
exportiert.

## Kopf und Spieler

Der Kopf enthält Regel-/Beobachtungsversion, Perspektivspieler, aktiven Spieler,
Runde, Zugphase und Ergebnis. Jeder Spieler enthält in stabiler Reihenfolge:

- ID, Sitzreihenfolge, Aktiv-/Ausscheidensstatus und öffentlichen Stil,
- Geld, Marktwert, sämtliche Rohstoffbestände,
- Runden-/Produktions-/Versorgungswerte,
- kontrollierte Verwaltung und erreichbare Wirtschaftsstandorte,
- Einheiten und abgeschlossene Spielerhandelsgeschäfte,
- alle eigenen emittierten und gehaltenen Anleihen.

Jede Anleihe enthält ID, Emittent, Gläubiger, Nennwert, offenen Rückkaufsbetrag,
Zins, Laufzeit, Emissions-/Fälligkeitsrunde und erfolgte Zinszahlungen.

## Karte und Diplomatie

Die Kartensicht enthält die vollständige Topologie ausdrücklich als sortierte
`felder`, `knoten`, `kanten` und `spezialfelder`. Dazu kommen Gelände,
Verwaltungs- und Wirtschaftsbelegung, Ruinen, Handelslinien, Seewege/Frachtschiffe,
Panzer und Kriegsschiffe. Zusätzlich werden je Spieler die zentral berechnete
Erreichbarkeit und Kontrolle sowie Land-/Hafenblockaden abgebildet. Ein Seeweg
bleibt bei Blockade oder Verlust eines Endhafens samt Frachtschiff sichtbar, ist
aber bis zu zwei wieder kontrollierten, intakten Endhäfen inaktiv.

Kriege enthalten IDs, Seiten, Mitglieder, Waffenstillstandsangebote und
-abschlüsse, Kapitulationen und Status. Abgeschlossene Kapitulationen werden über
ihre Friedensvertrag-IDs der öffentlichen Spielerhistorie zugeordnet. Belagerungen enthalten Standort,
Verteidiger, alle Belagerer/Stärken, Führer, Beginn, Fortschritt und gespeicherten
Ertrag. Friedensverträge enthalten Rollen, Schuldverteilung, entstandene
Anleihen, Nachfolgestriche und Annahmen. Zentralbankgeldschöpfung und
Schuldenstriche sind öffentlich.

## Stabilität und Verbraucher

Listen und Mengen werden vor der Projektion nach kanonischen IDs beziehungsweise
Kartenkoordinaten sortiert. Kotlin-Worker, Episode 2, Server, Android/Web und
Python lesen dieses Schema ohne eigene Regelauslegung. Unbekannte oder andere
Versionen werden von Dataset und Modellagent abgelehnt; es gibt keinen V1-
Kompatibilitätsadapter.
