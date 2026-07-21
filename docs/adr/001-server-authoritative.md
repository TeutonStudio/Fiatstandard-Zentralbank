# ADR 001: Serverautoritative Web-Architektur

- Status: angenommen
- Datum: 2026-07-21

## Kontext

Android soll weiterhin lokal spielbar sein, während Browser-Clients denselben
Regeln folgen müssen. Eine im Browser duplizierte Engine würde abweichende
Validierung, Manipulation und schwer reproduzierbare Spielstände ermöglichen.

## Entscheidung

Der Browser ist ausschließlich Client. Er sendet versionierte `SpielAktionDto`
an einen JVM-Server. Der Server lädt die maßgebliche Sitzung, validiert jede
Aktion mit `SpielEngine`, speichert ausschließlich akzeptierte Ereignisse und
liefert einen bereinigten Zustands-DTO zurück.

Android darf die gleiche Engine lokal betreiben. Der Unterschied liegt nur im
Host und Persistenzadapter, nicht in den Fachregeln.

Die erste Serverimplementierung verwendet den JDK-HTTP-Server, damit der neue
vertikale Schnitt ohne zusätzliches Framework klein bleibt. Der Transport ist
hinter `SpielServerDienst` gekapselt und kann später gegen Ktor ausgetauscht
werden.

## Folgen

- Web-Clients benötigen für autoritative Aktionen eine Serververbindung.
- Validierung und Zustandsfortschritt sind auf allen Serverinstanzen identisch.
- Authentifizierung kann vor Dienst und Application-Layer ergänzt werden.
- Passwort-Hashes und interne Sicherheitsdaten gehören nicht in API-DTOs.
- Horizontale Skalierung erfordert später eine koordinierte Ablage bzw.
  Sperrstrategie; `SpielDienst` serialisiert heute Aktionen je Spielprozess.
