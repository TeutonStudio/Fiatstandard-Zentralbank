from __future__ import annotations

import argparse
from pathlib import Path

import torch
from torch.utils.data import DataLoader

from .dataset import EpisodeDataset, collate_variable
from .model import VariableActionPolicyValue


def evaluate(episodes: Path, checkpoint: Path) -> dict[str, float]:
    dataset = EpisodeDataset([episodes])
    loader = DataLoader(dataset, batch_size=32, collate_fn=collate_variable)
    model = VariableActionPolicyValue()
    model.load_state_dict(torch.load(checkpoint, map_location="cpu", weights_only=True)["model"])
    model.eval()
    correct = total = 0
    with torch.no_grad():
        for batch in loader:
            logits, _ = model(batch["states"], batch["actions"], batch["styles"], batch["mask"])
            choices = logits.argmax(dim=1)
            if not batch["mask"].gather(1, choices.unsqueeze(1)).all():
                raise AssertionError("Das Modell wählte eine maskierte Aktion.")
            correct += int((choices == batch["chosen"]).sum())
            total += len(choices)
    return {"accuracy": correct / max(1, total), "samples": float(total)}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("episodes", type=Path)
    parser.add_argument("checkpoint", type=Path)
    args = parser.parse_args()
    print(evaluate(args.episodes, args.checkpoint))


if __name__ == "__main__":
    main()
