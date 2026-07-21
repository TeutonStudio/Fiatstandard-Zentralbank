from __future__ import annotations

from dataclasses import dataclass
import argparse
import json
from pathlib import Path
from typing import Any, Iterator


AKTUELLE_FORMAT_VERSION = 2
EPISODEN_PFLICHTFELDER = frozenset(
    {
        "formatVersion",
        "regelVersion",
        "beobachtungsVersion",
        "aktionsVersion",
        "spielId",
        "seed",
        "szenarioId",
        "startzustand",
        "entscheidungen",
        "ereignisse",
        "ergebnis",
        "truncated",
    }
)
ENTSCHEIDUNGS_PFLICHTFELDER = frozenset(
    {
        "formatVersion",
        "regelVersion",
        "beobachtungsVersion",
        "aktionsVersion",
        "spielId",
        "entscheidungsNummer",
        "spieler",
        "beobachtung",
        "erlaubteAktionen",
        "gewaehlteAktion",
        "belohnung",
        "ergebnis",
    }
)


def _pflichtfelder(daten: dict[str, Any], pflichtfelder: frozenset[str], art: str) -> None:
    fehlend = pflichtfelder.difference(daten)
    if fehlend:
        raise ValueError(f"Fehlende {art}felder: {', '.join(sorted(fehlend))}")


def _enthaelt_passwortfeld(wert: Any) -> bool:
    if isinstance(wert, dict):
        return any(
            "passwort" in str(name).lower() or _enthaelt_passwortfeld(unterwert)
            for name, unterwert in wert.items()
        )
    if isinstance(wert, list):
        return any(_enthaelt_passwortfeld(element) for element in wert)
    return False


@dataclass(frozen=True)
class EntscheidungsDatensatz:
    format_version: int
    regel_version: str
    beobachtungs_version: int
    aktions_version: int
    spiel_id: str
    entscheidungs_nummer: int
    spieler: str
    beobachtung: dict[str, Any]
    erlaubte_aktionen: dict[str, Any]
    gewaehlte_aktion: dict[str, Any]
    belohnung: float
    ergebnis: dict[str, Any] | None

    @classmethod
    def aus_json(cls, daten: dict[str, Any]) -> "EntscheidungsDatensatz":
        _pflichtfelder(daten, ENTSCHEIDUNGS_PFLICHTFELDER, "Entscheidungs")
        return cls(
            format_version=int(daten["formatVersion"]),
            regel_version=str(daten["regelVersion"]),
            beobachtungs_version=int(daten["beobachtungsVersion"]),
            aktions_version=int(daten["aktionsVersion"]),
            spiel_id=str(daten["spielId"]),
            entscheidungs_nummer=int(daten["entscheidungsNummer"]),
            spieler=str(daten["spieler"]),
            beobachtung=dict(daten["beobachtung"]),
            erlaubte_aktionen=dict(daten["erlaubteAktionen"]),
            gewaehlte_aktion=dict(daten["gewaehlteAktion"]),
            belohnung=float(daten["belohnung"]),
            ergebnis=None if daten["ergebnis"] is None else dict(daten["ergebnis"]),
        )


@dataclass(frozen=True)
class SpielEpisode:
    format_version: int
    regel_version: str
    beobachtungs_version: int
    aktions_version: int
    spiel_id: str
    seed: int
    szenario_id: str
    startzustand: dict[str, Any]
    entscheidungen: tuple[EntscheidungsDatensatz, ...]
    ereignisse: list[dict[str, Any]]
    ergebnis: dict[str, Any] | None
    truncated: bool

    @classmethod
    def aus_json(cls, daten: dict[str, Any]) -> "SpielEpisode":
        _pflichtfelder(daten, EPISODEN_PFLICHTFELDER, "Episoden")
        format_version = int(daten["formatVersion"])
        if format_version != AKTUELLE_FORMAT_VERSION:
            raise ValueError(
                f"Nicht unterstützte Episodenformatversion: {format_version}; "
                f"erwartet wird {AKTUELLE_FORMAT_VERSION}."
            )
        if _enthaelt_passwortfeld(daten):
            raise ValueError("Trainingsdaten dürfen keine Passwortfelder enthalten.")
        entscheidungen_roh = daten["entscheidungen"]
        if not isinstance(entscheidungen_roh, list):
            raise ValueError("entscheidungen ist keine Liste.")
        entscheidungen = tuple(
            EntscheidungsDatensatz.aus_json(dict(entscheidung))
            for entscheidung in entscheidungen_roh
        )
        spiel_id = str(daten["spielId"])
        regel_version = str(daten["regelVersion"])
        beobachtungs_version = int(daten["beobachtungsVersion"])
        aktions_version = int(daten["aktionsVersion"])
        for entscheidung in entscheidungen:
            if (
                entscheidung.format_version != format_version
                or entscheidung.regel_version != regel_version
                or entscheidung.beobachtungs_version != beobachtungs_version
                or entscheidung.aktions_version != aktions_version
                or entscheidung.spiel_id != spiel_id
            ):
                raise ValueError(
                    "Entscheidungs- und Episodenversionen beziehungsweise Spiel-ID weichen ab."
                )
        return cls(
            format_version=format_version,
            regel_version=regel_version,
            beobachtungs_version=beobachtungs_version,
            aktions_version=aktions_version,
            spiel_id=spiel_id,
            seed=int(daten["seed"]),
            szenario_id=str(daten["szenarioId"]),
            startzustand=dict(daten["startzustand"]),
            entscheidungen=entscheidungen,
            ereignisse=list(daten["ereignisse"]),
            ergebnis=None if daten["ergebnis"] is None else dict(daten["ergebnis"]),
            truncated=bool(daten["truncated"]),
        )


def lade_episoden(pfad: Path) -> Iterator[SpielEpisode]:
    with pfad.open("r", encoding="utf-8") as datei:
        for zeilennummer, zeile in enumerate(datei, start=1):
            if not zeile.strip():
                continue
            try:
                daten = json.loads(zeile)
                if not isinstance(daten, dict):
                    raise ValueError("JSONL-Zeile ist kein Objekt.")
                yield SpielEpisode.aus_json(daten)
            except (json.JSONDecodeError, TypeError, ValueError) as fehler:
                raise ValueError(f"Ungültige Episode in Zeile {zeilennummer}: {fehler}") from fehler


def main() -> None:
    argumente = argparse.ArgumentParser(description="Validiert eine Simulations-JSONL-Datei.")
    argumente.add_argument("datei", type=Path)
    pfad = argumente.parse_args().datei
    episoden = list(lade_episoden(pfad))
    entscheidungen = sum(len(episode.entscheidungen) for episode in episoden)
    print(f"{entscheidungen} Entscheidungen aus {len(episoden)} Episoden gelesen.")


if __name__ == "__main__":
    main()
