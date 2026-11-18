/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-11
 * 课名：综合案例：订单主题湖仓
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章串成一条完整数据链路**：MySQL 订单（binlog）-> Flink CDC 写 Iceberg ODS（46-05/06，checkpoint 对齐）-> 每日批清洗 DWD（Parquet 分区 + 质量校验 46-08）-> DWS 预聚合 -> ADS 物化视图（46-07）-> 指标 API/大屏/回灌 Redis（46-10）。**每一课的设计决策在链路上各就各位**。
 *   - **关键设计参数**：ODS 按 CDC 写（MOR + 每 10 分钟 compaction）、DWD 按 COW 天分区（读优先）、快照保留 7 天 + 每日过期、ADS 回灌 Redis 分钟级。**参数背后都是读写比例与时效 SLA 的权衡**。
 *   - **质量与治理贯通**：契约（46-08）约束上游事件、每层产出跑质量规则、血缘覆盖全链、目录登记责任人。**一次口径变更的影响面靠血缘在 10 分钟内评估完**。
 *   - **故障与修复叙事**：上游误把退款金额写负数 -> 质量规则当日拦截（金额范围）-> 阻断 DWD 产出 -> 修复后重跑该分区（幂等）-> 时间旅行核对修复前后差异 -> 下游自动恢复。**全链路无人工救火**。
 *   - **上线检查单**：管道 SLA 与告警、质量规则覆盖率、compaction 排期、快照过期排期、权限最小化、成本三层兜底、演练（分区失败重跑/时间旅行回滚/下游新鲜度降级）。
 *   - 易错点：全链路只有 ODS 没有 DWD（下游直接扫原始 JSON）；质量规则上线后从不演练（规则失效无人知）；ADS 与 DWS 口径两套代码（指标打架复发）；时间旅行当成日常修复手段滥用（应修根因，回滚只是止血）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LakehouseCase {
    record Stage(String name, String tech, String cadence, String qualityGate) {
    }

    static void architecture() {
        System.out.println("【订单主题湖仓：从 binlog 到大屏】");
        List<Stage> stages = List.of(
                new Stage("ODS", "Iceberg(MOR) + Flink CDC", "秒级写入/10min compaction", "契约校验（46-08）"),
                new Stage("DWD", "Iceberg(COW) 天分区", "每日 1:00 批", "质量规则硬阻断"),
                new Stage("DWS", "预聚合表", "每日 2:00", "跨表一致性核对"),
                new Stage("ADS", "物化视图 + Redis 回灌", "分钟级", "新鲜度 SLA"),
                new Stage("服务", "指标 API / 大屏 / 榜单", "实时读", "新鲜度元数据随响应返回"));
        for (Stage s : stages) {
            System.out.printf("  %-4s %-26s %-24s %s%n", s.name(), s.tech(), s.cadence(), s.qualityGate());
        }
        System.out.println();
    }

    static void incidentReplay() {
        System.out.println("【一次负数金额事故的全自动处置】");
        List<String> timeline = List.of(
                "09:12 上游 bug：refund_amount 写入负数",
                "09:13 ODS 契约校验发现异常（字段范围外）-> 记录旁路不阻断 ODS（保真）",
                "09:14 DWD 质量规则（amount>=0）触发硬阻断 -> 该分区标记不可用",
                "09:20 上游修复 -> 重跑当日增量（幂等覆盖）",
                "09:25 质量规则复检通过 -> 分区恢复 -> 下游自动续跑",
                "10:00 时间旅行对比修复前后快照 -> 确认无残留错误数据",
                "复盘：契约文档补充“金额非负”条款 + 上游加单元测试");
        for (String t : timeline) {
            System.out.println("  " + t);
        }
        System.out.println("  全程 48 分钟，零人工救火 —— 质量规则 + 幂等重跑 + 时间旅行的组合拳");
        System.out.println();
    }

    static void opsChecklist() {
        System.out.println("【湖仓上线检查单】");
        Map<String, String> items = new LinkedHashMap<>();
        items.put("管道", "CDC lag 告警、批任务失败告警、补数流程演练");
        items.put("存储", "compaction 排期、快照过期排期、生命周期降级");
        items.put("质量", "规则覆盖率 >80% 核心表、阻断/告警分级、月度误报复盘");
        items.put("治理", "血缘覆盖、目录登记、僵尸表下线流程");
        items.put("成本", "存储分层、出网预算、小文件治理");
        items.put("安全", "桶策略最小化、列级权限、出域审批");
        for (Map.Entry<String, String> e : items.entrySet()) {
            System.out.printf("  %-4s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  演练清单：分区失败重跑 / 时间旅行回滚 / 新鲜度降级，每季度一次");
    }

    public static void main(String[] args) {
        architecture();
        incidentReplay();
        opsChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现迷你湖仓（内存模拟）：ODS->DWD->ADS 三层 + 质量规则 + 幂等重跑 + 快照回退，跑通本章事故叙事。
 * 2. 为你的业务写四层分区设计文档（技术选型 + 参数 + 质量门槛 + SLA），标注每个参数的权衡理由。
 * 3. 思考题：为什么说"时间旅行是止血手段而不是修复手段"？从根因修复、审计、存储成本三个角度界定它的使用边界。
 * ============================================================
 */
