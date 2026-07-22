# Krieg und Konflikt

## Formaler Zustand

Ein `Konflikt` besitzt eine stabile `KriegId`, Mengen von Aggressoren und
Verteidigern, paarweise Waffenstillstände, offene Angebote, Kapitulationen und
einen Status. Allianzen leben ausschließlich in diesem Krieg. Ein Spieler kann
gleichzeitig mehreren Kriegen angehören; Einheiten, Befehle, Ressourcen und
Schlachten bleiben immer spielerspezifisch.

Eine Kriegserklärung ist im Epizug kostenlos, unmittelbar wirksam und braucht
keine Verbindung. `KriegsAllianzBeitreten` ergänzt genau eine Seite.
`RessourcenUebertragen` überträgt Mark/Rohstoffe, niemals Einheiten oder Kontrolle.

## Kampf und Bewegung

Panzer kosten 3 Stahl und 2 Diesel und entstehen an einer Landhandelslinie neben
eigenem Bahnhof, Großbahnhof oder Hauptbahnhof. Die Kantengrundkosten sind
Eisenbahn 1, Gelände 2 und Gebirge 5 Diesel. Für `n` gemeinsam bewegte Panzer gilt
`n × Grundkosten − (n − 1)`. Bewegung bleibt kantenweise und darf im Epizug nach
einem Kampf fortgesetzt werden.

Kriegsschiffe kosten 3 Stahl und 2 Schweröl, entstehen an einem eigenen Hafen und
zahlen je Schiff/Seekante 1 Schweröl. Frachtschiffe kämpfen nicht und bleiben bei
Blockade, Ruinenbildung oder vorübergehendem Verlust eines Endhafens als
Kartenobjekt bestehen. Die Route ist dabei inaktiv und wird nach Wiederherstellung
beider kontrollierter Endhäfen automatisch wieder aktiv.

Land- und Seekampf verwenden dieselbe symmetrische Funktion. Bei gleicher Stärke
sterben alle; Differenz 1 lässt eine, Differenz 2 lässt drei, Differenz mindestens
3 lässt alle Einheiten der stärkeren Seite bestehen. Es gibt keinen Zufallswurf.

## Blockade und Belagerung

Die zentrale Graphauswertung entfernt feindlich besetzte Landkanten und durch
Kriegsschiffe blockierte Hafenzugänge. Eine alternative Route erhält
Erreichbarkeit. Wirtschaftsstandorte werden unabhängig von allen erreichenden
Spielern kontrolliert; jeder erhält den vollen regulären Ertrag.

Eine Verwaltungsbelagerung beginnt nur, wenn jede angeschlossene Handelslinie
durch feindliche Panzer besetzt ist. Eine freie Linie setzt Fortschritt und
Belagerungsbestand sofort auf null. Erforderlich sind 3 Runden für Bahnhof/Hafen,
5 für Großbahnhof/Großhafen und 7 für den Hauptbahnhof.

Pro voller Runde wird der theoretische normale Standortertrag gespeichert, nicht
beim Verteidiger abgezogen und nicht parallel normal ausgezahlt. Führend ist die
größte beteiligte Einheitengruppe; bei Gleichstand bleibt der frühere Führer. Nach
Ablauf wird das Gebäude eine neutrale Ruine und der Führer erhält den Bestand. Ein
zerstörter Hauptbahnhof löst sofort Ausscheiden und Siegprüfung aus.

Eine erreichbare, nicht voll blockierte Ruine kostet bei Reparatur 3 Ziegel und
2 Kohle und gehört danach dem Zahler. Abriss entfernt sie ohne Ertrag.

## Waffenstillstand, Frieden und Kapitulation

Waffenstillstand entsteht durch Angebot und Annahme und gilt paarweise. Sind alle
Aggressor-Verteidiger-Paare im Waffenstillstand, endet der Krieg unentschieden und
ohne Schuldtransfer.

Ein Friedensvertrag führt Beteiligte, Gewinner, Verlierer, Unentschiedene,
Ausscheidende, Kapitulationen, Schuldanteile, neue Anleihen und erforderliche
Nachfolgestriche. Jede beteiligte Person erhält genau eine Ergebnisrolle. Ein
Gesamtvertrag umfasst alle verbleibenden Kriegsteilnehmer, beginnt ausschließlich
mit der Zustimmung des vorschlagenden Spielers und braucht danach alle Annahmen.
Ein unabhängiger Frieden entfernt nur den ausdrücklich ausscheidenden Teilnehmer.
`KriegKapitulieren` ist ausschließlich im Krieg legal, markiert den Spieler als
Verlierer und beendet seine Beteiligung, nicht die Partie.

## Schuldübertragung

Unmittelbar vor nicht unentschiedenem Frieden werden mit derselben
`MarktAuswertung` die Marktwerte aller Verlierer und alle offenen Nennwerte der
Sieger ermittelt. Anteile sind proportional; Cent-Reste werden nach Spieler-ID
verteilt und im Vertrag markiert. Siegeranleihen werden vollständig ausgelöst und
beim selben Gläubiger durch gleich hohe, mindestens zum Leitzins verzinste
Verliereranleihen ersetzt. Reicht die reguläre Bankkapazität eines Verlierers
nicht, folgt nach seinem letzten Krieg automatisch Schuldenstrich, andernfalls
Ausscheiden als Kriegsfolge.

## Zustandsautomat

```text
AKTIV ──Friedensangebot──> FRIEDEN_ANGEBOTEN
  │                            │
  ├──Kapitulation──────────────┤
  ├──unabhängiger Frieden──────┤──> Teilnehmer entfernt
  └──alle Paar-Waffenstillstände──> unentschieden beendet

FRIEDEN_ANGEBOTEN ──alle Annahmen──> Vertrag vollzogen / Krieg reduziert oder beendet
```
