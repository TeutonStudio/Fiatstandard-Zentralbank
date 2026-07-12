# Unklarheiten

## Etappe 3 - Schuldenstrich

- Frage: Wie soll der `Schuldenstrich` regeltechnisch modelliert werden?
  - Kontext: Die beantwortete Pflichtschritt-Regel sagt: Wenn der Spieler Finanz-Ausgaben nicht decken und auch nicht per Handel stopfen kann, entsteht entweder der Schuldenstrich oder eine Kriegserklärung. Nach einem Schuldenstrich wird die normale Spieleraktivität übersprungen.
  - Benoetigte Antwort:
    - Wann genau entsteht der Schuldenstrich: automatisch bei bestaetigtem Finanzloch, oder durch aktive Auswahl des Spielers?
    - Welche Daten muss der State speichern: nur `SpielerId`, Betrag, Runde, betroffene Glaeubiger/Anleihen?
    - Was passiert mit offenen Schulden/Anleihen beim Schuldenstrich: werden sie geloescht, gestundet, auf Bank gebucht oder bleiben sie bestehen?
    - Wie lange wird die Aktions-Phase uebersprungen: nur im aktuellen Zug oder auch in Folgezügen bis zu einer Bedingung?
