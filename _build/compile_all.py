# -*- coding: utf-8 -*-
import os, glob, subprocess, shutil
from concurrent.futures import ThreadPoolExecutor

ROOT = r"D:\Javalianxi\JavaProject"
WORK = r"D:\Javalianxi\_build\out"
REPORT = r"D:\Javalianxi\_build\compile_report.txt"
if os.path.exists(WORK):
    shutil.rmtree(WORK)
os.makedirs(WORK)

files = sorted(glob.glob(os.path.join(ROOT, "**", "*.java"), recursive=True))


def check(pair):
    i, f = pair
    d = os.path.join(WORK, str(i))
    os.makedirs(d, exist_ok=True)
    r = subprocess.run(["javac", "-encoding", "UTF-8", "-d", d, f],
                       capture_output=True)
    err = r.stderr.decode("gbk", errors="replace") if r.stderr else ""
    return (os.path.relpath(f, ROOT), r.returncode, err)


with ThreadPoolExecutor(max_workers=8) as ex:
    results = list(ex.map(check, enumerate(files, 1)))

fails = [(f, e) for f, rc, e in results if rc != 0]
lines = [f"total={len(files)} pass={len(files)-len(fails)} fail={len(fails)}"]
for f, e in fails:
    lines.append("=" * 70)
    lines.append("FAIL: " + f)
    lines.append(e[:4000])
open(REPORT, "w", encoding="utf-8").write("\n".join(lines))
print(f"total={len(files)} pass={len(files)-len(fails)} fail={len(fails)}")
for f, _ in fails:
    print("FAIL:", f)
