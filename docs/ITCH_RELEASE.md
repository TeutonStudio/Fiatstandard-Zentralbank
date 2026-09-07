# itch.io-Release

Die Browserversion verwendet denselben Kotlin-Regelkern wie Android, Server und
Simulation. Sie läuft vollständig lokal und benötigt weder den JVM-Server noch
eine externe API.

## Artefakte bauen

```bash
./gradlew releaseArtifacts
```

Die Ausgabe liegt unter `build/releases/`:

- `fiatstandard-itch-v2.0.0.zip`
- `fiatstandard-android-v2.0.0-debug.apk`
- `checksums.sha256`
- `build-manifest.json`

## itch.io hochladen

1. Neues Projekt als HTML-Spiel anlegen.
2. `fiatstandard-itch-v2.0.0.zip` hochladen.
3. Die Datei als im Browser ausführbar markieren.
4. Einen ausreichend großen responsiven iframe verwenden.

Die Gradle-Aufgabe `:apps:web:itchBundlePruefen` stellt sicher, dass
`index.html` direkt im ZIP-Wurzelverzeichnis liegt, alle Laufzeitdateien
vorhanden sind und die Distribution keine Abhängigkeit zu `localhost`,
`127.0.0.1` oder unverschlüsselten HTTP-Endpunkten enthält.

## Lokale Entwicklung

```bash
./gradlew :apps:web:jsBrowserDevelopmentRun
```

Browser-Spielstände werden in `localStorage` als versionierte
`GespeichertesSpiel`-Dokumente abgelegt. Zusätzlich können sie in der Oberfläche
als JSON exportiert und wieder importiert werden.
