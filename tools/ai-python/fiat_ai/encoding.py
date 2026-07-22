from __future__ import annotations

import json
from typing import Any

import torch


STATE_FEATURES = 64
ACTION_FEATURES = 64
STYLES = (
    "VORSICHTIG", "PRODUKTIONSORIENTIERT", "SCHULDENFINANZIERT",
    "HANDELSORIENTIERT", "AGGRESSIV", "OPPORTUNISTISCH",
    "EXPANSIONISTISCH", "DEFENSIVE_DOMINANZ",
)


def _number(value: Any) -> float:
    if isinstance(value, bool):
        return float(value)
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, dict) and set(value) == {"cent"}:
        return float(value["cent"]) / 100_000.0
    return 0.0


def encode_state(observation: dict[str, Any]) -> torch.Tensor:
    if int(observation.get("beobachtungsVersion", -1)) != 2:
        raise ValueError("Es wird Beobachtungsversion 2 erwartet.")
    features = [0.0] * STATE_FEATURES
    features[0] = float(observation.get("runde", 0)) / 100.0
    markt = observation.get("markt", {})
    features[1] = float(markt.get("leitzinsBasispunkte", 0)) / 10_000.0
    players = list(observation.get("spieler", []))
    features[2] = len(players) / 7.0
    for index, player in enumerate(players[:7]):
        offset = 4 + index * 7
        features[offset] = _number(player.get("geld", 0))
        features[offset + 1] = _number(player.get("marktwert", 0))
        features[offset + 2] = sum(_number(x.get("menge", 0)) for x in player.get("rohstoffe", [])) / 100.0
        features[offset + 3] = len(player.get("offeneEigeneAnleihen", [])) / 10.0
        features[offset + 4] = len(player.get("erreichbareWirtschaftsstandorte", [])) / 100.0
        features[offset + 5] = len(player.get("einheiten", [])) / 50.0
        features[offset + 6] = float(bool(player.get("ausgeschieden", False)))
    karte = observation.get("karte") or {}
    features[55] = len(karte.get("gelaendefelder", [])) / 1000.0
    features[56] = len(karte.get("eckBauwerke", [])) / 100.0
    features[57] = len(karte.get("handelslinien", [])) / 500.0
    features[58] = len(karte.get("feldAnlagen", [])) / 500.0
    features[59] = len(karte.get("kriegseinheiten", [])) / 100.0
    features[60] = len(observation.get("kriege", [])) / 10.0
    features[61] = len(observation.get("belagerungen", [])) / 20.0
    features[62] = len(observation.get("friedensvertraege", [])) / 20.0
    features[63] = float(observation.get("ergebnis") is not None)
    return torch.tensor(features, dtype=torch.float32)


def action_canonical(action: dict[str, Any]) -> str:
    return json.dumps(action, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def encode_action(action: dict[str, Any], candidate_index: int = 0) -> torch.Tensor:
    if candidate_index < 0 or candidate_index > 0xFFFFFFFF:
        raise ValueError("Der lokale Kandidatenindex muss in 32 Bit darstellbar sein.")
    features = [0.0] * ACTION_FEATURES
    action_type = str(action.get("art", ""))
    # Der Typname wird zeichenweise injektiv im Feature-Präfix transportiert; die
    # vollständige kanonische Zeichenfolge bleibt daneben Identität im Dataset.
    encoded = action_type.encode("utf-8")[:31]
    features[0] = len(encoded) / 31.0
    for index, byte in enumerate(encoded, start=1):
        features[index] = byte / 255.0
    numeric: list[float] = []
    def collect(value: Any) -> None:
        if isinstance(value, dict):
            for key in sorted(value):
                if key != "art":
                    collect(value[key])
        elif isinstance(value, list):
            for item in value:
                collect(item)
        elif isinstance(value, (bool, int, float)):
            numeric.append(_number(value))
    collect(action)
    for index, value in enumerate(numeric[:28], start=32):
        features[index] = max(-10.0, min(10.0, value))
    # Kein globaler Aktionscode: Die vier letzten Bytes tragen ausschließlich den
    # stabilen Index innerhalb der aktuell legalen, kanonisch sortierten Kandidatenliste.
    # Damit bleiben auch Aktionen mit ausschließlich unterschiedlichen String-IDs für
    # das Modell unterscheidbar, ohne Hashing oder Kartenpositionsnummern.
    for byte_index in range(4):
        features[60 + byte_index] = ((candidate_index >> (byte_index * 8)) & 0xFF) / 255.0
    return torch.tensor(features, dtype=torch.float32)


def style_index(style: str) -> int:
    try:
        return STYLES.index(style)
    except ValueError as error:
        raise ValueError(f"Unbekannter Spielstil: {style}") from error
