# -*- coding: utf-8 -*-
"""全量结构审计：章节笔记 + 实操文档的格式完整性。"""
import glob
import io
import os
import re

NOTES = r"D:/Javalianxi/黑马Java课程笔记"
issues = []
report = []

# ---------------- 章节笔记审计 ----------------
chapter_files = sorted(glob.glob(os.path.join(NOTES, "第*章*.md")),
                       key=lambda p: int(re.search(r"第(\d+)章", p).group(1)))
for path in chapter_files:
    chap = int(re.search(r"第(\d+)章", path).group(1))
    name = os.path.basename(path)
    text = io.open(path, encoding="utf-8").read()
    fences = text.count("```")
    if fences % 2 != 0:
        issues.append("%s: 代码围栏不配对（%d 个 ```）" % (name, fences))
    headings = re.findall(r"^### (%d)-(\d+)\s+(.+?)\s*$" % chap, text, re.M)
    nums = [int(n) for _, n, _ in headings]
    if not nums:
        issues.append("%s: 无课程标题（### %d-XX）" % (name, chap))
        continue
    # 连续性
    for i, n in enumerate(nums, start=1):
        if n != i:
            issues.append("%s: 课号不连续，第 %d 个标题是 %d-（期望 %d）" % (name, i, n, i))
            break
    if len(nums) != len(set(nums)):
        issues.append("%s: 课号重复" % name)
    # 每课三段
    blocks = re.split(r"^### %d-\d+.*$" % chap, text, flags=re.M)[1:]
    for idx, block in enumerate(blocks):
        has_k = "**知识点**" in block
        has_c = "**示例代码**" in block
        has_e = "**课后练习**" in block
        if not (has_k and has_c and has_e):
            missing = [m for m, ok in (("知识点", has_k), ("示例代码", has_c), ("课后练习", has_e)) if not ok]
            issues.append("%s %d-%02d: 缺少段落 %s" % (name, chap, nums[idx], missing))
        java_blocks = len(re.findall(r"```java\s*\n", block))
        if java_blocks != 1:
            issues.append("%s %d-%02d: java 代码块数量 %d（期望 1）" % (name, chap, nums[idx], java_blocks))
        # 代码块内的可疑引号（中文正文允许，字符串里 ASCII 双引号嵌套已由编译器兜底，这里只查明显模式）
        if re.search(r'println\("[^"]*"[^"]*"[^)]*\)\s*;\s*$', block, re.M):
            pass  # 太多误报，跳过，依赖编译器
    report.append("%s: lessons=%d java=%d fences=%d" % (name, len(nums),
                  text.count("```java"), fences))

# ---------------- 实操文档审计 ----------------
practice_files = sorted(glob.glob(os.path.join(NOTES, "毕业实操题", "*.md")))
for path in practice_files:
    name = os.path.basename(path)
    text = io.open(path, encoding="utf-8").read()
    fences = text.count("```")
    if fences % 2 != 0:
        issues.append("毕业实操题/%s: 代码围栏不配对（%d）" % (name, fences))
    # 分章实操
    ex_heads = re.findall(r"^### 实操 (\d+)-(\d+)\s+(.+?)\s*$", text, re.M)
    blocks = re.split(r"^### 实操 \d+-\d+.*$", text, flags=re.M)
    # blocks[0] 是文件头；实操块从第 2 段开始（注意卷标题也会切段）
    # 用另一种方式：定位每个实操标题后的片段
    pos = [(m.start(), m.end()) for m in re.finditer(r"^### 实操 (\d+)-(\d+)\s+.+$", text, re.M)]
    for (start, end), (cc, ll) in zip(pos, [(m.group(1), m.group(2))
                                            for m in re.finditer(r"^### 实操 (\d+)-(\d+)\s+.+$", text, re.M)]):
        nxt = text.find("\n## ", end)
        seg_end = nxt if nxt > 0 else len(text)
        block = text[end:seg_end]
        has_k = "**知识点注释**" in block
        has_c = "**示例代码**" in block
        has_e = "**课后练习题**" in block
        if not (has_k and has_c and has_e):
            missing = [m for m, ok in (("知识点注释", has_k), ("示例代码", has_c), ("课后练习题", has_e)) if not ok]
            issues.append("毕业实操题/%s 实操 %s-%s: 缺少段落 %s" % (name, cc, ll, missing))
        java_blocks = len(re.findall(r"```java\s*\n", block))
        if java_blocks != 1:
            issues.append("毕业实操题/%s 实操 %s-%s: java 代码块数量 %d（期望 1）"
                          % (name, cc, ll, java_blocks))
    # 综合卷
    papers = re.findall(r"^## 卷[一二三四五六]\s+(.+?)\s*$", text, re.M)
    ppos = [(m.start(), m.end()) for m in re.finditer(r"^## 卷[一二三四五六]\s+.+$", text, re.M)]
    for (start, end) in ppos:
        nxt = text.find("\n---", end)
        seg_end = nxt if nxt > 0 else len(text)
        block = text[end:seg_end]
        for marker in ("**知识点注释**", "**示例代码**", "**课后练习题**"):
            if marker not in block:
                issues.append("毕业实操题/%s 卷: 缺少 %s" % (name, marker))
    ex_total = len(ex_heads)
    paper_total = len(papers)
    report.append("毕业实操题/%s: 分章题=%d 综合卷=%d fences=%d" % (name, ex_total, paper_total, fences))

print("==== 审计明细 ====")
for r in report:
    print(r)
print()
print("==== 发现的问题（%d）====" % len(issues))
for i in issues:
    print(" -", i)
