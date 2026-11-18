# -*- coding: utf-8 -*-
"""整体编译校验：javac 一次性编译全部源文件（包结构校验 + 重名校验）。"""
import os, glob, subprocess, shutil, sys

ROOT = r"D:\Javalianxi\JavaProject"
SRC = os.path.join(ROOT, "src", "main", "java")
WORK = r"D:\Javalianxi\_build\out_pkg"
REPORT = r"D:\Javalianxi\_build\compile_report.txt"
JAVAC = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\javac.exe"

if os.path.exists(WORK):
    shutil.rmtree(WORK, ignore_errors=True)
os.makedirs(WORK)

files = sorted(glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True))
print("源文件数:", len(files))

r = subprocess.run([JAVAC, "-J-Duser.language=en", "-J-Duser.country=US",
                    "--release", "21", "-encoding", "UTF-8", "-Xlint:all", "-parameters",
                    "-d", WORK] + files, capture_output=True)
# javac 在中文 Windows 下可能按 GBK 输出诊断信息，先按 GBK 解码，失败再退回 UTF-8
raw = r.stdout + r.stderr
try:
    out = raw.decode("gbk")
except UnicodeDecodeError:
    out = raw.decode("utf-8", errors="replace")
lines = [f"total={len(files)} exit={r.returncode}", out]
open(REPORT, "w", encoding="utf-8").write("\n".join(lines))
print(f"total={len(files)} exit={r.returncode}")
if out.strip():
    # 控制台编码可能是 GBK，打印前先转义掉无法表示的字符，避免 UnicodeEncodeError
    enc = sys.stdout.encoding or "utf-8"
    print(out[:6000].encode(enc, errors="replace").decode(enc, errors="replace"))
