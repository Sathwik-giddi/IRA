#!/usr/bin/env python3
"""Drive the debug harness on-device: push batch, run, sample, pull, score."""
import argparse
import json
import os
import re
import subprocess
import sys
import threading
import time
from datetime import datetime

ADB = os.environ.get("ADB", "/opt/homebrew/share/android-commandlinetools/platform-tools/adb")
PKG = "com.voicepoco.app"
ACT = f"{PKG}/.HarnessActivity"


def sh(cmd, timeout=120, text=True):
    return subprocess.run(cmd, shell=True, capture_output=True, text=text, timeout=timeout)


def adb(cmd, timeout=120):
    return sh(f"{ADB} {cmd}", timeout=timeout)


def meminfo_kb():
    out = adb("shell dumpsys meminfo com.voicepoco.app").stdout
    m = re.search(r"TOTAL PSS:\s*(\d+)", out)
    if m:
        return int(m.group(1))
    m = re.search(r"TOTAL\s+(\d+)", out)
    return int(m.group(1)) if m else None


def max_temp_c():
    out = adb("shell dumpsys thermalservice").stdout
    vals = [float(x) for x in re.findall(r"Temperature\{[^}]*mValue=([\d.]+)", out)]
    if not vals:
        vals = [float(x) / 10.0 for x in re.findall(r"mValue=([\d.]+)", out)]
    return round(max(vals), 1) if vals else None


def charge_uah():
    for path in ("/sys/class/power_supply/battery/charge_counter",
                 "/sys/class/power_supply/battery/charge_counter_uah"):
        out = adb(f"shell cat {path}").stdout.strip()
        try:
            return int(out)
        except ValueError:
            pass
    out = adb("shell dumpsys battery").stdout
    m = re.search(r"Charge counter:\s*(\d+)", out)
    return int(m.group(1)) if m else None


def sample_until(stop, peak):
    while not stop.is_set():
        v = meminfo_kb()
        if v and v > peak[0]:
            peak[0] = v
        time.sleep(0.25)


FILES = f"/data/user/0/{PKG}/files"

def push_batch(rows, local):
    with open(local, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps({"id": r["id"], "utterance": r["utterance"]}, ensure_ascii=False) + "\n")
    with open(local, "rb") as fh:
        p = subprocess.run(
            [ADB, "shell", f"run-as {PKG} sh -c 'cat > {FILES}/vp_cmds.jsonl'"],
            stdin=fh, stderr=subprocess.PIPE,
        )
    if p.returncode != 0:
        raise RuntimeError("push failed: " + p.stderr.decode(errors="replace"))
    adb(f"shell run-as {PKG} rm -f {FILES}/vp_results.jsonl")
    adb(f"shell run-as {PKG} rm -f {FILES}/vp_marker.txt")


def pss_kb():
    out = adb(f"shell dumpsys meminfo {PKG}").stdout
    m = re.search(r"TOTAL\s+(\d+)", out)
    return int(m.group(1)) if m else None


def pull_results(local):
    with open(local, "wb") as f:
        p = subprocess.run(
            f"{ADB} exec-out run-as {PKG} cat {FILES}/vp_results.jsonl",
            shell=True, stdout=f, stderr=subprocess.PIPE,
        )
    return p.returncode == 0 and os.path.getsize(local) > 0


def run_once(rows, backend, tag, outdir):
    local_in = os.path.join(outdir, f"cmds-{tag}.jsonl")
    local_out = os.path.join(outdir, f"{tag}.jsonl")
    push_batch(rows, local_in)

    adb("logcat -c")
    peak = [None]
    free = [None]
    stop = threading.Event()

    def sample():
        while not stop.is_set():
            v = pss_kb()
            if v and (peak[0] is None or v > peak[0]):
                peak[0] = v
            m = meminfo_kb()
            if m and (free[0] is None or m < free[0]):
                free[0] = m
            time.sleep(1.0)

    t = threading.Thread(target=sample, daemon=True)
    t.start()

    adb(f'shell am start -n {ACT} --es backend {backend}', timeout=60)

    deadline = time.time() + max(180, len(rows) * 12)
    done = False
    while time.time() < deadline:
        out = adb(f"shell run-as {PKG} cat {FILES}/vp_marker.txt").stdout
        if "done" in out:
            done = True
            break
        log = adb("logcat -d -s POCO_HARNESS:I").stdout
        if "HARNESS_FAIL" in log:
            break
        if not adb("shell pidof %s" % PKG).stdout.strip():
            break
        time.sleep(1.0)

    stop.set()
    t.join(timeout=3)
    log = adb("logcat -d -s POCO_HARNESS:I").stdout
    load_ms = None
    m = re.search(r"HARNESS_MODEL_LOAD_MS=(\d+)", log)
    if m:
        load_ms = int(m.group(1))
    ok = pull_results(local_out)
    n = sum(1 for _ in open(local_out, encoding="utf-8")) if ok else 0
    print(f"[{tag}] done={done} rows={n} peak_pss={peak[0] // 1024 if peak[0] else None}MB min_free={free[0] // 1024 if free[0] else None}MB load_ms={load_ms}")
    return {"tag": tag, "done": done, "rows": n, "peak_pss_kb": peak[0], "min_free_kb": free[0],
            "load_ms": load_ms, "results": local_out}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dataset", default="harness/datasets/commands.v1.jsonl")
    ap.add_argument("--backend", default="rules", choices=["rules", "model"])
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--cooldown", type=int, default=30)
    ap.add_argument("--label", default=None)
    ap.add_argument("--outdir", default="harness/results")
    a = ap.parse_args()

    label = a.label or f"baseline-{a.backend}"
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    outdir = os.path.join(a.outdir, f"{label}-{stamp}")
    os.makedirs(outdir, exist_ok=True)

    rows = [json.loads(l) for l in open(a.dataset, encoding="utf-8") if l.strip()]

    state = adb("get-state").stdout.strip()
    if state != "device":
        sys.exit(f"device not ready: {state!r}")

    device = adb("shell getprop ro.product.model").stdout.strip()
    thermal_before = max_temp_c()
    charge_before = charge_uah()

    runs = []
    for i in range(1, a.runs + 1):
        r = run_once(rows, a.backend, f"run{i}", outdir)
        runs.append(r)
        if i < a.runs:
            time.sleep(a.cooldown)

    thermal_after = max_temp_c()
    charge_after = charge_uah()
    delta_mah = None
    if charge_before is not None and charge_after is not None:
        delta_mah = round((charge_before - charge_after) / 1000.0, 2)

    meta = {
        "device": device,
        "backend": a.backend,
        "runs": a.runs,
        "rows": len(rows),
        "thermal_before": thermal_before,
        "thermal_after": thermal_after,
        "battery_delta_mah": delta_mah,
        "peak_pss_kb": max([r["peak_pss_kb"] for r in runs] or [0]) or None,
        "min_free_kb": min([r["min_free_kb"] for r in runs if r.get("min_free_kb")] or [0]) or None,
        "runs_detail": runs,
        "timestamp": datetime.now().isoformat(timespec="seconds"),
    }
    metap = os.path.join(outdir, "meta.json")
    json.dump(meta, open(metap, "w"), indent=2)

    result_files = [r["results"] for r in runs if r["rows"] > 0]
    if not result_files:
        sys.exit("no results pulled")

    cmd = [
        sys.executable, "harness/scorer/score.py",
        "--dataset", a.dataset,
        "--results", *result_files,
        "--meta", metap,
        "--label", label,
        "--outdir", "harness/results",
    ]
    p = subprocess.run(cmd)
    print("outdir:", outdir)
    sys.exit(p.returncode)


if __name__ == "__main__":
    main()
