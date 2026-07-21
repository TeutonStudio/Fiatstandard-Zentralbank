from pathlib import Path
import unittest

from fiat_ai.episode_parser import lade_episode


class EpisodenSmokeTest(unittest.TestCase):
    def test_exportierte_episode_wird_gelesen(self) -> None:
        pfad = Path(__file__).parent / "fixtures" / "episode.jsonl"

        schritte = list(lade_episode(pfad))

        self.assertEqual(1, len(schritte))
        self.assertEqual("episode-0", schritte[0].episode_id)
        self.assertEqual(42, schritte[0].seed)
        self.assertEqual("Agent-1", schritte[0].actor)
        self.assertEqual(1, schritte[0].chosen_action["zugId"])


if __name__ == "__main__":
    unittest.main()
