# -*- coding: utf-8 -*-
"""把 _build/_new/chXX_<cls>.txt 的 Java 代码替换进对应课时的 code 字段。"""
import ast
import io
import sys

NEW = r"D:/Javalianxi/_build/_new"


def splice(module_path, cls, txt_path):
    t = io.open(module_path, encoding="utf-8").read()
    new_code = io.open(txt_path, encoding="utf-8").read().rstrip("\n")

    anchor = t.find('cls="%s"' % cls)
    assert anchor > 0, "anchor %s not found" % cls
    start = t.find("code=r'''", anchor)
    assert start > 0, "code start not found"
    begin = start + len("code=r'''")
    end = t.find("'''", begin)
    assert end > 0, "code end not found"
    old = t[begin:end]

    t = t[:begin] + "\n" + new_code + "\n" + t[end:]
    ast.parse(t)
    io.open(module_path, "w", encoding="utf-8", newline="\n").write(t)
    print("%s: %d -> %d chars, syntax ok" % (cls, len(old), len(new_code)))


if __name__ == "__main__":
    splice(sys.argv[1], sys.argv[2], sys.argv[3])
