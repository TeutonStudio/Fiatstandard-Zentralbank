from __future__ import annotations

import torch
from torch import nn

from .encoding import ACTION_FEATURES, STATE_FEATURES, STYLES


class VariableActionPolicyValue(nn.Module):
    def __init__(self, hidden: int = 64):
        super().__init__()
        self.state_encoder = nn.Sequential(nn.Linear(STATE_FEATURES, hidden), nn.ReLU(), nn.Linear(hidden, hidden))
        self.action_encoder = nn.Sequential(nn.Linear(ACTION_FEATURES, hidden), nn.ReLU(), nn.Linear(hidden, hidden))
        self.style_embedding = nn.Embedding(len(STYLES), 16)
        self.policy_scorer = nn.Sequential(nn.Linear(hidden * 2 + 16, hidden), nn.ReLU(), nn.Linear(hidden, 1))
        self.value_head = nn.Sequential(nn.Linear(hidden + 16, hidden), nn.ReLU(), nn.Linear(hidden, 1))

    def forward(
        self,
        states: torch.Tensor,
        actions: torch.Tensor,
        styles: torch.Tensor,
        legal_mask: torch.Tensor,
    ) -> tuple[torch.Tensor, torch.Tensor]:
        state_embedding = self.state_encoder(states)
        action_embedding = self.action_encoder(actions)
        style_embedding = self.style_embedding(styles)
        count = actions.shape[1]
        context = torch.cat((state_embedding, style_embedding), dim=-1).unsqueeze(1).expand(-1, count, -1)
        scores = self.policy_scorer(torch.cat((context, action_embedding), dim=-1)).squeeze(-1)
        scores = scores.masked_fill(~legal_mask, torch.finfo(scores.dtype).min)
        value = self.value_head(torch.cat((state_embedding, style_embedding), dim=-1)).squeeze(-1)
        return scores, value
