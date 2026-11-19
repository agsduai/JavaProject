# -*- coding: utf-8 -*-
"""全量运行冒烟：跑指定包前缀下的全部 main，stdin 喂通用输入。"""
import io
import os
import subprocess
import sys

JAVA = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe"
CP = r"D:\Javalianxi\_build\out_pkg"
SRC = r"D:\Javalianxi\JavaProject\src\main\java"

# 通用模拟输入：数字/字符串混合，覆盖 Scanner 常见读取
STDIN_FEED = "\n".join(
    ["3", "2", "5", "100", "alice", "1", "2", "3", "50", "u1", "9001",
     "6900", "PAID", "7", "25", "abc", "0", "-1", "999", "done"] * 3) + "\n"

prefixes = sys.argv[1:] or ["com/heima"]

# 收集含 main 的类
classes = []
for root, dirs, files in os.walk(SRC):
    rel = os.path.relpath(root, SRC).replace("\\", "/")
    if not any(rel.startswith(p) or rel + "/" == p or p.startswith(rel) for p in prefixes) \
            and rel != "com/heima":
        pass
    for f in files:
        if not f.endswith(".java"):
            continue
        path = os.path.join(root, f)
        text = io.open(path, encoding="utf-8", errors="replace").read()
        if "static void main" not in text:
            continue
        pkg = rel.replace("/", ".")
        classes.append((pkg, f[:-5]))

# 过滤前缀
def matched(pkg):
    for p in prefixes:
        if pkg.startswith(p.replace("/", ".")):
            return True
    return False

classes = [(pkg, name) for pkg, name in classes if matched(pkg)]
classes.sort()

print("classes to run: %d (prefixes=%s)" % (len(classes), prefixes), flush=True)

fails = []
ok = 0
interact = []
for i, (pkg, name) in enumerate(classes):
    cls = pkg + "." + name
    try:
        r = subprocess.run([JAVA, "-Dstdout.encoding=UTF-8", "-cp", CP, cls],
                           input=STDIN_FEED.encode("utf-8"),
                           capture_output=True, timeout=90)
    except subprocess.TimeoutExpired:
        interact.append(cls)
        print("TIMEOUT %s" % cls, flush=True)
        continue
    except Exception as e:
        fails.append((cls, repr(e)))
        print("ERROR %s" % cls, flush=True)
        continue
    if r.returncode != 0:
        err = r.stderr.decode("utf-8", errors="replace")
        fails.append((cls, err[:800]))
        print("FAIL(%d) %s" % (r.returncode, cls), flush=True)
    else:
        ok += 1
        if i % 50 == 0:
            print("... progress %d/%d (ok=%d)" % (i + 1, len(classes), ok), flush=True)

print()
print("RUN_ALL pass=%d fail=%d timeout=%d total=%d" % (ok, len(fails), len(interact), len(classes)),
      flush=True)
if interact:
    print("TIMEOUT（可能交互式/长循环，需单独处理）:")
    for c in interact:
        print("  " + c)
if fails:
    print("FAILS:")
    for cls, msg in fails:
        print("=" * 60)
        print(cls)
        print(msg.encode("utf-8", "replace").decode("utf-8"))
