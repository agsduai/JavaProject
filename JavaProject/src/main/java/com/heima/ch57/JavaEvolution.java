/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-01
 * 课名：从 Java 8 到 Java 21 的演进地图
 * ------------------------------------------------------------
 * 知识点：
 *   - **Java 的发布节奏变了：每 6 个月一个功能版本，每 2 年一个 LTS**——8（2014，LTS）、11（2018，LTS）、17（2021，LTS）、21（2023，LTS）。**企业选型只跟 LTS**：8 -> 11 -> 17 -> 21 是主流升级路线，中间版本只用来"提前试用"。
 *   - **JDK 21 是本课程选它作基座的原因**：虚拟线程（50 章）、record 与密封类、模式匹配、Sequenced Collections 全部定稿——**这些不是语法糖，而是改变建模方式的语言能力**（record 消除样板、密封类让"穷举"可被编译器检查）。
 *   - **演进的主线有三条**：① **表达力**（record/sealed/模式匹配/text blocks/var）；② **并发模型**（虚拟线程、结构化并发、ScopedValue）；③ **运行时**（G1/ZGC、容器感知、JFR 增强）。**本课程前面章节分散用到的特性，本章系统收口**。
 *   - **预览特性（preview）不是 API 承诺**：需要 `--enable-preview` 编译，下一个版本可能改语法甚至撤回——**生产代码不要依赖预览特性**（结构化并发、字符串模板目前仍是预览）。区分标准：`java --list-modules` 与官方 API 文档的"Preview"标记。
 *   - **升级的真实收益**：更少样板代码（record 替代 50 行 getter）、更强类型安全（sealed + switch 穷举检查）、更高并发（虚拟线程）、更好性能（新 GC）——**收益要量化到"每个版本省了多少维护成本"**（52-02 ADR 的输入）。
 *   - **升级的真实成本**：依赖库兼容性（字节码增强类库最敏感）、废弃 API 清理（`SecurityManager` 在 21 被永久禁用）、GC 参数变化、团队认知——**成本要提前扫描（jdeprscan）而不是上线才发现**。
 *   - 易错点：在生产用预览特性（升级即编译失败）；以为"新版本一定更快"（默认 GC 变化需要回归）；升级只改 `pom.xml` 不动代码（废弃 API 隐患潜伏）；跳过 17 直接 8->21（跨度过大，问题难以分批定位）。
 * ============================================================
 */

package com.heima.ch57;

import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class JavaEvolution {
    record Milestone(int version, int year, boolean lts, String headline) {
    }

    static void timeline() {
        System.out.println("【Java 版本地图（只看 LTS 做选型）】");
        List<Milestone> ms = List.of(
                new Milestone(8, 2014, true, "Lambda/Stream（函数式起步）"),
                new Milestone(11, 2018, true, "var、HTTP Client、单文件运行"),
                new Milestone(17, 2021, true, "sealed、switch 模式匹配（预览）、文本块"),
                new Milestone(21, 2023, true, "虚拟线程、record 模式、Sequenced Collections"));
        for (Milestone m : ms) {
            System.out.printf("  Java %-3d %d %s  %s%n", m.version(), m.year(),
                    m.lts() ? "LTS" : "    ", m.headline());
        }
        System.out.println();
    }

    static void featureMap() {
        System.out.println("【三条演进主线与本课程对应章节】");
        Map<String, String> lines = Map.of(
                "表达力", "record(57-02) / sealed(57-03) / 模式匹配(57-04/05) / 文本块(57-06) / var(57-07)",
                "并发", "虚拟线程(50 章) / 结构化并发(预览) / ScopedValue(预览)");
        lines.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println();
        System.out.println("  判定：定稿特性可用生产；预览特性只做学习验证（--enable-preview）");
    }

    public static void main(String[] args) {
        timeline();
        featureMap();
        System.out.println("  本机运行时：" + Runtime.version());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 列出你项目当前 JDK 版本与目标版本，画出两版之间的 LTS 升级路线，标注每步的风险点。
 * 2. 用 `jdeprscan --release 21 <jar>` 扫描一个真实依赖，统计废弃 API 命中数并给出修复清单。
 * 3. 思考题：为什么"企业只跟 LTS"？从中长期支持周期、依赖生态成熟度、升级成本摊销三个角度论证。
 * ============================================================
 */
