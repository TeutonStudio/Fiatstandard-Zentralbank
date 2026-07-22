from pathlib import Path
import json
import tempfile
import unittest

from fiat_ai.episode_parser import lade_episoden
from fiat_ai.encoding import encode_action


class EpisodenSmokeTest(unittest.TestCase):
    def test_exportierte_episode_wird_gelesen(self) -> None:
        pfad = Path(__file__).parent / "fixtures" / "episode.jsonl"

        episoden = list(lade_episoden(pfad))

        self.assertEqual(1, len(episoden))
        self.assertEqual("spiel-0", episoden[0].spiel_id)
        self.assertEqual(42, episoden[0].seed)
        self.assertEqual("spieler-1", episoden[0].entscheidungen[0].spieler)
        self.assertEqual(1, episoden[0].entscheidungen[0].gewaehlte_aktion["zugId"])

    def test_passwortfeld_wird_abgelehnt(self) -> None:
        fixture = Path(__file__).parent / "fixtures" / "episode.jsonl"
        daten = json.loads(fixture.read_text(encoding="utf-8"))
        daten["startzustand"]["spieler"][0]["passwortHash"] = "nicht-exportieren"
        with tempfile.TemporaryDirectory() as ordner:
            pfad = Path(ordner) / "episode.jsonl"
            pfad.write_text(json.dumps(daten) + "\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "Passwortfelder"):
                list(lade_episoden(pfad))

    def test_lokaler_kandidatenindex_verhindert_string_parameter_kollision(self) -> None:
        erste = {"art": "HandelsangebotAnnehmen", "angebot": "angebot-a"}
        zweite = {"art": "HandelsangebotAnnehmen", "angebot": "angebot-b"}

        self.assertFalse(encode_action(erste, 0).equal(encode_action(zweite, 1)))


if __name__ == "__main__":
    unittest.main()
