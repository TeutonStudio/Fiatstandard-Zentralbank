from __future__ import annotations

from pathlib import Path
from typing import Any

import torch
from torch.utils.data import Dataset

from .encoding import action_canonical, encode_action, encode_state, style_index
from .episode_parser import lade_episoden


class EpisodeDataset(Dataset[dict[str, Any]]):
    def __init__(self, paths: list[Path]):
        self.samples: list[dict[str, Any]] = []
        for path in paths:
            for episode in lade_episoden(path):
                transition_rewards = {
                    (str(t["spieler"]), int(t["startEntscheidung"])): float(t["akkumulierteBelohnung"])
                    for t in episode.spieler_uebergaenge
                }
                for index, decision in enumerate(episode.entscheidungen):
                    actions = list(decision.erlaubte_aktionen["aktionen"])
                    chosen = action_canonical(decision.gewaehlte_aktion)
                    canonical = [action_canonical(action) for action in actions]
                    if len(set(canonical)) != len(canonical):
                        raise ValueError("Kanonische Aktionsliste enthält eine Kollision.")
                    self.samples.append({
                        "state": encode_state(decision.beobachtung),
                        "actions": torch.stack([
                            encode_action(action, candidate_index)
                            for candidate_index, action in enumerate(actions)
                        ]),
                        "chosen": canonical.index(chosen),
                        "style": style_index(decision.spielstil),
                        "value": transition_rewards.get((decision.spieler, index), 0.0),
                    })

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int) -> dict[str, Any]:
        return self.samples[index]


def collate_variable(batch: list[dict[str, Any]]) -> dict[str, torch.Tensor]:
    max_actions = max(item["actions"].shape[0] for item in batch)
    feature_count = batch[0]["actions"].shape[1]
    actions = torch.zeros((len(batch), max_actions, feature_count), dtype=torch.float32)
    mask = torch.zeros((len(batch), max_actions), dtype=torch.bool)
    for index, item in enumerate(batch):
        count = item["actions"].shape[0]
        actions[index, :count] = item["actions"]
        mask[index, :count] = True
    return {
        "states": torch.stack([item["state"] for item in batch]),
        "actions": actions,
        "mask": mask,
        "chosen": torch.tensor([item["chosen"] for item in batch], dtype=torch.long),
        "styles": torch.tensor([item["style"] for item in batch], dtype=torch.long),
        "values": torch.tensor([item["value"] for item in batch], dtype=torch.float32),
    }
