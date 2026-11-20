# -*- coding: utf-8 -*-
"""把「本章综合练习」也写进 JavaProject 每章的 README.md（章节索引页）。

内容与章节笔记 md、最后一课 java 注释块保持一致，另附指向笔记与源码的链接。
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

MARK = "## 本章综合练习"


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
    """从每章 README 的课程表里取最后一课的 java 文件名。"""
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


def note_name(ch):
    hits = glob.glob(os.path.join(NOTES, "第%02d章-*.md" % ch))
    if len(hits) != 1:
        raise RuntimeError("第%d章笔记定位失败：%r" % (ch, hits))
    return os.path.basename(hits[0])


def build(item, ch, java_name):
    note = note_name(ch)
    note_rel = "../../../../../../../黑马Java课程笔记/" + note
    lines = [MARK, "", "### 综合题：%s" % item["title"], "", "**场景**", "", item["scene"], "", "**任务**"]
    for i, t in enumerate(item["tasks"], 1):
        lines.append("%d. %s" % (i, t))
    lines += ["", "**验收清单**"]
    for a in item["acceptance"]:
        lines.append("- [ ] %s" % a)
    lines += ["", "> 同一道题也写在 [%s](%s) 文件末尾的注释块中，无参考答案；完整笔记见 [%s](%s) 的「本章综合练习」一节。"
              % (java_name, java_name, note, note_rel), ""]
    return "\n".join(lines)


def main():
    items = load_items()
    last_java = load_last_java()
    done = skip = 0
    problems = []

    for item in items:
        ch = item["ch"]
        java_name = last_java.get(ch)
        if not java_name:
            problems.append("ch%02d: 缺少最后一课映射" % ch)
            continue
        path = os.path.join(JAVA_ROOT, "ch%02d" % ch, "README.md")
        if not os.path.exists(path):
            problems.append("ch%02d: README 不存在" % ch)
            continue
        text = io.open(path, encoding="utf-8").read()
        if MARK in text:
            skip += 1
            continue
        if not text.endswith("\n"):
            text += "\n"
        text += "\n" + build(item, ch, java_name)
        io.open(path, "w", encoding="utf-8", newline="\n").write(text)
        done += 1

    print("README 新增=%d 跳过=%d" % (done, skip))
    if problems:
        print("问题：")
        for p in problems:
            print(" -", p)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
