from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess

import torch
import onnx

from .encoding import ACTION_FEATURES, STATE_FEATURES, STYLES
from .model import VariableActionPolicyValue


def export(checkpoint: Path, output: Path, manifest: Path) -> None:
    model = VariableActionPolicyValue()
    model.load_state_dict(torch.load(checkpoint, map_location="cpu", weights_only=True)["model"])
    model.eval()
    output.parent.mkdir(parents=True, exist_ok=True)
    example = (
        torch.zeros((1, STATE_FEATURES)),
        torch.zeros((1, 3, ACTION_FEATURES)),
        torch.zeros((1,), dtype=torch.long),
        torch.ones((1, 3), dtype=torch.bool),
    )
    torch.onnx.export(
        model, example, output,
        input_names=["state", "actions", "style", "legal_mask"],
        output_names=["scores", "value"],
        dynamic_axes={"actions": {1: "candidates"}, "legal_mask": {1: "candidates"}, "scores": {1: "candidates"}},
        opset_version=17,
        dynamo=False,
    )
    onnx.checker.check_model(onnx.load(output))
    try:
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        commit = "unknown"
    manifest.write_text(json.dumps({
        "modelVersion": "spieler-ki-model-v1-smoke",
        "observationSchemaVersion": 2,
        "actionSchemaVersion": 2,
        "episodeSchemaVersion": 2,
        "supportedStyles": list(STYLES),
        "maximumPlayers": 7,
        "trainingCommit": commit,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("checkpoint", type=Path)
    parser.add_argument("--output", type=Path, default=Path("build/model/spieler-ki-v1.onnx"))
    parser.add_argument("--manifest", type=Path, default=Path("build/model/manifest.json"))
    args = parser.parse_args()
    export(args.checkpoint, args.output, args.manifest)
    print(args.output)


if __name__ == "__main__":
    main()
