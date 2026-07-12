# Unklarheiten

## Etappe 3 - Automatischer Schuldenstrich

- Frage: Wie soll die automatische Ausloesung nach mehr als drei friedlichen Ueberschuldungsrunden exakt ausgewertet werden?
  - Kontext: Der manuelle Schuldenstrich aus dem Finanz-Schulden-Dialog ist umgesetzt. Fuer die automatische Regel fehlen im aktuellen Domain-State noch Verlauf und Bewertungsdetails.
  - Benoetigte Antwort:
    - Wann wird die Ueberschuldung geprueft: am Ende jeder vollstaendigen Runde, am Anfang des Spielerzuges oder beim Finanz-Ausgaben-Schritt des betroffenen Spielers?
    - Wie wird die Schuldensumme mit Zinsen berechnet: Nennwert plus nur aktuell faellige Zinsen, plus naechste Zinszahlung oder plus alle bis Laufzeitende noch offenen Zinsen?
    - Was zaehlt zum Marktwert: nur Bauwerke/Einheiten ueber Rohstoffkosten, oder auch aktuelle Rohstofflager und Geldkonto?
    - Welche Preise gelten als "vorherige Runde", wenn noch keine Vor-Runde existiert?
    - Bedeutet "im Frieden" keine aktive Kriegslage waehrend aller drei Runden, oder reicht Frieden im Pruefmoment?
