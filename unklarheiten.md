# Unklarheiten

## Etappe 1 - Geldtypisierung

- Frage: Was ist die kleinste Geldeinheit fuer die neue `Long`-Darstellung?
  - Kontext: Der Bestand zeigt Beträge als `Mark` und speichert `Zahlungsmittel` basisbasiert bzw. als String. `Umbau.md` verlangt `Long` in kleinster Einheit, aber nennt keine Untereinheit.
  - Benoetigte Antwort: Soll `1L == 1 Mark` gelten, oder gibt es eine kleinere Einheit wie Pfennig/Cent?
