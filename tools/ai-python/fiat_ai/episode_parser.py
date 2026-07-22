from __future__ import annotations

from dataclasses import dataclass
import argparse
import json
from pathlib import Path
from typing import Any, Iterator


AKTUELLE_FORMAT_VERSION = 2
AKTUELLE_BEOBACHTUNGS_VERSION = 2
AKTUELLE_AKTIONS_VERSION = 2


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
    spieler: str
    spielstil: str
    beobachtung: dict[str, Any]
    erlaubte_aktionen: dict[str, Any]
    gewaehlte_aktion: dict[str, Any]
    belohnungen: dict[str, float]
    terminated: bool
    truncated: bool

    @classmethod
    def aus_json(cls, daten: dict[str, Any]) -> "EntscheidungsDatensatz":
        required = {
            "spieler", "spielstil", "beobachtung", "erlaubteAktionen",
            "gewaehlteAktion", "belohnungen", "terminated", "truncated",
        }
        missing = required.difference(daten)
        if missing:
            raise ValueError(f"Fehlende Entscheidungsfelder: {', '.join(sorted(missing))}")
        if int(daten["beobachtungsVersion"]) != AKTUELLE_BEOBACHTUNGS_VERSION:
            raise ValueError("Inkompatible Beobachtungsversion.")
        if int(daten["aktionsVersion"]) != AKTUELLE_AKTIONS_VERSION:
            raise ValueError("Inkompatible Aktionsversion.")
        return cls(
            spieler=str(daten["spieler"]),
            spielstil=str(daten["spielstil"]),
            beobachtung=dict(daten["beobachtung"]),
            erlaubte_aktionen=dict(daten["erlaubteAktionen"]),
            gewaehlte_aktion=dict(daten["gewaehlteAktion"]),
            belohnungen={str(k): float(v) for k, v in dict(daten["belohnungen"]).items()},
            terminated=bool(daten["terminated"]),
            truncated=bool(daten["truncated"]),
        )


@dataclass(frozen=True)
class SpielEpisode:
    spiel_id: str
    seed: int
    szenario_id: str
    startzustand: dict[str, Any]
    entscheidungen: tuple[EntscheidungsDatensatz, ...]
    spieler_uebergaenge: tuple[dict[str, Any], ...]
    ereignisse: list[dict[str, Any]]
    ergebnis: dict[str, Any] | None
    truncated: bool

    @classmethod
    def aus_json(cls, daten: dict[str, Any]) -> "SpielEpisode":
        if int(daten.get("formatVersion", -1)) != AKTUELLE_FORMAT_VERSION:
            raise ValueError("Nicht unterstützte Episodenformatversion.")
        if int(daten.get("beobachtungsVersion", -1)) != AKTUELLE_BEOBACHTUNGS_VERSION:
            raise ValueError("Nicht unterstützte Beobachtungsversion.")
        if int(daten.get("aktionsVersion", -1)) != AKTUELLE_AKTIONS_VERSION:
            raise ValueError("Nicht unterstützte Aktionsversion.")
        if _enthaelt_passwortfeld(daten):
            raise ValueError("Trainingsdaten dürfen keine Passwortfelder enthalten.")
        entscheidungen = tuple(
            EntscheidungsDatensatz.aus_json(dict(wert))
            for wert in daten.get("entscheidungen", [])
        )
        return cls(
            spiel_id=str(daten["spielId"]),
            seed=int(daten["seed"]),
            szenario_id=str(daten["szenarioId"]),
            startzustand=dict(daten["startzustand"]),
            entscheidungen=entscheidungen,
            spieler_uebergaenge=tuple(map(dict, daten.get("spielerUebergaenge", []))),
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
                yield SpielEpisode.aus_json(json.loads(zeile))
            except (json.JSONDecodeError, KeyError, TypeError, ValueError) as fehler:
                raise ValueError(f"Ungültige Episode in Zeile {zeilennummer}: {fehler}") from fehler


def main() -> None:
    parser = argparse.ArgumentParser(description="Validiert Episode-V2-JSONL.")
    parser.add_argument("datei", type=Path)
    episoden = list(lade_episoden(parser.parse_args().datei))
    print(f"{sum(len(e.entscheidungen) for e in episoden)} Entscheidungen aus {len(episoden)} Episoden gelesen.")


if __name__ == "__main__":
    main()
