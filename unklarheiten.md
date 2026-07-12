# Unklarheiten

## Etappe 3 - Situativ entfallende Pflichtschritte

- Frage: Wann gelten Pflichtschritte automatisch als erledigt bzw. entfallen?
  - Kontext: Der Zugautomat kennt aktuell die Pflichtschritte `ROHSTOFF_EINNAHMEN`, `ROHSTOFF_AUSGABEN` und `FINANZ_AUSGABEN`. `Umbau.md` nennt als Beispiel "keine Ausgaben faellig -> Schritt gilt automatisch als erledigt". Im Bestandscode ist aber keine eindeutige Regel erkennbar, wie faellige Rohstoff- oder Finanz-Ausgaben pro Spieler bestimmt werden.
  - Benoetigte Antwort: Welche konkreten Bedingungen sollen je Pflichtschritt gelten?
    - `ROHSTOFF_EINNAHMEN`: immer manuell abschliessen, oder automatisch wenn kein Ertrag vorhanden ist?
    - `ROHSTOFF_AUSGABEN`: automatisch erledigt, wenn der Spieler keine verbrauchenden Bauteile/Regionen hat?
    - `FINANZ_AUSGABEN`: automatisch erledigt, wenn keine Zinsen/Rueckzahlungen/sonstigen Zahlungen faellig sind?
