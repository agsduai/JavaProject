# -*- coding: utf-8 -*-
import re, os, glob, shutil

ROOT = r"D:\Javalianxi"
SRC = os.path.join(ROOT, "黑马Java课程笔记")
OUT = os.path.join(ROOT, "JavaProject")

CHAPTER_DIRS = {
    "02": "02_语法篇",
    "03": "03_判断与循环",
    "04": "04_数组",
    "05": "05_方法",
    "06": "06_原理篇",
    "07": "07_算法训练",
    "08": "08_面向对象",
    "09": "09_面向对象原理篇",
    "10": "10_面向对象进阶",
    "11": "11_面向对象高级",
    "12": "12_常见API",
    "13": "13_阶段项目实战",
    "14": "14_常用类API",
}

HEADER = """/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：{no}
 * 课名：{title}
 * ------------------------------------------------------------
 * 知识点：
{points}
 * ============================================================
 */
"""


def safe_title(t):
    t = re.sub(r"[^\w\u4e00-\u9fff]+", "_", t)
    t = re.sub(r"_+", "_", t).strip("_")
    return t or "Lesson"


def parse_md(path):
    text = open(path, encoding="utf-8").read()
    ms = list(re.finditer(r"^### (\d+)-(\d+)\s+(.+?)\s*$", text, re.M))
    out = []
    for i, m in enumerate(ms):
        cc, ll, title = m.group(1).zfill(2), m.group(2), m.group(3).strip()
        seg = text[m.end(): ms[i + 1].start() if i + 1 < len(ms) else len(text)]
        ks = seg.find("**知识点**")
        es = seg.find("**示例代码**")
        ps = seg.find("**课后练习**")
        if min(ks, es) < 0:
            print("SKIP(no sections):", cc, ll, title)
            continue
        knowledge = seg[ks + len("**知识点**"):es] if ks >= 0 else ""
        examples = seg[es + len("**示例代码**"):ps if ps >= 0 else len(seg)] if es >= 0 else ""
        exercises = seg[ps + len("**课后练习**"):] if ps >= 0 else ""
        out.append(dict(cc=cc, ll=ll, no=f"{int(cc)}-{ll}", title=title,
                        knowledge=knowledge, examples=examples, exercises=exercises))
    return out


def bullets(text):
    res = []
    for line in text.splitlines():
        s = line.strip()
        if not s:
            continue
        if s.startswith("- "):
            res.append(s[2:].strip())
        elif s.startswith("* "):
            res.append(s[2:].strip())
        elif res:
            res[-1] += " " + s
    return res


CH13_DEPS = """
class Role {
    private String name;
    private int hp;
    private int attack;

    public Role() {
    }

    public Role(String name, int hp, int attack) {
        this.name = name;
        this.hp = hp;
        this.attack = attack;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getAttack() {
        return attack;
    }

    public void setAttack(int attack) {
        this.attack = attack;
    }

    public void hurt(int damage) {
        this.hp -= damage;
        if (this.hp < 0) {
            this.hp = 0;
        }
    }
}

class Hero extends Role {
    private int anger;

    public Hero() {
    }

    public Hero(String name, int hp, int attack, int anger) {
        super(name, hp, attack);
        this.anger = anger;
    }

    public int getAnger() {
        return anger;
    }

    public void setAnger(int anger) {
        this.anger = anger;
    }
}

class Enemy extends Role {
    private int type;

    public Enemy() {
    }

    public Enemy(int type, String name, int hp, int attack) {
        super(name, hp, attack);
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
"""


def parse_practice_md(path, kind):
    text = open(path, encoding="utf-8").read()
    if kind == "paper":
        pattern = r"^## 卷([一二三])\s+(.+?)\s*$"
    else:
        pattern = r"^### 实操 (\d+)-(\d+)\s+(.+?)\s*$"
    ms = list(re.finditer(pattern, text, re.M))
    out = []
    for i, m in enumerate(ms):
        if kind == "paper":
            cc = ll = ""
            tag = str(i + 1)
            no_label = "毕业综合卷 卷" + m.group(1)
            title = m.group(2).strip()
        else:
            cc, ll = m.group(1).zfill(2), m.group(2)
            tag = f"{cc}{ll}"
            no_label = f"实操 {int(cc)}-{ll}"
            title = m.group(3).strip()
        seg = text[m.end(): ms[i + 1].start() if i + 1 < len(ms) else len(text)]
        ks = seg.find("**知识点注释**")
        es = seg.find("**示例代码**")
        ps = seg.find("**课后练习题**")
        if min(ks, es, ps) < 0:
            print("SKIP(practice sections):", no_label, title)
            continue
        out.append(dict(cc=cc, ll=ll, tag=tag, no=no_label, title=title,
                        knowledge=seg[ks + len("**知识点注释**"):es],
                        examples=seg[es + len("**示例代码**"):ps],
                        exercises=seg[ps + len("**课后练习题**"):]))
    return out


def clean_block(b):
    imports = []
    lines = []
    for line in b.splitlines():
        s = line.strip()
        if re.match(r"^//\s*文件名[:：]", s):
            continue
        if re.match(r"^import\s+", s):
            imports.append(s)
            continue
        lines.append(line.rstrip())
    return "\n".join(lines).strip("\n"), imports


def exercise_comment(text):
    lines = []
    for line in text.splitlines():
        s = line.rstrip()
        if s.strip().startswith("```"):
            continue
        if s.strip() in ("---", "***", "___"):
            continue
        s = s.replace("**", "").replace("*/", "* /")
        lines.append(" * " + s)
    while lines and lines[0].strip() == "*":
        lines.pop(0)
    while lines and lines[-1].strip() == "*":
        lines.pop()
    if not lines:
        lines = [" * （本课未布置课后练习）"]
    return ("/*\n"
            " * ============================================================\n"
            " * 课后练习题（无参考答案）\n"
            " * ------------------------------------------------------------\n"
            + "\n".join(lines) + "\n"
            " * ============================================================\n"
            " */\n")


DECL = re.compile(
    r"^(?:public\s+)?(?:abstract\s+|final\s+|static\s+)*(?:class|interface|enum)\s+"
    r"([A-Za-z_\u4e00-\u9fff][\w\u4e00-\u9fff]*)", re.M)


def build_lesson(les, out_dir, prefix_char="L", tag=None, no_label=None):
    cc, ll = les.get("cc", ""), les.get("ll", "")
    title = les["title"]
    no = no_label or les.get("no", "")
    tag = tag or les.get("tag") or f"{cc}{ll}"
    pts = bullets(les["knowledge"])
    if not pts:
        pts = ["（见课名与示例代码）"]
    header = HEADER.format(
        no=no, title=title,
        points="\n".join(" *   - " + p.replace("*/", "* /") for p in pts))

    blocks = re.findall(r"```java\s*\n(.*?)```", les["examples"], re.S)
    if not blocks:
        print("SKIP(no java):", no, title)
        return None
    body_parts = []
    imports = []
    for b in blocks:
        nb, im = clean_block(b)
        imports += im
        if nb.strip():
            body_parts.append(nb)
    # 第13章 05~10 课依赖角色父类与敌我子类，注入后本课可独立编译运行
    if cc == "13" and 5 <= int(ll) <= 10:
        body_parts.insert(0, CH13_DEPS)
    body = "\n\n".join(body_parts)

    seen = set()
    imp_lines = []
    for im in imports:
        if im not in seen:
            seen.add(im)
            imp_lines.append(im)

    matches = list(DECL.finditer(body))
    if not matches:
        print("SKIP(no type):", no, title)
        return None
    type_names = [m.group(1) for m in matches]

    entry_old = None
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(body)
        span = body[m.start():end]
        if re.search(r"\bpublic\s+static\s+void\s+main\s*\(", span):
            entry_old = m.group(1)
            break

    safe = safe_title(title)
    entry_new = f"{prefix_char}{tag}_{safe}"
    prefix = f"{prefix_char}{tag}_"

    for old in sorted(set(type_names), key=len, reverse=True):
        new = entry_new if old == entry_old else prefix + old
        body = re.sub(r"(?<!\w)" + re.escape(old) + r"(?!\w)", new, body)

    has_main = entry_old is not None
    if has_main:
        body = re.sub(
            r"^public\s+(?=(?:abstract\s+|final\s+|static\s+)*(?:class|interface|enum)\s+)",
            "", body, flags=re.M)
        body = re.sub(
            r"^((?:abstract\s+|final\s+|static\s+)*(?:class|interface|enum)\s+"
            + re.escape(entry_new) + r"\b)",
            r"public \1", body, count=1, flags=re.M)
        runner = ""
    else:
        body = re.sub(
            r"^public\s+(?=(?:abstract\s+|final\s+|static\s+)*(?:class|interface|enum)\s+)",
            "", body, flags=re.M)
        runner = f"""
public class {entry_new} {{
    public static void main(String[] args) {{
        System.out.println("本课为设计/原理示例，请阅读代码与注释，并完成文件末尾的课后练习。");
    }}
}}
"""
    code = header
    if imp_lines:
        code += "\n" + "\n".join(imp_lines) + "\n"
    code += "\n// ==================== 示例代码 ====================\n\n" + body.strip() + "\n" + runner
    code += "\n" + exercise_comment(les["exercises"])

    fname = entry_new + ".java"
    path = os.path.join(out_dir, fname)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(code)
    return dict(no=no, title=title, fname=fname, cc=cc)


def main():
    if os.path.exists(OUT):
        for root, dirs, fs in os.walk(OUT):
            for name in fs:
                try:
                    os.remove(os.path.join(root, name))
                except OSError:
                    pass
    else:
        os.makedirs(OUT)
    index = {}
    files = sorted(glob.glob(os.path.join(SRC, "第*.md")))
    for md in files:
        base = os.path.basename(md)
        m = re.match(r"第(\d+)章", base)
        if not m:
            continue
        cc = m.group(1)
        if cc not in CHAPTER_DIRS:
            continue
        out_dir = os.path.join(OUT, CHAPTER_DIRS[cc])
        os.makedirs(out_dir, exist_ok=True)
        lessons = parse_md(md)
        rows = []
        for les in lessons:
            r = build_lesson(les, out_dir)
            if r:
                rows.append(r)
        index[cc] = rows

        lines = [f"# {CHAPTER_DIRS[cc]}", "", "| 课号 | 课名 | 文件 |", "| --- | --- | --- |"]
        for r in rows:
            lines.append(f"| {r['no']} | {r['title']} | [{r['fname']}]({r['fname']}) |")
        lines += ["", "> 练习题在每课文件末尾的注释块中，无参考答案。"]
        open(os.path.join(out_dir, "README.md"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
        print(f"{CHAPTER_DIRS[cc]}: {len(rows)} 课")

    # 毕业实操题：29 道分章实操 + 3 套综合卷
    practice_sets = [
        ("第02-05章-基础实操.md", "基础篇_第02-05章", "practice"),
        ("第06-10章-进阶实操.md", "进阶篇_第06-10章", "practice"),
        ("第11-14章-综合实操.md", "综合篇_第11-14章", "practice"),
        ("毕业综合实操卷.md", "毕业综合卷", "paper"),
    ]
    practice_root = os.path.join(OUT, "毕业实操题")
    practice_rows = []
    practice_counts = []
    for fname, folder, kind in practice_sets:
        src = os.path.join(SRC, "毕业实操题", fname)
        if not os.path.exists(src):
            continue
        out_dir = os.path.join(practice_root, folder)
        os.makedirs(out_dir, exist_ok=True)
        rows = []
        for les in parse_practice_md(src, kind):
            r = build_lesson(les, out_dir,
                             prefix_char="SJ" if kind == "paper" else "S",
                             tag=les["tag"], no_label=les["no"])
            if r:
                rows.append(r)
        practice_rows.extend(rows)
        practice_counts.append((folder, len(rows)))
        plines = [f"# {folder}", "", "| 编号 | 题名 | 文件 |", "| --- | --- | --- |"]
        for r in rows:
            plines.append(f"| {r['no']} | {r['title']} | [{r['fname']}]({r['fname']}) |")
        plines += ["", "> 题目要求见文件头知识点注释；示例代码只是技术示范，不是本题答案；练习题在文件末尾，无参考答案。"]
        open(os.path.join(out_dir, "README.md"), "w", encoding="utf-8").write("\n".join(plines) + "\n")
        print(f"毕业实操题/{folder}: {len(rows)} 题")

    total = sum(len(v) for v in index.values())
    lines = [
        "# JavaProject（黑马 2026 版 AI+Java 课程配套工程）", "",
        "> 课程来源：B站 BV1TJxCzSEEZ《黑马程序员2026版AI+Java零基础全套视频课程》",
        f"> 共 {total} 课（一课一个 `.java`），另含 29 道分章实操 + 3 套综合卷（共 {len(practice_rows)} 个实操文件）。", "",
        "## 目录结构", "", "```",
        "JavaProject/",
    ]
    for cc in sorted(index):
        lines.append(f"├── {CHAPTER_DIRS[cc]}/")
        rows = index[cc]
        for j, r in enumerate(rows):
            mark = "└──" if j == len(rows) - 1 else "├──"
            lines.append(f"│   {mark} {r['fname']}")
    lines.append("└── 毕业实操题/")
    for j, (folder, cnt) in enumerate(practice_counts):
        mark = "└──" if j == len(practice_counts) - 1 else "├──"
        lines.append(f"    {mark} {folder}/（{cnt} 题）")
    lines += ["```", "",
              "## 每课文件格式", "",
              "1. 文件头：课程来源、课号、课名、知识点注释；",
              "2. 正文：本课完整可运行示例（多类示例合并为一个文件，辅助类为非 public）；",
              "3. 文件末尾：课后练习题注释块，**不附参考答案**。", "",
              "## 命名规则", "",
              "- 文件名 = `L` + 两位章号 + 两位课号 + `_` + 课名，例如 `L0201_字面量.java`；",
              "- 为避免不同课之间类名冲突，所有顶层类的类名都带有 `L章号课号_` 前缀；",
              "- 入口类名与文件名一致，可直接运行其 `main` 方法；",
              "- 毕业实操题使用 `S` 前缀（综合卷用 `SJ`），如 `S0201_智能点餐结算单.java`。", "",
              "## 如何运行", "",
              "**IntelliJ IDEA**：`File → Open` 选择 `JavaProject` 目录，等待索引完成后，",
              "打开任意 `.java` 文件，点击 `main` 方法左侧绿色三角运行。", "",
              "**命令行**：", "",
              "```bash",
              "cd JavaProject/02_语法篇",
              "javac -encoding UTF-8 L0201_字面量.java",
              "java L0201_字面量",
              "```", "",
              "> 部分课程需要 JDK 17+（如 switch 箭头语法等）；建议统一使用 JDK 17 或 21。",
              "> 课后练习题仅出题，不提供答案；示例代码只是课堂示范，建议先独立完成练习。", ""]
    open(os.path.join(OUT, "README.md"), "w", encoding="utf-8").write("\n".join(lines))
    print("总计", total, "课")


if __name__ == "__main__":
    main()
