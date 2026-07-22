from __future__ import annotations

import argparse
import random
from pathlib import Path

import torch
from torch.nn import functional as F
from torch.utils.data import DataLoader

from .dataset import EpisodeDataset, collate_variable
from .model import VariableActionPolicyValue


def train(input_path: Path, checkpoint: Path, epochs: int = 2, seed: int = 42) -> dict[str, float]:
    random.seed(seed)
    torch.manual_seed(seed)
    torch.use_deterministic_algorithms(True)
    dataset = EpisodeDataset([input_path])
    if not dataset:
        raise ValueError("Das Dataset enthält keine Entscheidungen.")
    loader = DataLoader(dataset, batch_size=min(16, len(dataset)), shuffle=True,
                        generator=torch.Generator().manual_seed(seed), collate_fn=collate_variable)
    model = VariableActionPolicyValue()
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    last_loss = 0.0
    model.train()
    for _ in range(epochs):
        for batch in loader:
            logits, values = model(batch["states"], batch["actions"], batch["styles"], batch["mask"])
            policy_loss = F.cross_entropy(logits, batch["chosen"])
            value_loss = F.mse_loss(values, batch["values"])
            loss = policy_loss + 0.25 * value_loss
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
            last_loss = float(loss.detach())
    checkpoint.parent.mkdir(parents=True, exist_ok=True)
    torch.save({"model": model.state_dict(), "seed": seed, "epochs": epochs}, checkpoint)
    return {"loss": last_loss, "samples": float(len(dataset))}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("episodes", type=Path)
    parser.add_argument("--checkpoint", type=Path, default=Path("build/model/model.pt"))
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    print(train(args.episodes, args.checkpoint, args.epochs, args.seed))


if __name__ == "__main__":
    main()
