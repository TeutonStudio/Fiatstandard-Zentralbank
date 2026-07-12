# Unklarheiten

## Etappe 3 - Automatischer Schuldenstrich: Bahnwege

- Frage: Wie soll bei einem automatisch faelligen Schuldenstrich die Anzahl der abzubauenden Bahnwege bestimmt werden?
  - Kontext: Die Ueberschuldungsserie wird jetzt am Ende des eigenen Zuges bewertet und als schuldenstrichfaellig markiert. Der eigentliche Schuldenstrich braucht aber weiterhin `entfernteBahnwege`, weil das Domain-Modell kein konkretes Streckennetz kennt.
  - Benoetigte Antwort:
    - Soll die App beim fälligen automatischen Schuldenstrich erneut einen Dialog zur Eingabe der entfernten Bahnwege öffnen?
    - Oder soll die Domain automatisch einen Ersatzwert verwenden, z. B. 0, alle Eisenbahnlinien oder eine andere feste Regel?
