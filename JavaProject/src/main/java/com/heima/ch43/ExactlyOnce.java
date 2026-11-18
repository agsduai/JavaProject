/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-06
 * 课名：Exactly-Once 语义
 * ------------------------------------------------------------
 * 知识点：
 *   - **三条可靠性等级**：At-most-once（最多一次：可能丢，简单最快）；At-least-once（至少一次：不丢但可能重，最常用默认）；Exactly-once（精确一次：不丢不重，成本最高）。**选择依据是业务对"丢"与"重"的容忍度**：监控大屏丢了可容忍（at-most-once 也行），支付计费重了就是事故（exactly-once 或幂等兜底）。
 *   - **Exactly-once 的两个层面**：**端到端** = 框架内部状态精确一次（checkpoint 保证）+ **外部副作用精确一次**（写 Kafka/DB/MQ 的结果不重不漏）。框架内部容易，外部难——**外部系统必须参与两阶段提交或提供幂等**。
 *   - **实现手段一：幂等写入**（最常用）：下游按主键 upsert（`INSERT ... ON DUPLICATE KEY UPDATE`）、Kafka 按键写可覆盖。**重放覆盖旧结果，天然收敛**。窗口结果带主键（窗口起点）就是为幂等服务。
 *   - **实现手段二：两阶段提交（2PC）**：Flink 的 TwoPhaseCommitSink——预提交（写事务暂存）+ checkpoint 成功后正式提交。要求下游支持事务（Kafka 事务 API），代价是延迟（数据要等 commit 才可见）。
 *   - **实现手段三：事务性写入下游**（DB 侧）：把"状态更新 + 输出写入"放进下游事务，靠下游原子性兜底。流框架只保证 at-least-once，精确性交给下游约束。
 *   - **"幂等 + at-least-once" ≈ exactly-once**：这是工程上最实用的等价——不追求框架级精确，而是**让每个写操作幂等**，重复执行结果不变。第 28 章消费幂等、第 39 章本地消息表，同一思想贯穿。
 *   - 易错点：以为开启 checkpoint 就端到端 exactly-once（只保了内部状态）；下游写非幂等（insert 重复数据）；2PC 下游不支持事务还硬用（事务泄漏）；把"窗口结果对不上"归罪于框架（多数是幂等缺失或时间语义错）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ExactlyOnce {
    record WindowResult(String windowKey, double gmv, int version) {
    }

    /** 幂等 sink：窗口结果按主键覆盖 */
    static class IdempotentSink {
        final Map<String, WindowResult> table = new LinkedHashMap<>();

        void write(WindowResult r) {
            WindowResult existing = table.get(r.windowKey());
            if (existing == null || r.version() >= existing.version()) {
                table.put(r.windowKey(), r);
                System.out.printf("  写入 %s gmv=%.1f v%d（覆盖式）%n",
                        r.windowKey(), r.gmv(), r.version());
            } else {
                System.out.printf("  忽略 %s v%d（已有 v%d，旧版本）%n",
                        r.windowKey(), r.version(), existing.version());
            }
        }
    }

    static void idempotentReplay() {
        System.out.println("【重放场景：同一窗口结果被写两次】");
        IdempotentSink sink = new IdempotentSink();
        sink.write(new WindowResult("11-17", 268.0, 1));
        // 故障恢复后重放，窗口重算出相同结果（版本相同）或更新结果（版本更高）
        sink.write(new WindowResult("11-17", 268.0, 1));
        sink.write(new WindowResult("11-17", 297.0, 2));
        System.out.println("  最终表：" + table(sink));
        System.out.println("  无论重放多少次，结果收敛 —— 这就是幂等的价值");
        System.out.println();
    }

    static Map<String, WindowResult> table(IdempotentSink sink) {
        return sink.table;
    }

    static void levelChoice() {
        System.out.println("【按业务选等级，不默认最高】");
        String[][] rows = {
                {"监控指标曲线", "at-least-once + 幂等（按窗口键覆盖）", "偶尔重复被覆盖，无损"},
                {"实时大屏 GMV", "幂等 upsert（窗口键主键）", "修正可见，最终一致"},
                {"计费/佣金入账", "事务写入 + 去重表", "钱的事，宁可复杂不可错"},
                {"风控事件流", "at-least-once + 下游幂等消费", "与 28 章消费幂等同款"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void twoPhase() {
        System.out.println("【2PC sink：预提交与正式提交】");
        List<String> steps = List.of(
                "1) sink 收到数据 -> 写入下游事务（未提交，预提交态）",
                "2) checkpoint barrier 到达 -> 状态快照（含事务句柄）",
                "3) checkpoint 成功 -> 正式 commit 事务（数据对外可见）",
                "4) 失败 -> 事务 abort，恢复后从 checkpoint 重放");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  代价：数据可见延迟 = checkpoint 间隔；要求下游支持事务");
        System.out.println("  实用主义：多数场景「幂等 + at-least-once」已等价于业务上的精确一次");
    }

    public static void main(String[] args) {
        idempotentReplay();
        levelChoice();
        twoPhase();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 43-05 的恢复重放接上幂等 sink，验证重复消费后最终结果仍然正确；再去掉幂等，观察重复数据。
 * 2. 为你的流作业下游设计幂等方案：主键选择（窗口键/业务键）、版本号策略、旧版本忽略规则。
 * 3. 思考题：为什么"幂等 + at-least-once"在工程上等价于 exactly-once？举一个该等价不成立的反例（幂等键设计错误的场景）。
 * ============================================================
 */
