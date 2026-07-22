# Episodenformat 2

Eine JSONL-Zeile ist genau eine vollständige `SpielEpisode` mit
`formatVersion = 2`, Regel-, Beobachtungs- und Aktionsversion, Spiel-ID, Seed,
Szenario-ID und passwortfreiem Startzustand. Das frühere Format wird nicht geladen.

## Entscheidung

Jeder `EntscheidungsDatensatz` speichert:

- vollständige Beobachtung und vollständigen legalen Aktionsraum,
- aktiven Spieler und öffentlichen Spielstil,
- gewählte Aktion,
- Belohnung jedes Spielers,
- `terminated`/`truncated`, Ausscheidensgründe und nächsten aktiven Spieler,
- sämtliche Schritt-Ereignisse, darunter Krieg, Belagerung und Geldschöpfung,
- Marktwerte aller Spieler vor und nach dem Schritt sowie das mögliche Ergebnis.

Die gewählte Aktion muss Bestandteil des gespeicherten Aktionsraums sein.
Entscheidungsnummern sind lückenlos.

## Spielerspezifische Trajektorien

`SpielerUebergang` beginnt an einer eigenen Entscheidung und akkumuliert die
Belohnungen dieses Spielers über alle dazwischenliegenden Mehrspielerschritte bis
zu seiner nächsten Aktion, seinem Ausscheiden, Partieende oder technischer
Truncation. Damit gehen Belohnungen nicht mehr beim gerade handelnden Spieler
verloren.

Terminal bleibt Sieger `+1`, alle Verlierer `-1`, technisch abgebrochen `0`.
Potential-Shaping ist relativ zum Gegnerdurchschnitt, zählt nur eigene
Kontrolle/Erreichbarkeit und klemmt reine Schuldenemission, Zentralbankgeld und
wiederholbaren Schuldenstrich als Reward-Exploit.

## Replay und Watchdog

`episode.replay()` faltet Startzustand und vollständigen Ereignisverlauf erneut.
Bleiben die Marktwerte aller Spieler über 10.000 aufeinanderfolgende
Entscheidungen gleich, endet nur die Trainingsumgebung mit `truncated`. Es gibt
keinen Sieger und keine Fachaktion zum Abbruch. Die Diagnose enthält Grund,
Zähler, die letzten 100 Aktionen und den vollständigen Endzustand.

Export und Import:

```bash
./gradlew :tools:simulation:run --args="--spiele 10 --seed 42 \
  --max-entscheidungen 10000 --spieler sicherheit \
  --karte generiert-wirtschaft-3 --ausgabe /tmp/fiat-episoden"
```
