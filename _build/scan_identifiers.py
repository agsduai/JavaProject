# -*- coding: utf-8 -*-
"""扫描示例代码中的中文标识符（排除注释与字符串字面量）。"""
import re, glob, os, collections

ROOT = r"D:\Javalianxi\JavaProject\src\main\java"
CJK = re.compile(r"[\u4e00-\u9fff]")


def strip_comments_strings(t):
    out = []
    i, n = 0, len(t)
    while i < n:
        c = t[i]
        if t.startswith("//", i):
            j = t.find("\n", i)
            i = n if j < 0 else j
        elif t.startswith("/*", i):
            j = t.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif c == '"':
            if t.startswith('"""', i):
                j = t.find('"""', i + 3)
                i = n if j < 0 else j + 3
            else:
                j = i + 1
                while j < n:
                    if t[j] == "\\":
                        j += 2
                        continue
                    if t[j] == '"':
                        break
                    j += 1
                i = j + 1
            out.append(" ")
        elif c == "'":
            j = i + 1
            while j < n:
                if t[j] == "\\":
                    j += 2
                    continue
                if t[j] == "'":
                    break
                j += 1
            i = j + 1
            out.append(" ")
        else:
            out.append(c)
            i += 1
    return "".join(out)


hits = collections.Counter()
files_with = []
for f in sorted(glob.glob(os.path.join(ROOT, "**", "*.java"), recursive=True)):
    code = strip_comments_strings(open(f, encoding="utf-8").read())
    found = CJK.findall(code)
    if found:
        words = re.findall(r"[\u4e00-\u9fff]+", code)
        hits.update(words)
        files_with.append((os.path.relpath(f, ROOT), len(found), words[:6]))

print("含中文标识符的文件数:", len(files_with), "/ 170")
print("不同中文标识符个数:", len(hits))
print("\n--- 出现最多的 30 个 ---")
for w, c in hits.most_common(30):
    print(f"{c:4d}  {w}")
print("\n--- 前 12 个文件示例 ---")
for rel, n, ws in files_with[:12]:
    print(f"{n:3d}  {rel}  {ws}")
