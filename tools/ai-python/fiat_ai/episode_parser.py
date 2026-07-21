from __future__ import annotations

from dataclasses import dataclass
import argparse
import json
from pathlib import Path
from typing import Any, Iterator


PFLICHTFELDER = frozenset(
    {
        "episodeId",
        "engineVersion",
        "seed",
        "step",
        "actor",
        "observation",
        "legalActions",
        "chosenAction",
        "rewardComponents",
        "nextObservation",
        "terminated",
        "winner",
        "events",
    }
)


@dataclass(frozen=True)
class EpisodenSchritt:
    episode_id: str
    engine_version: str
    seed: int
    step: int
    actor: str
    observation: dict[str, Any]
    legal_actions: list[dict[str, Any]]
    chosen_action: dict[str, Any]
    reward_components: dict[str, float]
    next_observation: dict[str, Any]
    terminated: bool
    winner: str | None
    events: list[dict[str, Any]]

    @classmethod
    def aus_json(cls, daten: dict[str, Any]) -> "EpisodenSchritt":
        fehlend = PFLICHTFELDER.difference(daten)
        if fehlend:
            raise ValueError(f"Fehlende Episodenfelder: {', '.join(sorted(fehlend))}")
        return cls(
            episode_id=str(daten["episodeId"]),
            engine_version=str(daten["engineVersion"]),
            seed=int(daten["seed"]),
            step=int(daten["step"]),
            actor=str(daten["actor"]),
            observation=dict(daten["observation"]),
            legal_actions=list(daten["legalActions"]),
            chosen_action=dict(daten["chosenAction"]),
            reward_components={
                str(name): float(wert) for name, wert in daten["rewardComponents"].items()
            },
            next_observation=dict(daten["nextObservation"]),
            terminated=bool(daten["terminated"]),
            winner=None if daten["winner"] is None else str(daten["winner"]),
            events=list(daten["events"]),
        )


def lade_episode(pfad: Path) -> Iterator[EpisodenSchritt]:
    with pfad.open("r", encoding="utf-8") as datei:
        for zeilennummer, zeile in enumerate(datei, start=1):
            if not zeile.strip():
                continue
            try:
                daten = json.loads(zeile)
                if not isinstance(daten, dict):
                    raise ValueError("JSONL-Zeile ist kein Objekt.")
                yield EpisodenSchritt.aus_json(daten)
            except (json.JSONDecodeError, TypeError, ValueError) as fehler:
                raise ValueError(f"Ungültige Episode in Zeile {zeilennummer}: {fehler}") from fehler


def main() -> None:
    argumente = argparse.ArgumentParser(description="Validiert eine Simulations-JSONL-Datei.")
    argumente.add_argument("datei", type=Path)
    pfad = argumente.parse_args().datei
    schritte = list(lade_episode(pfad))
    episoden = {schritt.episode_id for schritt in schritte}
    print(f"{len(schritte)} Schritte aus {len(episoden)} Episoden gelesen.")


if __name__ == "__main__":
    main()
