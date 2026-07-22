from __future__ import annotations

from abc import ABC, abstractmethod
import json
from pathlib import Path
import subprocess
from typing import Any, TextIO


class Environment(ABC):
    @abstractmethod
    def reset(self, seed: int | None = None) -> dict[str, Any]: ...

    @abstractmethod
    def observe(self) -> dict[str, Any]: ...

    @abstractmethod
    def legal_actions(self) -> list[dict[str, Any]]: ...

    @abstractmethod
    def step(self, action: dict[str, Any] | str) -> dict[str, Any]: ...

    @abstractmethod
    def close(self) -> None: ...


class NdjsonEnvironment(Environment):
    """Transportiert Befehle; ausschließlich der Kotlin-Worker führt Regeln aus."""

    def __init__(self, eingabe: TextIO, ausgabe: TextIO, environment_id: str = "default"):
        self._eingabe = eingabe
        self._ausgabe = ausgabe
        self.environment_id = environment_id
        self._geschlossen = False

    def reset(self, seed: int | None = None, scenario_id: str = "kleine-wirtschaft-v2") -> dict[str, Any]:
        daten: dict[str, Any] = {"environmentId": self.environment_id, "scenarioId": scenario_id}
        if seed is not None:
            daten["seed"] = seed
        return self._sende("reset", daten)

    def observe(self) -> dict[str, Any]:
        return self._sende("observe", {"environmentId": self.environment_id})

    def legal_actions(self) -> list[dict[str, Any]]:
        antwort = self._sende("legal_actions", {"environmentId": self.environment_id})
        aktionen = antwort.get("actions", [])
        if not isinstance(aktionen, list):
            raise RuntimeError("legal_actions lieferte keine Liste.")
        return aktionen

    def step(self, action: dict[str, Any] | str) -> dict[str, Any]:
        payload = {"environmentId": self.environment_id}
        payload["actionCanonical" if isinstance(action, str) else "action"] = action
        return self._sende("step", payload)

    def export_episode(self, path: Path) -> dict[str, Any]:
        return self._sende(
            "export_episode",
            {"environmentId": self.environment_id, "path": str(path)},
        )

    def close(self) -> None:
        if not self._geschlossen:
            self._sende("close", {"environmentId": self.environment_id})
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
            fehler = antwort.get("error", {})
            raise RuntimeError(str(fehler.get("message", fehler)))
        return antwort


class KotlinWorker:
    """Startet einen Workerprozess und stellt beliebig viele logische Umgebungen bereit."""

    def __init__(self, repository: Path | None = None):
        root = repository or Path(__file__).resolve().parents[3]
        self._prozess = subprocess.Popen(
            [str(root / "gradlew"), "-q", "--console=plain", ":tools:simulation:worker"],
            cwd=root,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        if self._prozess.stdin is None or self._prozess.stdout is None:
            raise RuntimeError("Worker-Ein-/Ausgabe konnte nicht geöffnet werden.")
        self._environments: dict[str, NdjsonEnvironment] = {}

    def environment(self, environment_id: str) -> NdjsonEnvironment:
        if environment_id in self._environments:
            return self._environments[environment_id]
        env = NdjsonEnvironment(self._prozess.stdout, self._prozess.stdin, environment_id)
        self._environments[environment_id] = env
        return env

    def batch_reset(self, configs: list[dict[str, Any]]) -> dict[str, Any]:
        return self._request("batch_reset", {"environments": configs})

    def batch_step(self, steps: list[dict[str, Any]]) -> dict[str, Any]:
        return self._request("batch_step", {"steps": steps})

    def _request(self, command: str, data: dict[str, Any]) -> dict[str, Any]:
        if self._prozess.stdin is None or self._prozess.stdout is None:
            raise RuntimeError("Worker ist geschlossen.")
        self._prozess.stdin.write(json.dumps({"command": command, **data}) + "\n")
        self._prozess.stdin.flush()
        response = json.loads(self._prozess.stdout.readline())
        if not response.get("ok", False):
            raise RuntimeError(str(response.get("error")))
        return response

    def close(self) -> None:
        if self._prozess.poll() is not None:
            return
        try:
            self._request("close", {})
        finally:
            self._prozess.wait(timeout=30)

    def __enter__(self) -> "KotlinWorker":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()
