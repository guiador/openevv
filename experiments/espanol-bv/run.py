"""Banco local b/v. Solo biblioteca estándar; cada síntesis vive en un proceso."""
import argparse
import array
import csv
import ctypes
import hashlib
import json
import math
import os
from pathlib import Path
import random
import re
import shutil
import subprocess
import sys
import time
import wave
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
DEFAULT_DLL = ROOT / "free-eloquence-tts-main/nvda-es/addon/synthDrivers/openevv_es_engine/eci.dll"
LANGUAGES = {"esus": 0x20001, "eses": 0x20000}
MODES = ("natural", "control", "orthographic")


def sha(path):
    with open(path, "rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def corpus():
    cases = json.loads((HERE / "corpus.json").read_text(encoding="utf-8"))["cases"]
    seen = set()
    for case in cases:
        name = case["id"]
        if not re.fullmatch(r"[a-z]+", name) or name in seen:
            raise ValueError("Identificador inválido o repetido")
        seen.add(name)
        control, changed = case["control"], case["orthographic"]
        if not re.fullmatch(r"[a-zA-Z.12]+", control + changed):
            raise ValueError("Transcripción no permitida")
        differences = [(a, b) for a, b in zip(control, changed) if a != b]
        if len(control) != len(changed) or case["letter"] not in ("b", "v"):
            raise ValueError("Contraste inválido")
        if case["letter"] == "b" and differences:
            raise ValueError("El control b debe permanecer idéntico")
        if case["letter"] == "v" and (len(differences) != 1 or differences[0] not in (("b", "v"), ("B", "v"))):
            raise ValueError("Solo se permite cambiar un b/B por v")
    return cases


def input_text(case, mode):
    return case["id"] + "." if mode == "natural" else "`[" + case[mode] + "]."


def snapshot(out):
    """Copia local del árbol visible para Git; incluye archivos aún sin commit."""
    out.mkdir(parents=True, exist_ok=False)
    names = sorted(set(git("ls-files", "--cached", "--others", "--exclude-standard", "-z").decode("utf-8").split("\0")) - {""})
    status = git("status", "--porcelain=v1", "-z").decode("utf-8")
    files, missing = [], []
    archive = out / "fuentes.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zipped:
        for name in names:
            path = ROOT / name
            if path.is_symlink():
                raise ValueError("Revisar enlace antes de respaldar: " + name)
            if path.is_file():
                zipped.write(path, name)
                files.append({"path": name, "sha256": sha(path)})
            elif not path.exists():
                missing.append(name)
    if status != git("status", "--porcelain=v1", "-z").decode("utf-8"):
        raise RuntimeError("El árbol cambió durante el respaldo; repetir en otra carpeta")
    write_json(out / "manifest.json", {
        "branch": git("branch", "--show-current").decode().strip(),
        "head": git("rev-parse", "HEAD").decode().strip(),
        "status_porcelain_z": status, "archive_sha256": sha(archive),
        "files": files, "deleted": missing,
        "scope": "Árbol de trabajo versionado y archivos no ignorados. Excluye .git y archivos ignorados; no es un respaldo de instaladores ni de claves."
    })
    with zipfile.ZipFile(archive) as zipped:
        if zipped.testzip() is not None:
            raise RuntimeError("Respaldo ZIP inválido")
        for record in files:
            if hashlib.sha256(zipped.read(record["path"])).hexdigest() != record["sha256"]:
                raise RuntimeError("Respaldo distinto del origen: " + record["path"])
    print(f"Respaldo verificado: {len(files)} archivos, {archive}")


def synthesize(args):
    """Se ejecuta como hijo para limitar bloqueos o fallos del motor nativo."""
    if os.name != "nt":
        raise RuntimeError("Este adaptador inicial usa una DLL de Windows x64")
    dll = ctypes.WinDLL(str(args.dll.resolve()))
    ptr = ctypes.c_void_p
    declarations = {
        "eciNewEx": (ptr, [ctypes.c_int]),
        "eciDelete": (ptr, [ptr]),
        "eciRegisterCallback": (None, [ptr, ptr, ptr]),
        "eciSetOutputBuffer": (ctypes.c_int, [ptr, ctypes.c_int, ptr]),
        "eciSetParam": (ctypes.c_int, [ptr, ctypes.c_int, ctypes.c_int]),
        "eciCopyVoice": (ctypes.c_int, [ptr, ctypes.c_int, ctypes.c_int]),
        "eciSetVoiceParam": (ctypes.c_int, [ptr, ctypes.c_int, ctypes.c_int, ctypes.c_int]),
        "eciAddText": (ctypes.c_int, [ptr, ctypes.c_char_p]),
        "eciSynthesize": (ctypes.c_int, [ptr]),
        "eciSynchronize": (ctypes.c_int, [ptr]),
    }
    for name, (result, arguments) in declarations.items():
        function = getattr(dll, name)
        function.restype, function.argtypes = result, arguments
    pcm = bytearray()
    buffer = (ctypes.c_short * 4096)()
    errors = []
    callback_type = ctypes.WINFUNCTYPE(ctypes.c_int, ptr, ctypes.c_int, ctypes.c_int, ptr)

    @callback_type
    def callback(handle, message, count, user):
        if message == 0:
            if not 0 <= count <= len(buffer):
                errors.append("Tamaño inválido en callback")
                return 2
            pcm.extend(bytes(buffer)[:count * 2])
        return 1

    handle = dll.eciNewEx(LANGUAGES[args.language])
    if not handle:
        raise RuntimeError("La DLL no pudo abrir el idioma solicitado")
    try:
        dll.eciRegisterCallback(handle, callback, None)
        if not dll.eciSetOutputBuffer(handle, len(buffer), buffer):
            raise RuntimeError("El motor rechazó el buffer")
        # SetOutputBuffer can reset the sample rate: set it afterwards.
        for parameter, value in ((1, int(args.mode != "natural")), (5, 11025), (8, 1)):
            if dll.eciSetParam(handle, parameter, value) == -1:
                raise RuntimeError("Parámetro rechazado: " + str(parameter))
        if not dll.eciCopyVoice(handle, 1, 0):
            raise RuntimeError("No se pudo seleccionar la voz 1")
        if dll.eciSetVoiceParam(handle, 0, 6, args.speed) == -1:
            raise RuntimeError("Velocidad rechazada")
        case = next(case for case in corpus() if case["id"] == args.case)
        if not dll.eciAddText(handle, input_text(case, args.mode).encode("cp1252")):
            raise RuntimeError("Texto rechazado")
        if not dll.eciSynthesize(handle) or not dll.eciSynchronize(handle):
            raise RuntimeError("Síntesis incompleta")
    finally:
        dll.eciDelete(handle)
    if errors or not pcm or not any(pcm):
        raise RuntimeError("Audio vacío o callback inválido: " + repr(errors))
    with wave.open(str(args.wav), "wb") as wav:
        wav.setparams((1, 2, 11025, 0, "NONE", "not compressed"))
        wav.writeframes(pcm)


def audio_info(path):
    with wave.open(str(path), "rb") as wav:
        if (wav.getnchannels(), wav.getsampwidth(), wav.getframerate()) != (1, 2, 11025):
            raise ValueError("Formato WAV inesperado")
        data = wav.readframes(wav.getnframes())
    samples = array.array("h", data)
    if sys.byteorder != "little":
        samples.byteswap()
    return {"pcm_sha256": hashlib.sha256(data).hexdigest(), "samples": len(samples),
            "duration_s": len(samples) / 11025, "peak": max(abs(x) for x in samples),
            "rms": math.sqrt(sum(x*x for x in samples) / len(samples)),
            "clipped_samples": sum(x in (-32768, 32767) for x in samples)}


def listening_page(out, count):
    rows = "\n".join(f'<fieldset><legend>Muestra {n:03}</legend><audio controls preload="none" src="blind/trial-{n:03}.wav"></audio><label> Letra percibida <select name="trial-{n:03}"><option value="">Sin responder</option><option>b</option><option>v</option></select></label></fieldset>' for n in range(1, count+1))
    page = '''<!doctype html><html lang="es"><meta charset="utf-8"><title>Prueba de escucha b/v</title>
<style>body{font:1.15rem system-ui;max-width:52rem;margin:2rem auto;padding:1rem}fieldset{margin:1rem 0}button,select{font:inherit}audio{display:block;width:100%}</style>
<h1>Prueba de escucha b/v</h1><p>Escucha cada muestra y elige la letra que percibes. Puedes repetirla o dejarla sin responder. Se mezclan controles y versiones experimentales; algunas muestras no permiten distinguir la escritura por su sonido.</p>
<p>Antes de la prueba, <a href="entrenar.html">familiarízate con los ejemplos de entrenamiento</a>. Las velocidades configuradas son 180 y 400 palabras por minuto; no son una medición de la velocidad efectiva de cada palabra aislada.</p>
<form>ROWS<label>Fatiga o dificultad (opcional)<br><textarea id="notes" rows="4" cols="45"></textarea></label><p><button type="button" id="save">Descargar respuestas CSV</button></p><p id="status" role="status"></p></form>
<script>document.getElementById('save').onclick=()=>{let note='"'+document.getElementById('notes').value.replaceAll('"','""')+'"';let text='trial,response,notes\\n';document.querySelectorAll('select').forEach(s=>text+=s.name+','+s.value+','+note+'\\n');let blob=new Blob([text],{type:'text/csv;charset=utf-8'});let url=URL.createObjectURL(blob);let a=document.createElement('a');a.href=url;a.download='respuestas.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);document.getElementById('status').textContent='Respuestas y observaciones exportadas.';};</script></html>'''.replace("ROWS", rows)
    (out / "escuchar.html").write_text(page, encoding="utf-8")
    examples = []
    for case in corpus():
        for speed in (180, 400):
            examples.append(f'<section><h2>{case["id"]}, velocidad configurada {speed}</h2>')
            for mode, title in (("natural", "Lectura normal"), ("control", "Control fonético"), ("orthographic", "Contraste ortográfico experimental")):
                examples.append(f'<p>{title}</p><audio controls preload="none" aria-label="{case["id"]}, {title}, {speed}" src="{case["id"]}-{speed}-{mode}.wav"></audio>')
            examples.append('</section>')
    (out / "entrenar.html").write_text('<!doctype html><html lang="es"><meta charset="utf-8"><title>Entrenamiento b/v</title><style>body{font:1.15rem system-ui;max-width:52rem;margin:2rem auto;padding:1rem}audio{width:100%}section{margin:2rem 0}</style><h1>Entrenamiento b/v</h1><p>Hipótesis: usar el fonema interno v para señalar la letra escrita. Los casos b deben conservar su audio. Esta propuesta no describe una distinción normativa del español ni demuestra un beneficio educativo.</p><p><a href="escuchar.html">Ir a la prueba sin etiquetas</a></p>' + '\n'.join(examples) + '</html>', encoding="utf-8")


def generate(args):
    cases = corpus()
    dll = args.dll.resolve(strict=True)
    args.out.mkdir(parents=True, exist_ok=False)
    records = []
    try:
        for speed in (180, 400):
            for case in cases:
                for mode in MODES:
                    name = f'{case["id"]}-{speed}-{mode}.wav'
                    command = [sys.executable, str(Path(__file__).resolve()), "_synth", "--dll", str(dll),
                               "--case", case["id"], "--mode", mode, "--speed", str(speed),
                               "--language", args.language, "--wav", str(args.out.resolve() / name)]
                    start = time.perf_counter()
                    env = {k: v for k, v in os.environ.items() if not k.startswith("EVV_")}
                    completed = subprocess.run(command, env=env, capture_output=True, timeout=30)
                    if completed.returncode:
                        raise RuntimeError(completed.stderr.decode("utf-8", errors="replace"))
                    record = {"file": name, "word": case["id"], "letter": case["letter"], "pair": case["pair"],
                              "mode": mode, "speed_setting_wpm": speed, "input": input_text(case, mode),
                              "process_wall_s": time.perf_counter() - start, **audio_info(args.out / name)}
                    if record["clipped_samples"]:
                        raise RuntimeError("Se detectó saturación PCM: " + name)
                    records.append(record)
                    print(name, flush=True)
        checks = []
        for speed in (180, 400):
            for case in cases:
                a, b = [next(r for r in records if r["word"] == case["id"] and r["speed_setting_wpm"] == speed and r["mode"] == m) for m in ("control", "orthographic")]
                natural = next(r for r in records if r["word"] == case["id"] and r["speed_setting_wpm"] == speed and r["mode"] == "natural")
                baseline_equal = natural["pcm_sha256"] == a["pcm_sha256"]
                equal = a["pcm_sha256"] == b["pcm_sha256"]
                expected_equal = case["letter"] == "b"
                checks.append({"word": case["id"], "speed": speed, "natural_control_equal": baseline_equal,
                               "expected_equal": expected_equal, "equal": equal, "pass": equal == expected_equal and baseline_equal})
        blind = [r for r in records if r["mode"] != "natural"]
        random.Random(args.seed).shuffle(blind)
        (args.out / "blind").mkdir()
        key = []
        for n, record in enumerate(blind, 1):
            trial = f"trial-{n:03}"
            shutil.copyfile(args.out / record["file"], args.out / "blind" / (trial + ".wav"))
            key.append({"trial": trial, **record})
        write_json(args.out / "clave-privada.json", key)
        listening_page(args.out, len(blind))
        report = {"status": "technical_pass" if all(c["pass"] for c in checks) else "technical_fail",
                  "dll": str(dll), "dll_sha256": sha(dll), "corpus_sha256": sha(HERE / "corpus.json"),
                  "runner_sha256": sha(Path(__file__)), "head": git("rev-parse", "HEAD").decode().strip(),
                  "branch": git("branch", "--show-current").decode().strip(), "language": args.language,
                  "seed": args.seed, "sample_rate": 11025, "voice_preset": 1,
                  "caveat": "Diferencia PCM no demuestra reconocimiento ni beneficio educativo. Tiempo de proceso incluye arranque; no mide latencia de streaming.",
                  "checks": checks, "records": records}
        write_json(args.out / "report.json", report)
        if report["status"] != "technical_pass":
            raise RuntimeError("Contrastes fuera de lo esperado; revisar report.json")
    except Exception as error:
        write_json(args.out / "failure.json", {"error": str(error), "completed": records})
        raise
    print("Verificación técnica correcta. Falta evaluación de escucha.")


def score(args):
    key = {r["trial"]: r for r in json.loads((args.run / "clave-privada.json").read_text(encoding="utf-8"))}
    answers = {}
    with args.responses.open(encoding="utf-8-sig", newline="") as source:
        for row in csv.DictReader(source):
            trial, response = row["trial"], row["response"].strip().lower()
            if trial not in key or trial in answers or response not in ("", "b", "v"):
                raise ValueError("Respuesta duplicada o inválida: " + trial)
            answers[trial] = response
    groups = {}
    for trial, record in key.items():
        group = groups.setdefault(f'{record["mode"]}-{record["speed_setting_wpm"]}', {"total": 0, "answered": 0, "correct": 0})
        group["total"] += 1
        answer = answers.get(trial, "")
        group["answered"] += bool(answer)
        group["correct"] += answer == record["letter"]
    for group in groups.values():
        group["accuracy_all_trials"] = group["correct"] / group["total"]
    print(json.dumps(groups, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    checkpoint = commands.add_parser("snapshot")
    checkpoint.add_argument("--out", type=Path, required=True)
    make = commands.add_parser("generate")
    make.add_argument("--out", type=Path, required=True)
    make.add_argument("--dll", type=Path, default=DEFAULT_DLL)
    make.add_argument("--language", choices=LANGUAGES, default="esus")
    make.add_argument("--seed", type=int, default=20261014)
    worker = commands.add_parser("_synth", help=argparse.SUPPRESS)
    worker.add_argument("--dll", type=Path, required=True)
    worker.add_argument("--wav", type=Path, required=True)
    worker.add_argument("--case", required=True)
    worker.add_argument("--mode", choices=MODES, required=True)
    worker.add_argument("--speed", type=int, required=True)
    worker.add_argument("--language", choices=LANGUAGES, required=True)
    evaluate = commands.add_parser("score")
    evaluate.add_argument("--run", type=Path, required=True)
    evaluate.add_argument("--responses", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "snapshot": snapshot(args.out)
    elif args.command == "generate": generate(args)
    elif args.command == "_synth": synthesize(args)
    else: score(args)


if __name__ == "__main__":
    main()
