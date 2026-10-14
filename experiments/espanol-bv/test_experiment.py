"""Pruebas del protocolo y de la puntuación; no sustituyen la escucha."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

import run


class ProtocolTests(unittest.TestCase):
    def test_homophone_controls_and_balanced_pairs(self):
        pairs = {}
        for case in run.corpus():
            pairs.setdefault(case["pair"], []).append(case)
        self.assertEqual(len(pairs), 3)
        for pair in pairs.values():
            self.assertEqual({case["letter"] for case in pair}, {"b", "v"})
            self.assertEqual(pair[0]["control"], pair[1]["control"])

    def test_natural_input_keeps_written_word_and_disables_markup(self):
        for case in run.corpus():
            self.assertEqual(run.input_text(case, "natural"), case["id"] + ".")
            self.assertNotIn("`", run.input_text(case, "natural"))

    def test_missing_answers_are_counted_and_modes_stay_separate(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            run.write_json(folder / "clave-privada.json", [
                {"trial": "trial-001", "mode": "control", "speed_setting_wpm": 180, "letter": "b"},
                {"trial": "trial-002", "mode": "control", "speed_setting_wpm": 180, "letter": "v"},
                {"trial": "trial-003", "mode": "orthographic", "speed_setting_wpm": 400, "letter": "v"},
            ])
            response = folder / "responses.csv"
            response.write_text("trial,response\ntrial-001,b\ntrial-003,b\n", encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                run.score(SimpleNamespace(run=folder, responses=response))
            groups = json.loads(output.getvalue())
            self.assertEqual(groups["control-180"], {"total": 2, "answered": 1, "correct": 1, "accuracy_all_trials": .5})
            self.assertEqual(groups["orthographic-400"]["correct"], 0)
            response.write_text("trial,response\ntrial-001,b\ntrial-001,v\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                run.score(SimpleNamespace(run=folder, responses=response))


if __name__ == "__main__":
    unittest.main()
