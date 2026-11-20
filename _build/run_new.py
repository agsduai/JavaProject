# -*- coding: utf-8 -*-
"""扩展章节生成器：一章一个内容模块（_build/_new/chNN.py），
本脚本负责把模块写成三处：
  1) 黑马Java课程笔记/第NN章-主题.md
  2) JavaProject/src/main/java/com/heima/chNN/<Class>.java（每课一个）
  3) JavaProject/src/main/java/com/heima/chNN/README.md

模块契约（_build/_new/ch81.py）：
    CH = 81
    TITLE = "缓存架构与一致性设计"
    INTRO = "一段介绍，以「所有示例仍是纯 JDK：…」结尾"
    LESSONS = [dict(cls=..., title=..., points=[...], code=r'''...''', exercises=[...]), ...]
    COMP = dict(title=..., scene=..., tasks=[...], acceptance=[...])
"""
import glob
import importlib.util
import io
import os
import sys

ROOT = r"D:/Javalianxi"
NOTES = os.path.join(ROOT, "黑马Java课程笔记")
JAVA_ROOT = os.path.join(ROOT, "JavaProject", "src", "main", "java", "com", "heima")
NEW = os.path.join(ROOT, "_build", "_new")

COURSE = "黑马程序员《2026版 AI+Java 零基础全套视频课程》"
COURSE2 = "B站 BV1TJxCzSEEZ（阿玮老师）"
BAR = " * " + "=" * 60
SUB = " * " + "-" * 60


class Chapter:
    pass


def _exec(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def load(ch):
    """chNN.py 提供 CH/TITLE/INTRO/COMP；chNN_l*.py 按文件名顺序提供 LESSONS。"""
    meta = _exec(os.path.join(NEW, "ch%d.py" % ch), "ch%d" % ch)
    parts = sorted(glob.glob(os.path.join(NEW, "ch%d_l*.py" % ch)))
    if not parts:
        raise SystemExit("第%d章缺少内容分片 ch%d_l*.py" % (ch, ch))
    lessons = []
    for p in parts:
        lessons.extend(_exec(p, os.path.basename(p)[:-3]).LESSONS)
    c = Chapter()
    c.CH = meta.CH
    c.TITLE = meta.TITLE
    c.INTRO = meta.INTRO
    c.COMP = meta.COMP
    c.LESSONS = lessons
    return c


def safe(text):
    return text.replace("*/", "*／")


def java_header(ch, num, lesson):
    lines = ["/**", BAR,
             " * 课程来源：" + COURSE,
             " *            " + COURSE2,
             " * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容",
             " * 课号：%d-%02d" % (ch, num),
             " * 课名：" + lesson["title"],
             SUB,
             " * 知识点："]
    for p in lesson["points"]:
        lines.append(" *   - " + safe(p))
    lines += [BAR, " */", ""]
    return "\n".join(lines)


def java_tail(lesson, comp):
    lines = ["", "/*", BAR,
             " * 课后练习题（无参考答案）", SUB]
    for i, e in enumerate(lesson["exercises"], 1):
        lines.append(" * %d. %s" % (i, safe(e)))
    lines += [BAR, " */", ""]
    if comp:
        lines += ["/*", BAR, " * 本章综合练习（无参考答案）", SUB,
                  " * 【场景】", " * " + safe(comp["scene"]), " * 【任务】"]
        for i, t in enumerate(comp["tasks"], 1):
            lines.append(" * %d. %s" % (i, safe(t)))
        lines.append(" * 【验收清单】")
        for a in comp["acceptance"]:
            lines.append(" * [ ] " + safe(a))
        lines += [BAR, " */", ""]
    return "\n".join(lines)


def build_java(ch, num, lesson, comp, is_last):
    body = lesson["code"].rstrip("\n")
    parts = [java_header(ch, num, lesson),
             "package com.heima.ch%d;" % ch,
             "",
             "// ==================== 示例代码 ====================",
             "",
             body,
             java_tail(lesson, comp if is_last else None)]
    return "\n".join(parts)


def build_md(ch, title, intro, lessons, comp):
    out = ["# 第%d章 %s" % (ch, title), "", intro, ""]
    for i, les in enumerate(lessons, 1):
        out += ["### %d-%02d %s" % (ch, i, les["title"]), "", "**知识点**"]
        for p in les["points"]:
            out.append("- " + p)
        out += ["", "**示例代码**", "```java", les["code"].rstrip("\n"), "```", "", "**课后练习**"]
        for j, e in enumerate(les["exercises"], 1):
            out.append("%d. %s" % (j, e))
        out.append("")
    out += ["## 本章综合练习", "", "### 综合题：%s" % comp["title"], "", "**场景**", "",
            comp["scene"], "", "**任务**"]
    for i, t in enumerate(comp["tasks"], 1):
        out.append("%d. %s" % (i, t))
    out += ["", "**验收清单**"]
    for a in comp["acceptance"]:
        out.append("- [ ] " + a)
    out.append("")
    return "\n".join(out)


def build_readme(ch, title, lessons, comp, note_file):
    out = ["# %s（com.heima.ch%d）" % (title, ch), "",
           "`package com.heima.ch%d`" % ch, "",
           "| 编号 | 课名 | 文件 |", "| --- | --- | --- |"]
    for i, les in enumerate(lessons, 1):
        out.append("| %d-%02d | %s | [%s.java](%s.java) |"
                   % (ch, i, les["title"], les["cls"], les["cls"]))
    out += ["", "> 练习题在每课文件末尾的注释块中，无参考答案。", "",
            "## 本章综合练习", "", "### 综合题：%s" % comp["title"], "",
            "**场景**", "", comp["scene"], "", "**任务**"]
    for i, t in enumerate(comp["tasks"], 1):
        out.append("%d. %s" % (i, t))
    out += ["", "**验收清单**"]
    for a in comp["acceptance"]:
        out.append("- [ ] " + a)
    last = lessons[-1]["cls"]
    out += ["",
            "> 同一道题也写在 [%s.java](%s.java) 文件末尾的注释块中，无参考答案；"
            "完整笔记见 [%s](../../../../../../../黑马Java课程笔记/%s) 的「本章综合练习」一节。"
            % (last, last, note_file, note_file), ""]
    return "\n".join(out)


def run(ch):
    mod = load(ch)
    title = mod.TITLE
    lessons = mod.LESSONS
    comp = mod.COMP
    if len(lessons) != 11:
        raise SystemExit("第%d章课程数 %d，应为 11" % (ch, len(lessons)))

    pkg_dir = os.path.join(JAVA_ROOT, "ch%d" % ch)
    os.makedirs(pkg_dir, exist_ok=True)

    for i, les in enumerate(lessons, 1):
        path = os.path.join(pkg_dir, les["cls"] + ".java")
        text = build_java(ch, i, les, comp, i == len(lessons))
        io.open(path, "w", encoding="utf-8", newline="\n").write(text + "\n")

    note_name = "第%d章-%s.md" % (ch, title)
    md_path = os.path.join(NOTES, note_name)
    io.open(md_path, "w", encoding="utf-8", newline="\n").write(
        build_md(ch, title, mod.INTRO, lessons, comp) + "\n")

    readme = os.path.join(pkg_dir, "README.md")
    io.open(readme, "w", encoding="utf-8", newline="\n").write(
        build_readme(ch, title, lessons, comp, note_name) + "\n")

    print("ch%d %s: %d 课 -> %s" % (ch, title, len(lessons), os.path.basename(md_path)))
    return note_name


if __name__ == "__main__":
    args = [int(a) for a in sys.argv[1:]] or [81]
    for c in args:
        run(c)
