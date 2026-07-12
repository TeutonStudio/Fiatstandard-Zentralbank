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

## Etappe 1 - Domain-Modul und Geldbasis

- Neues reines Kotlin/JVM-Modul `:domain` angelegt.
- `:app` hängt von `:domain` ab; `:domain` hat keine Android- oder Compose-Abhängigkeit.
- Serialisierbaren Geldtyp `Geld` eingeführt: kleinste gespeicherte und berechnete Einheit ist Cent, also `100L == 1 Mark`.
- Erste serialisierbare Domain-Typen ergänzt: `SpielerId`, `KontoId`, `Rohstoff`, `RohstoffMenge`, `Spieler`, `Anleihe`, `GameState`.
- Domain-Tests für Cent-basierte Geldrechnung ergänzt.
- Android-freie Bauteiltypen (`BauteilTyp`, `BauteilArt`) mit Kosten, Ertrag und Verbrauch ergänzt.
- App-seitigen Adapter vom bestehenden `Spiel` in den neuen `GameState` ergänzt, damit die neue Domain-Struktur gegen reale Bestandsdaten getestet werden kann.
- Adaptertests für Geld-, Marktpreis-, Spieler- und Bauteilabbildung ergänzt.

Offene Punkte:

- Bestehende App-Modelle verwenden noch `Zahlungsmittel`; die Migration auf `Geld` erfolgt schrittweise.
- Bestehende App-UI nutzt weiterhin `Rohstoffe` mit Compose-`Color`; die Domain nutzt bereits Android-freie `Rohstoff`- und `BauteilTyp`-Typen.
