/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：49-05
 * 课名：CQRS 读写分离建模
 * ------------------------------------------------------------
 * 知识点：
 *   - **CQRS（命令查询职责分离）把写模型与读模型分开建模**：写模型（聚合）保证不变量与事务，为业务正确性服务；读模型（查询视图）为查询体验服务——**两者可以不同结构、不同存储、不同一致性**。"一个模型既当事务守卫又当查询视图"是复杂系统的常见负担。
 *   - **读写不对称的根源**：写要满足不变量（订单聚合完整），查要跨表聚合展示（订单列表要商品名/用户名/物流状态）——**写模型稠密归一，读模型稀疏反范式**。强行用一个模型两头迁就（大宽表 + 事务守卫）两头都难受。
 *   - **CQRS 的实现光谱**：轻量级——同一个库，写走聚合、读走 SQL 视图（**90% 场景够用**）；重量级——写库 + 事件投影到读库（ES/宽表），最终一致（第 38 章 ES 同步的架构身份就是"读模型投影"）。
 *   - **事件驱动投影**：领域事件 -> 投影器（Projection）更新读模型——**读模型可整体重建**（事件回放，46-05 时间旅行同源思想），坏了重建而不是修补。
 *   - **一致性的明确契约**：写后读自己的写（45-02）在读模型上如何保证——版本号 + 等待投影 + 超时回主读；**契约写进设计文档**。
 *   - **何时需要重量级 CQRS**：查询维度与事务维度严重分歧（多维度列表/全文搜索/报表）、读性能独立伸缩、读模型可容忍秒级延迟。**不需要时重量级 CQRS 是分布式事务税**。
 *   - 易错点：轻量级需求上了双库（维护投影管道，成本翻倍）；读模型无版本契约（写后读旧值客诉）；投影不可重建（读模型坏了手工修数据）；把 CQRS 理解成"必须事件溯源"（CQRS 可独立使用）。
 * ============================================================
 */

package com.heima.ch49;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class CqrsPattern {
    /** 写模型：聚合，保证不变量 */
    static class OrderWriteModel {
        final Map<String, Integer> orders = new LinkedHashMap<>();   // id -> amountCents
        final Map<String, String> statuses = new LinkedHashMap<>();

        void place(String orderId, int amountCents) {
            if (amountCents <= 0) {
                throw new IllegalArgumentException("金额必须为正");
            }
            orders.put(orderId, amountCents);
            statuses.put(orderId, "PAID");
            System.out.printf("  [写模型] 订单 %s 落库（不变量已校验）%n", orderId);
        }
    }

    /** 读模型：为列表查询而生的反范式视图 */
    record OrderListItem(String orderId, String status, String userName,
                         String productNames, int amountCents) {
    }

    static class OrderListView {
        final List<OrderListItem> rows = new ArrayList<>();

        /** 投影器：订阅事件更新视图（可整体重建） */
        void project(String orderId, String status, String userName,
                     String productNames, int amountCents) {
            rows.add(new OrderListItem(orderId, status, userName, productNames, amountCents));
            System.out.printf("  [投影] 视图行更新：%s（%s %s %s）%n", orderId, status, userName, productNames);
        }

        List<OrderListItem> listByUser(String userName) {
            List<OrderListItem> hits = rows.stream()
                    .filter(r -> r.userName().equals(userName)).toList();
            System.out.printf("  [读模型] 按 user=%s 查询 -> %d 行（一次反范式读，无 join）%n",
                    userName, hits.size());
            return hits;
        }
    }

    static void twoModels() {
        System.out.println("【同一业务：写模型与读模型各自成形】");
        OrderWriteModel write = new OrderWriteModel();
        write.place("9001", 6900);
        OrderListView view = new OrderListView();
        view.project("9001", "PAID", "小王", "iPhone 手机壳, 手机支架", 6900);
        view.listByUser("小王");
        System.out.println("  写模型关心不变量，读模型关心展示 —— 结构不同天经地义");
        System.out.println();
    }

    static void spectrum() {
        System.out.println("【CQRS 实现光谱：从轻到重】");
        String[][] rows = {
                {"L1 同库视图", "SQL 视图/接口聚合", "90% 场景，零管道成本"},
                {"L2 双模型同库", "写走聚合表、读走宽表（同库同步）", "查询形状差异大时"},
                {"L3 事件投影异构", "事件 -> ES/宽表，最终一致", "全文检索/多维列表（38 章正是 L3）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  升级原则：从 L1 开始，查询需求推不动再升");
        System.out.println();
    }

    static void rebuild() {
        System.out.println("【投影可重建：读模型的修复姿势】");
        List<String> steps = List.of(
                "1) 读模型损坏/口径变更 -> 不修补，重建",
                "2) 清空视图 -> 从事件流重放（46-05 时间旅行同源）",
                "3) 双写期：新旧视图并存，对比一致后切流",
                "4) 投影器幂等：同一事件重放结果不变（39 章幂等原则）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  前提：事件是完整真源（Event Sourcing 的价值由此显现，49-06）");
        System.out.println();
    }

    static void consistencyContract() {
        System.out.println("【写后读契约：读模型的最后一块拼图】");
        List<String> rules = List.of(
                "写侧返回版本号（事件 revision）",
                "读侧校验：投影版本 >= 写入版本才返回；否则回退写模型读",
                "超时兜底：等待投影 500ms 超时 -> 回写模型（正确性优先）",
                "契约写进接口文档（42 章新鲜度元数据同款思想）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  反例：支付成功页读到“未支付”-> 客诉 —— 45-02 read-your-writes 的落地");
    }

    public static void main(String[] args) {
        twoModels();
        spectrum();
        rebuild();
        consistencyContract();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为订单列表实现 L1 轻量 CQRS（聚合写 + SQL 视图读），识别出"哪一步查询形状差异大"再评估是否升 L3。
 * 2. 实现"投影重建"模拟：故意破坏视图，从事件流重放恢复，验证投影器幂等。
 * 3. 思考题：为什么"轻量级 CQRS 从 L1 开始"是纪律？从投影管道的维护成本与查询需求的真实分布分析。
 * ============================================================
 */
