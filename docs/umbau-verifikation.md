# Umbau-Verifikation

## Frühere modulare Baseline

Vor der Modulverschiebung am 21. Juli 2026 liefen `./gradlew test` und der damalige
Android-Debug-Build erfolgreich. Vor dem KI-Trainingsumbau auf Commit `0c78565`
lief anschließend ebenfalls:

```text
./gradlew test :apps:android:assembleDebug
BUILD SUCCESSFUL
```

Es gab damit keine bekannten Baseline-Kompilierungs- oder Testfehler. Fachregeln
wurden nicht geändert, um einen vorhandenen Test zu umgehen.

## Abschlussprüfung KI-Trainingsumbau

Am 21. Juli 2026 liefen auf dem aktuellen Branch erfolgreich:

```text
./gradlew test assembleDebug :apps:server:test :tools:simulation:test \
  :adapters:persistence-room:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL (107 Tasks)

./gradlew :tools:simulation:massentest
Simulation: 1000/1000 beendet, 0 trunciert, 0 Fehler

./gradlew :tools:simulation:run --args="--spiele 2 --seed 42 \
  --max-entscheidungen 500 --spieler sicherheit \
  --karte kleine-wirtschaft-v1 --ausgabe build/testsimulation-sicherheit"
Simulation: 2/2 beendet, 0 trunciert, 0 Fehler

cd apps/web
npm ci
npm run build

cd tools/ai-python
python -m unittest discover -s tests
```

Der Massentest lief mit vier isolierten Umgebungen in 1 Minute 45 Sekunden. Er
exportierte 1.000 JSONL-Episoden mit 44.841 Entscheidungen. Der Python-Parser las
alle Entscheidungen erfolgreich. Die Statistik meldete durchschnittlich 0,232
Runden und 44,841 Entscheidungen; der Zufallsagent beendet diese Baseline häufig
durch frühe Aufgabe.

Die Room-Porttests sind als Android-Instrumentierungstests vorhanden und wurden
kompiliert. Ihre Ausführung auf Gerät oder Emulator war nicht Teil dieser lokalen
Abnahme. Ein zusätzlich geprüfter gemischter Agentenlauf erreichte bei Seed 42 das
technische Limit; er blieb fehlerfrei und erzeugte korrekt `truncated`, aber kein
fachliches Partieergebnis.

## Während der Arbeit behobene Fehler

Zwischenstände enthielten kurzzeitig fehlende Android-Callback-Namen sowie
Kompilierungsfehler während der Modul- und API-Anpassung. Diese Fehler waren durch
den Umbau verursacht und wurden vor den jeweiligen Commits behoben. Es verbleibt
kein dokumentierter Build- oder Testfehler im bearbeiteten Stand.
