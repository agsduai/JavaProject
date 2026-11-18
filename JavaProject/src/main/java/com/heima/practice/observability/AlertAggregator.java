/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 42-02
 * 课名：告警聚合与抑制器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现告警聚合器：输入带时间戳的告警流（服务/类型/严重度），按“根因服务 + 5 分钟窗口”聚合成事件；依赖抑制规则（DB 挂时抑制下游服务的连接错误告警）；输出事件列表（含被抑制的成员清单）与降噪统计（原始条数 vs 事件数）。
 *   - 【考核点】窗口聚合、根因识别（依赖方向）、抑制链、降噪度量。
 *   - 【易错点】抑制方向搞反（根因被下游告警抑制）；窗口边界归属（跨窗口事件拆分规则未定义）；聚合后丢失明细（排障需要成员清单）。
 *   - 【提示】依赖方向表（db -> order/stock/pay）显式声明；被抑制的告警保留在事件成员里。
 * ============================================================
 */

package com.heima.practice.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AlertAggregator {
    record Alert(String service, String msg) {
    }

    static final Map<String, List<String>> DEPENDS = Map.of(
            "db", List.of("order", "stock", "pay"));

    public static void main(String[] args) {
        List<Alert> alerts = List.of(
                new Alert("db", "主库连接拒绝"),
                new Alert("order", "DB 连接超时"),
                new Alert("stock", "DB 连接超时"),
                new Alert("pay", "DB 连接超时"));
        List<Alert> rootCauses = new ArrayList<>();
        List<String> suppressed = new ArrayList<>();
        for (Alert a : alerts) {
            if (DEPENDS.containsKey(a.service())) {
                rootCauses.add(a);
            } else {
                boolean covered = rootCauses.stream()
                        .anyMatch(rc -> DEPENDS.getOrDefault(rc.service(), List.of())
                                .contains(a.service()));
                if (covered) {
                    suppressed.add(a.service());
                } else {
                    rootCauses.add(a);
                }
            }
        }
        System.out.println("  根因事件：" + rootCauses);
        System.out.println("  抑制成员：" + suppressed);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整聚合器：5 分钟窗口 + 依赖方向表 + 成员清单保留，用 20 条模拟告警验证降噪比。
 * 2. 构造“两个独立故障同时发生”的场景，验证聚合器不会错误合并（按服务依赖图分区）。
 * 3. 思考题：为什么“抑制”必须基于显式的依赖方向表？用错误抑制方向的后果说明。
 * 
 * 
 * ## 第43章 实时计算与流处理
 * ============================================================
 */
