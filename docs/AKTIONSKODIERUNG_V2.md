# Aktionskodierung 2

`AktionsRaum.aktionsSchemaVersion` ist `2`. Eine globale 512er-Maske und
`hashCode() % 512` existieren nicht mehr.

## Erzeugung

Für jeden Entscheidungspunkt erzeugt `AktionsAuswertung` alle regelkonformen
Kandidaten. Jeder Kandidat wird durch `StandardSpielEngine.pruefe` validiert,
kanonisch als JSON mit Diskriminator `art` und Defaultwerten serialisiert und
lexikografisch nach dieser Serialisierung sortiert. Identische Serialisierungen
sind ein Fehler. Kartenorte sind strukturierte Koordinaten, keine globalen IDs.

Bewegungen sind genau ein Kantenschritt; Gruppen führen stabile Einheiten-ID-
Listen. Numerische Kandidaten für Handel, Anleihen und Aufstockung stammen aus
Beständen, Marktpreisen, Verpflichtungsdefiziten, Leitzins und Validatorgrenzen.
Es gibt keine `take(128)`-/`take(4)`-Kappung.

Das Modellmerkmal enthält zusätzlich den in vier Bytes kodierten lokalen Index
innerhalb genau dieser kanonisch sortierten Kandidatenliste. Dieser Index ist kein
globaler Aktionscode und keine Kartenpositionsnummer. Er verhindert, dass zwei
ansonsten numerisch identische Aktionen mit verschiedenen String-IDs denselben
Action-Embedding-Eingang erhalten; es wird dafür weder gehasht noch modulo gerechnet.

## Modellanwendung

```text
observation → state encoder
for candidate in legal_actions:
    candidate → action encoder
    score(candidate) = policy(state, candidate, style)
softmax/argmax(score ausschließlich legaler Kandidaten)
```

Workerantworten führen pro Kandidat sowohl `canonical` als auch das strukturierte
`action`-Objekt. `step` akzeptiert eines davon und prüft vor der Engine erneut die
Mitgliedschaft in der aktuellen Kandidatenliste. Modellagent und Fallback können
daher keine nicht legale Aktion zurückgeben.

## Versionierung

Aktionsschema 2 steht im Aktionsraum, in jeder Episode und im Modellmanifest.
Eine Schemaabweichung verhindert Training beziehungsweise ONNX-Laden. Stabile
Serialisierung ist durch Kotlin-Tests, JSONL-Roundtrip und Replay abgesichert.
