/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-08
 * 课名：热修复与动态诊断
 * ------------------------------------------------------------
 * 知识点：
 *   - **热修复（Hot Swap / Hot Fix）的两种实现**：**JVM 自带 HotSwap**（Debug 模式改方法体，重启断点生效）与 **Agent redefine**（运行时替换字节码，生产可用）——**后者是线上应急的主流**，但**只能改方法体**（66-07 的限制）。
 *   - **热修复的适用与不适用**：**适用**（改一个 if 条件、加日志、降级开关、空指针保护）；**不适用**（新增字段/方法、改签名、改类结构）——**后者只能发版**。
 *   - **动态诊断的常见动作**：**看方法入参与返回值**（不打断点）、**看某个方法的调用栈与耗时**、**看静态字段值**、**触发一次 GC / 打印线程栈**——**arthas 类工具就是"Agent + 命令"**。
 *   - **热修复的风险**：**改错就是二次故障**（字节码错误 -> VerifyError -> 类加载失败）；**无版本管理**（改了什么没人知道）；**修复被遗忘**（下次发版回退成旧代码，问题复发）——**所以热修复必须：灰度（一台机器先验证）、记录（修了什么、何时、谁）、跟进（下次发版合入源码）**。
 *   - **与 41 章的关系**：热修复是"绕过发布流程的应急通道"——**41-10 的"紧急发布也要记录"在这里同样适用**（热修复台账）。
 *   - 易错点：热修复当常规手段（技术债）；改完不验证就全量推（二次故障）；不记录（下次发版丢修复）；在 Agent 里做复杂逻辑（易错）。
 * ============================================================
 */

package com.heima.ch66;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class HotFixDemo {
    /** 模拟类的"可替换方法体"：版本 -> 行为 */
    static final Map<Integer, java.util.function.Function<String, String>> behaviors =
            new LinkedHashMap<>();
    static int activeVersion = 1;
    static final List<String> audit = new java.util.ArrayList<>();

    static {
        behaviors.put(1, arg -> arg == null ? "NPE（旧代码）" : "order-" + arg);
        behaviors.put(2, arg -> arg == null ? "order-unknown（热修复：空值兜底）" : "order-" + arg);
    }

    /** 被增强的方法：调用时取当前生效的行为 */
    static String query(String orderId) {
        return behaviors.get(activeVersion).apply(orderId);
    }

    /** 动态诊断：不修改行为，只观察 */
    static void diagnose(String orderId) {
        System.out.println("  [诊断] query 入参=" + orderId
                + " 当前生效版本=v" + activeVersion);
    }

    /** 热修复：灰度 -> 全量，带审计 */
    static void hotfix(int targetVersion, boolean grayFirst) {
        if (!behaviors.containsKey(targetVersion)) {
            System.out.println("  热修复失败：版本不存在");
            return;
        }
        if (grayFirst) {
            System.out.println("  灰度：先切 v" + targetVersion + " 到 1 台实例，观察 10 分钟");
            audit.add("灰度 v" + targetVersion + "（1 台实例）");
        }
        activeVersion = targetVersion;
        audit.add("全量切换 v" + targetVersion);
        System.out.println("  已切换到 v" + targetVersion + "（全量）");
    }

    public static void main(String[] args) {
        System.out.println("【热修复前】");
        diagnose("9001");
        System.out.println("  正常入参：" + query("9001"));
        System.out.println("  异常入参：" + query(null));

        System.out.println();
        System.out.println("【热修复：空值兜底（改方法体，不改签名）】");
        hotfix(2, true);
        System.out.println("  正常入参：" + query("9001"));
        System.out.println("  异常入参：" + query(null));

        System.out.println();
        System.out.println("【热修复台账】");
        audit.forEach(a -> System.out.println("  " + a));

        System.out.println();
        System.out.println("【热修复的三条纪律】");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("灰度", "先在 1 台实例验证，确认无 VerifyError 与业务异常");
        rules.put("台账", "记录修了什么/何时/谁/影响范围（41-10 审计）");
        rules.put("跟版", "下次发版必须把修复合入源码，否则回退即复发");
        rules.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println();
        System.out.println("【不能热修复的场景】新增字段/方法、改签名、改类结构 -> 只能发版（66-07）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 Agent implement 一次真实热修复：给一个空指针方法加兜底，验证修复前后行为与"不改签名"的约束。
 * 2. 设计"热修复台账"模板（时间/版本/修改人/影响类方法/灰度范围/合入发版号），并演练一次完整流程。
 * 3. 思考题：为什么"热修复不做灰度"是危险操作？从字节码错误导致类加载失败（应用起不来）的后果分析。
 * ============================================================
 */
