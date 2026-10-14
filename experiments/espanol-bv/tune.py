"""Preparar copias de laboratorio y comparar variantes nativas del fonema v."""
import argparse
import csv
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

import run

PROFILES = {"original": (40, 50, 60), "friction": (52, 50, 60), "friction-duration": (52, 80, 90)}
VOICED_PROFILES = {"friction": 0, "friction-voice3": 3, "friction-voice6": 6}


def replace_rule(text, rule, replacements):
    start = text.index("rule " + rule + " from ")
    end = text.index("\nend", start) + 4
    block = text[start:end]
    for old, new in replacements:
        if block.count(old) != 1:
            raise ValueError("No se encontró una única instrucción esperada: " + old)
        block = block.replace(old, new)
    return text[:start] + block + text[end:]


def prepare(args):
    source = run.ROOT / "free-eloquence-tts-main/native/openevv"
    args.out.mkdir(parents=True, exist_ok=False)
    for name in ("src", "tools", "include", "lib", "lang/esus"):
        shutil.copytree(source / name, args.out / name,
                        ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "delta_rules_c*.c"))
    shutil.copyfile(source / "Makefile", args.out / "Makefile")
    (args.out / "lab.mk").write_text(".PHONY: lab-objects\nlab-objects: $(OBJECTSWIN)\n", encoding="utf-8")
    for name in ("ss_val.dr", "ss_dur.dr"):
        shutil.copyfile(args.out / "lang/esus/rules" / name, args.out / (name + ".original"))
    paths = [p for folder in ("src", "tools", "include", "lib", "lang/esus") for p in (args.out / folder).rglob("*") if p.is_file()]
    run.write_json(args.out / "source-manifest.json", {"source": str(source), "files": [
        {"path": str(p.relative_to(args.out)), "sha256": run.sha(p)} for p in paths]})


def patch(args):
    af, short, long = PROFILES.get(args.profile, (52, 50, 60))
    acoustic = (args.out / "ss_val.dr.original").read_text(encoding="utf-8")
    duration = (args.out / "ss_dur.dr.original").read_text(encoding="utf-8")
    acoustic = replace_rule(acoustic, "span_ph_v", [("store movw imm 40 statefld 2762", f"store movw imm {af} statefld 2762")])
    voice_increment = VOICED_PROFILES.get(args.profile, 0)
    if voice_increment:
        acoustic = replace_rule(acoustic, "span_ph_v", [
            ("store movw imm 50 statefld 2754", f"store movw imm {50 + voice_increment} statefld 2754"),
            ("store movw imm 45 ind reg r6 2754", f"store movw imm {45 + voice_increment} ind reg r6 2754")])
    duration = replace_rule(duration, "span_ph_v_dur", [
        ("store movw imm 50 ind reg r6 2074", f"store movw imm {short} ind reg r6 2074"),
        ("store movw imm 60 ind reg r6 2074", f"store movw imm {long} ind reg r6 2074")])
    (args.out / "lang/esus/rules/ss_val.dr").write_text(acoustic, encoding="utf-8")
    (args.out / "lang/esus/rules/ss_dur.dr").write_text(duration, encoding="utf-8")
    run.write_json(args.out / (args.profile + ".patch.json"), {"profile": args.profile, "slot_2762": af,
        "voicing_increment": voice_increment,
        "duration_slot_2074": [short, long], "scope": "Solo span_ph_v y span_ph_v_dur de esus, en copia de laboratorio"})


def compare(args):
    out = args.out.resolve()
    reference = json.loads(args.reference.read_text(encoding="utf-8"))
    expected = {r["file"]: r for r in reference["records"]}
    reports = {name: json.loads((out / name / "report.json").read_text(encoding="utf-8")) for name in PROFILES}
    checks = []
    for profile, report in reports.items():
        for record in report["records"]:
            previous = expected[record["file"]]
            should_change = profile != "original" and record["mode"] == "orthographic" and record["letter"] == "v"
            changed = record["pcm_sha256"] != previous["pcm_sha256"]
            checks.append({"profile": profile, "file": record["file"], "changed": changed,
                           "expected_change": should_change, "pass": changed == should_change})
    # Observe the actual Klatt parameters, including noise with voicing, on
    # fresh worker processes; the new audio must match the generated sample.
    traces = []
    frame_data = {}
    for profile in PROFILES:
        for word in ("vaca", "votar", "tuvo"):
            for speed in (180, 400):
                prefix = f"{profile}-{word}-{speed}"
                tap = out / (prefix + ".tsv")
                wav = out / (prefix + "-trace.wav")
                existing = tap.exists() and wav.exists() and args.reuse_traces
                if (tap.exists() or wav.exists()) and not existing:
                    raise ValueError("La traza ya existe: " + prefix)
                env = {k: v for k, v in os.environ.items() if not k.startswith("EVV_")}
                env["EVV_KLATT_TAP"] = str(tap)
                if not existing:
                    subprocess.run([sys.executable, str(run.HERE / "run.py"), "_synth", "--dll", str(out / (profile + ".dll")),
                                    "--wav", str(wav), "--case", word, "--mode", "orthographic", "--speed", str(speed), "--language", "esus"],
                                   env=env, check=True, capture_output=True, timeout=30)
                with tap.open(encoding="utf-8") as source:
                    frames = [{k: int(v) for k, v in row.items()} for row in csv.DictReader(source, delimiter="\t")]
                frame_data[profile, word, speed] = frames
                active = [row for row in frames if row["af"] > 0]
                expected_record = next(r for r in reports[profile]["records"] if r["file"] == f"{word}-{speed}-orthographic.wav")
                assert run.audio_info(wav)["pcm_sha256"] == expected_record["pcm_sha256"], "La instrumentación alteró el audio"
                traces.append({"profile": profile, "word": word, "speed": speed,
                               "af_max": max(row["af"] for row in frames),
                               "af_level_ms_sum": sum(row["af"] * row["step"] for row in frames),
                               "frication_frame_ms": sum(row["step"] for row in active),
                               "voiced_frication_frame_ms": sum(row["step"] for row in active if row["av"] > 0),
                               "tap": tap.name, "tap_sha256": run.sha(tap)})
    for word in ("vaca", "votar", "tuvo"):
        for speed in (180, 400):
            base, stronger, longer = [next(t for t in traces if t["profile"] == p and t["word"] == word and t["speed"] == speed) for p in PROFILES]
            base_frames = frame_data["original", word, speed]
            strong_frames = frame_data["friction", word, speed]
            checks.append({"profile": "observed_af", "word": word, "speed": speed,
                           "pass": len(base_frames) == len(strong_frames)
                           and any(b["af"] > a["af"] and b["av"] > 0 for a, b in zip(base_frames, strong_frames))
                           and longer["af_level_ms_sum"] > base["af_level_ms_sum"]})
            checks.append({"profile": "observed_duration", "word": word, "speed": speed,
                           "pass": longer["frication_frame_ms"] > stronger["frication_frame_ms"]})
    run.write_json(out / "comparison.json", {"status": "technical_pass" if all(c["pass"] for c in checks) else "technical_fail",
        "reference_report": str(args.reference.resolve()), "reference_sha256": run.sha(args.reference),
        "tuner_sha256": run.sha(Path(__file__)), "build_script_sha256": run.sha(run.HERE / "build-variants.ps1"),
        "reused_existing_traces": args.reuse_traces,
        "checks": checks, "traces": traces,
        "scope": "Comparación local Windows x64, bytecode, esus, seis palabras. No ejecuta la matriz nativa completa ni evalúa reconocimiento auditivo."})
    labels = {"original": "Primera variante", "friction": "Fricción reforzada", "friction-duration": "Fricción reforzada y más duración"}
    sections = []
    for word in ("vaca", "votar", "tuvo"):
        for speed in (180, 400):
            sections.append(f"<section><h2>{word}, velocidad configurada {speed}</h2>")
            for profile, label in labels.items():
                sections.append(f'<p>{label}</p><audio controls preload="none" aria-label="{word}, {speed}, {label}" src="{profile}/{word}-{speed}-orthographic.wav"></audio>')
            sections.append("</section>")
    (out / "comparar.html").write_text('<!doctype html><html lang="es"><meta charset="utf-8"><title>Segunda prueba de v</title><style>body{font:1.15rem system-ui;max-width:52rem;margin:2rem auto;padding:1rem}audio{width:100%}section{margin:2rem 0}</style><h1>Segunda prueba de v</h1><p>Compara la primera variante con dos ajustes de las reglas acústicas. Escucha si votar conserva la v y la o, y si tuvo mantiene la fricción a velocidad alta. Un ajuste puede sonar excesivo o acercarse a f; tu evaluación decidirá el siguiente paso.</p><p>Las velocidades son valores configurados, no mediciones de palabras por minuto en estos ejemplos aislados.</p>' + "\n".join(sections) + "</html>", encoding="utf-8")
    print(json.dumps(traces, ensure_ascii=False, indent=2))
    if not all(c["pass"] for c in checks):
        raise RuntimeError("La comparación no pasó; revisar comparison.json")


def compare_voicing(args):
    out = args.out.resolve()
    reference = json.loads(args.reference.read_text(encoding="utf-8"))
    reports = {profile: json.loads((out / profile / "report.json").read_text(encoding="utf-8")) for profile in VOICED_PROFILES}
    baseline = {record["file"]: record for record in reports["friction"]["records"]}
    expected = {record["file"]: record for record in reference["records"]}
    if set(baseline) != set(expected):
        raise ValueError("La referencia debe contener exactamente las mismas palabras y velocidades")
    speeds = sorted({record["speed_setting_wpm"] for record in baseline.values()})
    if args.listening_speed not in speeds:
        raise ValueError("La velocidad de escucha no está en la corrida")
    checks = []
    for profile, report in reports.items():
        if set(r["file"] for r in report["records"]) != set(baseline):
            raise ValueError("Corpus incompleto: " + profile)
        for record in report["records"]:
            other = expected[record["file"]] if profile == "friction" else baseline[record["file"]]
            should_change = profile != "friction" and record["letter"] == "v" and record["mode"] == "orthographic"
            changed = record["pcm_sha256"] != other["pcm_sha256"]
            checks.append({"profile": profile, "file": record["file"], "expected_change": should_change,
                           "changed": changed, "pass": changed == should_change})
            checks.append({"profile": profile, "file": record["file"], "kind": "same_duration",
                           "pass": record["samples"] == other["samples"]})
    frame_data, traces = {}, []
    for profile in VOICED_PROFILES:
        for word in ("vaca", "votar", "tuvo"):
            for speed in speeds:
                prefix = f"{profile}-{word}-{speed}"
                tap, wav = out / (prefix + ".tsv"), out / (prefix + "-trace.wav")
                existing = tap.exists() and wav.exists() and args.reuse_traces
                if (tap.exists() or wav.exists()) and not existing:
                    raise ValueError("La traza ya existe: " + prefix)
                if not existing:
                    env = {k: v for k, v in os.environ.items() if not k.startswith("EVV_")}
                    env["EVV_KLATT_TAP"] = str(tap)
                    subprocess.run([sys.executable, str(run.HERE / "run.py"), "_synth", "--dll", str(out / (profile + ".dll")),
                                    "--wav", str(wav), "--case", word, "--mode", "orthographic", "--speed", str(speed), "--language", "esus"],
                                   env=env, check=True, capture_output=True, timeout=30)
                with tap.open(encoding="utf-8") as source:
                    frames = [{k: int(v) for k, v in row.items()} for row in csv.DictReader(source, delimiter="\t")]
                record = next(r for r in reports[profile]["records"] if r["file"] == f"{word}-{speed}-orthographic.wav")
                if run.audio_info(wav)["pcm_sha256"] != record["pcm_sha256"]:
                    raise RuntimeError("La instrumentación alteró el audio")
                frame_data[profile, word, speed] = frames
                traces.append({"profile": profile, "word": word, "speed": speed, "tap": tap.name, "tap_sha256": run.sha(tap)})
    for profile in ("friction-voice3", "friction-voice6"):
        for word in ("vaca", "votar", "tuvo"):
            for speed in speeds:
                base, candidate = frame_data["friction", word, speed], frame_data[profile, word, speed]
                changed_columns = sorted({key for a, b in zip(base, candidate) for key in a if a[key] != b[key]})
                raised_voicing = any(b["av"] > a["av"] and b["af"] > 0 for a, b in zip(base, candidate))
                checks.append({"profile": profile, "word": word, "speed": speed,
                               "kind": "only_av_changes", "changed_columns": changed_columns,
                               "pass": len(base) == len(candidate) and changed_columns == ["av"] and raised_voicing})
    run.write_json(out / "comparison.json", {"status": "technical_pass" if all(c["pass"] for c in checks) else "technical_fail",
        "reference_report": str(args.reference.resolve()), "reference_sha256": run.sha(args.reference),
        "tuner_sha256": run.sha(Path(__file__)), "runner_sha256": run.sha(run.HERE / "run.py"),
        "build_script_sha256": run.sha(run.HERE / "build-variants.ps1"), "reused_existing_traces": args.reuse_traces,
        "listening_speed": args.listening_speed, "checks": checks, "traces": traces,
        "scope": "Solo sonoridad: fricación y duración conservadas. Prueba local de palabras; no evaluación de naturalidad ni matriz nativa completa."})
    labels = {"friction": "Fricción reforzada: referencia", "friction-voice3": "Sonoridad aumentada: ajuste moderado", "friction-voice6": "Sonoridad aumentada: ajuste mayor"}
    sections = []
    for word in ("vaca", "votar", "tuvo"):
        sections.append(f"<section><h2>{word}, velocidad configurada {args.listening_speed}</h2>")
        for profile, label in labels.items():
            sections.append(f'<p>{label}</p><audio controls preload="none" aria-label="{word}, {label}" src="{profile}/{word}-{args.listening_speed}-orthographic.wav"></audio>')
        sections.append("</section>")
    (out / "comparar.html").write_text('<!doctype html><html lang="es"><meta charset="utf-8"><title>Sonoridad de v a ' + str(args.listening_speed) + '</title><style>body{font:1.15rem system-ui;max-width:52rem;margin:2rem auto;padding:1rem}audio{width:100%}section{margin:2rem 0}</style><h1>Sonoridad de v</h1><p>Esta prueba conserva la fricción reforzada y la duración original. Cambia únicamente la amplitud de la componente sonora durante v, con dos incrementos internos: 3 y 6 unidades. No son decibelios acústicos medidos.</p><p>Escucha si aparece una vibración más reconocible y natural, si se conserva la fricción y si aumenta demasiado el volumen. Todavía no se ha establecido que estos ajustes mejoren la naturalidad.</p>' + '\n'.join(sections) + '</html>', encoding="utf-8")
    if not all(c["pass"] for c in checks):
        raise RuntimeError("La comparación de sonoridad requiere revisión; consultar comparison.json")
    print(f"Sonoridad: {len(checks)} comprobaciones aprobadas; duración y demás parámetros conservados.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "patch", "compare", "compare-voicing"))
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--profile", choices=set(PROFILES) | set(VOICED_PROFILES))
    parser.add_argument("--reference", type=Path)
    parser.add_argument("--reuse-traces", action="store_true", help="Revalidar trazas y WAV ya generados al repetir la comparación")
    parser.add_argument("--listening-speed", type=int, default=180)
    args = parser.parse_args()
    if args.action == "prepare": prepare(args)
    elif args.action == "patch": patch(args)
    elif args.action == "compare": compare(args)
    else: compare_voicing(args)


if __name__ == "__main__":
    main()
