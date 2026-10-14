"""Pruebas del protocolo y de la puntuación; no sustituyen la escucha."""
import contextlib
import difflib
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

import run
import tune


class ProtocolTests(unittest.TestCase):
    def test_voice_tuning_preserves_duration_and_other_rules(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            rules = folder / "lang/esus/rules"
            rules.mkdir(parents=True)
            for name in ("ss_val.dr", "ss_dur.dr"):
                original = (run.ROOT / "lang/esus/rules" / name).read_text(encoding="utf-8")
                (folder / (name + ".original")).write_text(original, encoding="utf-8")
            tune.patch(SimpleNamespace(out=folder, profile="friction"))
            control = (rules / "ss_val.dr").read_text(encoding="utf-8")
            durations = (rules / "ss_dur.dr").read_text(encoding="utf-8")
            for profile in ("friction-voice3", "friction-voice6"):
                tune.patch(SimpleNamespace(out=folder, profile=profile))
                candidate = (rules / "ss_val.dr").read_text(encoding="utf-8")
                self.assertEqual(durations, (rules / "ss_dur.dr").read_text(encoding="utf-8"))
                changes = [line for line in difflib.ndiff(control.splitlines(), candidate.splitlines()) if line.startswith(("- ", "+ "))]
                self.assertEqual(len(changes), 4)
                self.assertTrue(all("2754" in line for line in changes))

    def test_listening_page_uses_selected_speed(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            run.listening_page(folder, 12, (250,))
            training = (folder / "entrenar.html").read_text(encoding="utf-8")
            self.assertIn("vaca-250-orthographic.wav", training)
            self.assertNotIn("vaca-180-orthographic.wav", training)
            self.assertIn("250 palabras", (folder / "escuchar.html").read_text(encoding="utf-8"))

    def test_tuning_changes_only_the_named_rule_and_checks_source(self):
        text = 'rule other from x\n  store movw imm 40 statefld 2762\nend\nrule span_ph_v from x\n  store movw imm 40 statefld 2762\nend\n'
        result = tune.replace_rule(text, 'span_ph_v', [('store movw imm 40 statefld 2762', 'store movw imm 52 statefld 2762')])
        self.assertEqual(result.split('rule span_ph_v')[0], text.split('rule span_ph_v')[0])
        self.assertIn('store movw imm 52 statefld 2762', result)
        with self.assertRaises(ValueError):
            tune.replace_rule(result, 'span_ph_v', [('store movw imm 40 statefld 2762', 'replacement')])

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
