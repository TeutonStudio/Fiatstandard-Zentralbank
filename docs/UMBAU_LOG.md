# Umbau-Log

## Etappe 0 - Bestandsaufnahme & Absicherung

- Architektur-Ist dokumentiert: Screens/Composables, Zustandshaltung, Persistenz, Geld-/Rohstofftypen, Rundenwechsel und Abhängigkeiten.
- Charakterisierungstests für zentrale Geldoperationen und Testspiel-Basisdaten ergänzt.
- Versionskatalog für die kommenden Module um Kotlin/JVM, Serialization und Coroutines vorbereitet.
- `./gradlew build` wird als Abschlussprüfung der Etappe verwendet.

Offene Punkte:

- Kein Regelfragen-Blocker für Etappe 0.
- Für Etappe 1 muss `Rohstoffe` von Compose-`Color` entkoppelt werden, bevor der Typ in ein reines Kotlin-Modul wandern kann.
- `Zahlungsmittel` muss in späteren Etappen auf `Long` in kleinster Einheit migriert werden; die aktuellen Tests halten nur das bestehende Verhalten fest.
