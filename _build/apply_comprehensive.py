# -*- coding: utf-8 -*-
"""为每一章追加「本章综合练习」：章节笔记 md + 该章最后一课的 java 源文件。

幂等：已存在标记则跳过。
"""
import glob
import importlib.util
import io
import os
import re
import sys

ROOT = r"D:/Javalianxi"
NOTES = os.path.join(ROOT, "黑马Java课程笔记")
JAVA_ROOT = os.path.join(ROOT, "JavaProject", "src", "main", "java", "com", "heima")

MD_MARK = "## 本章综合练习"
JAVA_MARK = "本章综合练习（无参考答案）"


def load_items():
    items = []
    for i in range(1, 5):
        path = os.path.join(ROOT, "_build", "_comp", "part%d.py" % i)
        spec = importlib.util.spec_from_file_location("part%d" % i, path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        items.extend(mod.ITEMS)
    items.sort(key=lambda x: x["ch"])
    return items


def load_last_java():
    """从每章 README 的课程表里取最后一课的 java 文件名（权威来源）。"""
    mapping = {}
    for d in sorted(glob.glob(os.path.join(JAVA_ROOT, "ch*"))):
        m = re.search(r"ch(\d+)$", d)
        if not m:
            continue
        ch = int(m.group(1))
        readme = os.path.join(d, "README.md")
        if not os.path.exists(readme):
            continue
        text = io.open(readme, encoding="utf-8").read()
        rows = re.findall(r"^\|\s*(\d+)-(\d+)\s*\|[^|]*\|\s*\[([^\]]+)\]", text, re.M)
        rows = [(int(a), int(b), c) for a, b, c in rows if int(a) == ch]
        if rows:
            mapping[ch] = max(rows, key=lambda r: r[1])[2]
    return mapping


def md_path(ch):
    hits = glob.glob(os.path.join(NOTES, "第%02d章-*.md" % ch))
    if len(hits) != 1:
        raise RuntimeError("第%d章 md 定位失败：%r" % (ch, hits))
    return hits[0]


def build_md(item):
    lines = [MD_MARK, "", "### 综合题：%s" % item["title"], "", "**场景**", "", item["scene"], "", "**任务**"]
    for i, t in enumerate(item["tasks"], 1):
        lines.append("%d. %s" % (i, t))
    lines += ["", "**验收清单**"]
    for a in item["acceptance"]:
        lines.append("- [ ] %s" % a)
    lines.append("")
    return "\n".join(lines)


def _safe(text):
    """注释块里不能出现 */，否则会提前闭合导致语法错误。"""
    return text.replace("*/", "*／")


def build_java(item, ch):
    bar = " * " + "=" * 60
    lines = ["", "/*", bar, " * " + JAVA_MARK, " * " + "-" * 60,
             " * 【场景】", " * " + _safe(item["scene"]), " * 【任务】"]
    for i, t in enumerate(item["tasks"], 1):
        lines.append(" * %d. %s" % (i, _safe(t)))
    lines.append(" * 【验收清单】")
    for a in item["acceptance"]:
        lines.append(" * [ ] %s" % _safe(a))
    lines += [bar, " */", ""]
    return "\n".join(lines)


def main():
    items = load_items()
    last_java = load_last_java()
    md_done = md_skip = java_done = java_skip = 0
    problems = []

    for item in items:
        ch = item["ch"]

        # ---------- md ----------
        path = md_path(ch)
        text = io.open(path, encoding="utf-8").read()
        if MD_MARK in text:
            md_skip += 1
        else:
            if not text.endswith("\n"):
                text += "\n"
            text += "\n" + build_md(item)
            io.open(path, "w", encoding="utf-8", newline="\n").write(text)
            md_done += 1

        # ---------- java ----------
        name = last_java.get(ch)
        if not name:
            problems.append("ch%02d: 缺少 last java 映射" % ch)
            continue
        jpath = os.path.join(JAVA_ROOT, "ch%02d" % ch, name)
        if not os.path.exists(jpath):
            problems.append("ch%02d: 文件不存在 %s" % (ch, jpath))
            continue
        jtext = io.open(jpath, encoding="utf-8").read()
        if JAVA_MARK in jtext:
            java_skip += 1
        else:
            if not jtext.endswith("\n"):
                jtext += "\n"
            jtext += build_java(item, ch)
            io.open(jpath, "w", encoding="utf-8", newline="\n").write(jtext)
            java_done += 1

    print("md   新增=%d 跳过=%d" % (md_done, md_skip))
    print("java 新增=%d 跳过=%d" % (java_done, java_skip))
    if problems:
        print("问题：")
        for p in problems:
            print(" -", p)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
