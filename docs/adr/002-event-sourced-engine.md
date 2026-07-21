# ADR 002: Ereignisbasierte Engine bleibt maßgeblich

- Status: angenommen
- Datum: 2026-07-21

## Kontext

Das vorhandene Domain-Modul besitzt bereits serialisierbare Ereignisse,
`SpielRegelwerk` und eine getestete Rekonstruktion. Eine Umstellung auf Android-
oder Datenbankmodelle würde Portabilität und bestehende Spielstände gefährden.

## Entscheidung

`SpielZustand`, `SpielEreignis`, `SpielRegelwerk` und `SpielAblauf` bleiben die
Grundlage. Hinzu kommt `SpielAktion` als getrennte Beschreibung einer Absicht.
`SpielEngine` validiert die Absicht, erzeugt akzeptierte Ereignisse und faltet
diese über das bestehende Regelwerk.

Persistiert werden Startzustand, geordnete Ereignisse, Schema- und
Engine-Version sowie – wo relevant – der Seed. Der aktuelle Zustand ist ein
Read Model und kann vollständig rekonstruiert werden. Ein ungültiger atomarer
Aktionssatz schreibt kein Ereignis.

Formatänderungen erfolgen nur über eine erhöhte Schema-Version und einen
expliziten Konverter oder eine klare Ablehnung. Bestehende Ereignisnamen werden
nicht beiläufig umbenannt.

## Folgen

- Replay, Undo/Redo, Tests und Simulationen verwenden denselben Mechanismus.
- Aktion und Ereignis dürfen nicht als austauschbare DTOs behandelt werden.
- Projektionen können später neu aufgebaut werden.
- Änderungen an der Ereignisbedeutung benötigen Tests und eine neue
  Engine-Version.
- Android-Legacy-Ereignispfade bleiben nur als benannte, atomare
  Übergangsbrücke bestehen, bis entsprechende Aktionen vorliegen.
