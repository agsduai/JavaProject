# -*- coding: utf-8 -*-
"""从 staging markdown 里抽出每个 ```java 代码块，单独编译 + 运行。

用法: python -I probe.py _ch35_a.md [--run]
"""
import io
import os
import re
import shutil
import subprocess
import sys

JAVAC = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\javac.exe"
JAVA = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe"
BASE = os.path.dirname(os.path.abspath(__file__))


def blocks(path):
    text = io.open(path, encoding="utf-8").read()
    out = []
    for m in re.finditer(r"^### (\d+)-(\d+)\s+(.+?)\s*$", text, re.M):
        out.append((m.group(1), m.group(2), m.group(3), m.start()))
    result = []
    for i, (ch, no, title, pos) in enumerate(out):
        end = out[i + 1][3] if i + 1 < len(out) else len(text)
        seg = text[pos:end]
        jm = re.search(r"```java\n(.*?)\n```", seg, re.S)
        if not jm:
            result.append((ch, no, title, None))
            continue
        result.append((ch, no, title, jm.group(1)))
    return result


def main():
    path = sys.argv[1]
    do_run = "--run" in sys.argv
    work = os.path.join(BASE, "_probe_tmp")
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work)
    ok = 0
    fail = 0
    for ch, no, title, code in blocks(path):
        tag = "%s-%s" % (ch, no)
        if code is None:
            print("%s  SKIP(no java)  %s" % (tag, title))
            fail += 1
            continue
        m = re.search(r"public\s+(?:final\s+|abstract\s+)?(?:class|interface|enum)\s+(\w+)", code)
        if not m:
            print("%s  SKIP(no public type)  %s" % (tag, title))
            fail += 1
            continue
        name = m.group(1)
        d = os.path.join(work, tag)
        os.makedirs(d, exist_ok=True)
        src = os.path.join(d, name + ".java")
        io.open(src, "w", encoding="utf-8", newline="\n").write(code + "\n")
        cp = subprocess.run(
            [JAVAC, "-J-Duser.language=en", "-J-Duser.country=US", "--release", "21",
             "-encoding", "UTF-8", "-Xlint:all", "-parameters", "-d", d, src],
            capture_output=True, text=True, encoding="utf-8", errors="replace")
        warn = cp.stderr.strip()
        if cp.returncode != 0 or warn:
            print("%s  COMPILE_FAIL  %s" % (tag, title))
            print(warn[:3000].encode("ascii","replace").decode("ascii"))
            fail += 1
            continue
        line = "%s  COMPILE_OK  %s" % (tag, title)
        if do_run:
            rp = subprocess.run(
                [JAVA, "-Dstdout.encoding=UTF-8", "-cp", d, name],
                capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=180)
            if rp.returncode != 0:
                print("%s  RUN_FAIL exit=%d" % (tag, rp.returncode))
                print(rp.stdout[-2000:].encode("ascii","replace").decode("ascii"))
                print(rp.stderr[-3000:].encode("ascii","replace").decode("ascii"))
                fail += 1
                continue
            lines = len([x for x in rp.stdout.splitlines() if x.strip()])
            line += "  RUN_OK(%d lines)" % lines
        print(line)
        ok += 1
    print("total=%d pass=%d fail=%d" % (ok + fail, ok, fail))
    return 0 if fail == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
