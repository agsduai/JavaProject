/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-09
 * 课名：CDC 与实时数仓
 * ------------------------------------------------------------
 * 知识点：
 *   - **CDC（Change Data Capture）把数据库的每次变更变成事件流**：基于 binlog（Canal/Debezium/Flink CDC）伪装成从库订阅变更，业务零侵入。第 38 章的"ES 数据同步"、第 39 章的"异构数仓"，落地的技术底座就是 CDC。
 *   - **实时数仓的分层**：ODS（binlog 原始层，对应每张表）-> DWD（明细层：清洗、拆列、维表关联）-> DWS（汇总层：实时聚合窗口）-> ADS（应用层：大屏/接口）。**与离线数仓同名分层，实时化替代 T+1**。
 *   - **CDC 的语义细节**：binlog 是 ROW 格式才带完整前后镜像；一条 UPDATE 在 CDC 里是"before + after"两个影像，下游要做对比；**DELETE 也要传播**（下游删数据，漏了就是幻影行）。
 *   - **全量 + 增量的衔接（snapshot reading）**：新表接入先全量快照再接 binlog 增量，衔接点用"快照时的高水位 offset"保证不重不漏——这是 Flink CDC 的核心卖点，省去手工衔接脚本。
 *   - **schema 变更的传播**：源表加列，CDC 下游怎么办——主流方案：下游手动兼容（新字段容忍缺失）或框架自动同步（Flink CDC 3.x schema evolution）。**schema 变更是 CDC 管道的运营常态**，要有演练。
 *   - **实时数仓与离线的口径治理**：同一指标（GMV）实时与离线各算一份，**口径必须同一份定义**（哪些订单算、退款怎么扣），否则"大屏 5000 万、报表 4900 万"的解释成本巨大（43-01 Lambda 架构的遗留问题）。
 *   - 易错点：binlog 还是 ROW 格式之外的（STATEMENT）下游无法解析；DELETE 事件被忽略（下游幻影数据）；全量增量手工衔接出错（重复或漏段）；实时与离线口径独立演化（数字打架）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class CdcWarehouse {
    record Row(String id, String status, double amount) {
    }

    /** 一条 CDC 事件：表 + 操作 + before/after */
    record CdcEvent(String table, String op, Row before, Row after, long binlogOffset) {
        String describe() {
            return switch (op) {
                case "INSERT" -> "新增 id=" + after.id();
                case "UPDATE" -> "更新 id=" + after.id() + " " + before.status() + "->" + after.status();
                case "DELETE" -> "删除 id=" + before.id();
                default -> "未知";
            };
        }
    }

    static void eventSemantics() {
        System.out.println("【CDC 事件语义：INSERT/UPDATE/DELETE 都要传播】");
        List<CdcEvent> events = List.of(
                new CdcEvent("orders", "INSERT", null, new Row("9001", "PAID", 199.0), 101),
                new CdcEvent("orders", "UPDATE",
                        new Row("9001", "PAID", 199.0), new Row("9001", "REFUNDED", 199.0), 108),
                new CdcEvent("orders", "DELETE", new Row("9002", "CREATED", 59.0), null, 112));
        for (CdcEvent e : events) {
            System.out.printf("  offset=%d %s%n", e.binlogOffset(), e.describe());
        }
        System.out.println("  下游数仓按 offset 应用：INSERT 插、UPDATE 改、DELETE 删");
        System.out.println("  忽略 DELETE -> 下游出现 id=9002 的幻影订单 —— 对账永远差一笔");
        System.out.println();
    }

    static void snapshotHandoff() {
        System.out.println("【全量快照与增量的无缝衔接】");
        List<String> steps = List.of(
                "1) 记录当前 binlog 位点（高水位 offset=500）",
                "2) 全量快照 SELECT 一致性读（相当于 offset=500 时刻的数据）",
                "3) 快照写入下游",
                "4) 从 offset=500 开始消费增量 —— 与快照正好衔接，不重不漏",
                "5) 校验：行数 + checksum 抽查");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  手工衔接的坑：快照与位点的时间差 -> 重段或漏段");
        System.out.println();
    }

    static void layered() {
        System.out.println("【实时数仓分层与口径治理】");
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("ODS", "binlog 原样入库（每表一 topic）");
        layers.put("DWD", "清洗 + 维表关联（订单明细实时化）");
        layers.put("DWS", "实时聚合（每分钟 GMV，43-03 窗口）");
        layers.put("ADS", "大屏/接口直接消费");
        for (Map.Entry<String, String> e : layers.entrySet()) {
            System.out.printf("  %-4s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  口径治理：GMV 定义（含退款？含取消？）实时与离线共用一份定义文档");
        System.out.println("  差异监控：实时值 vs 离线值每日 diff，> 0.5% 告警");
    }

    public static void main(String[] args) {
        eventSemantics();
        snapshotHandoff();
        layered();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"binlog 影像到下游表"的应用器：处理 INSERT/UPDATE/DELETE 三类事件，验证漏掉 DELETE 时的幻影数据现象。
 * 2. 模拟全量增量衔接：给 1000 行做快照（记录位点 500），位点 498~502 的增量事件重放，验证不重不漏的校验方法。
 * 3. 思考题：为什么实时与离线的"口径必须同一份定义"？举一个 GMV 口径分歧导致两个数字打架的具体场景。
 * ============================================================
 */
