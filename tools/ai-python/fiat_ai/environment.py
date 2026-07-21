from __future__ import annotations

from abc import ABC, abstractmethod
import json
from typing import Any, TextIO


class Environment(ABC):
    @abstractmethod
    def reset(self, seed: int | None = None) -> dict[str, Any]:
        raise NotImplementedError

    @abstractmethod
    def observe(self) -> dict[str, Any]:
        raise NotImplementedError

    @abstractmethod
    def legal_actions(self) -> list[dict[str, Any]]:
        raise NotImplementedError

    @abstractmethod
    def step(self, action: dict[str, Any]) -> dict[str, Any]:
        raise NotImplementedError

    @abstractmethod
    def close(self) -> None:
        raise NotImplementedError


class NdjsonEnvironment(Environment):
    """Transportiert Befehle; die Gegenstelle führt ausschließlich die Kotlin-Engine aus."""

    def __init__(self, eingabe: TextIO, ausgabe: TextIO):
        self._eingabe = eingabe
        self._ausgabe = ausgabe
        self._geschlossen = False

    def reset(self, seed: int | None = None) -> dict[str, Any]:
        return self._sende("reset", {} if seed is None else {"seed": seed})

    def observe(self) -> dict[str, Any]:
        return self._sende("observe")

    def legal_actions(self) -> list[dict[str, Any]]:
        antwort = self._sende("legal_actions")
        aktionen = antwort.get("actions", [])
        if not isinstance(aktionen, list):
            raise RuntimeError("legal_actions lieferte keine Liste.")
        return aktionen

    def step(self, action: dict[str, Any]) -> dict[str, Any]:
        return self._sende("step", {"action": action})

    def close(self) -> None:
        if not self._geschlossen:
            self._sende("close")
            self._geschlossen = True

    def _sende(self, befehl: str, daten: dict[str, Any] | None = None) -> dict[str, Any]:
        if self._geschlossen:
            raise RuntimeError("Environment ist geschlossen.")
        nachricht = {"command": befehl, **(daten or {})}
        self._ausgabe.write(json.dumps(nachricht, separators=(",", ":")) + "\n")
        self._ausgabe.flush()
        zeile = self._eingabe.readline()
        if not zeile:
            raise EOFError("Kotlin-Environment hat den Antwortstrom geschlossen.")
        antwort = json.loads(zeile)
        if not isinstance(antwort, dict):
            raise RuntimeError("Environment-Antwort ist kein JSON-Objekt.")
        if antwort.get("ok") is False:
            raise RuntimeError(str(antwort.get("error", "Environment-Fehler")))
        return antwort
