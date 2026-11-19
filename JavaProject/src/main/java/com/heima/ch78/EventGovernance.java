/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-10
 * 课名：事件治理与事件目录
 * ------------------------------------------------------------
 * 知识点：
 *   - **事件目录（Event Catalog）是事件驱动架构的"资产清单"**：**每个事件有名称、owner、版本、schema、生命周期**——**没有目录，半年后没人知道谁在发、谁在消费**。
 *   - **命名规范**：**`domain.entity.action.vN`**（如 `order.order.created.v1`）——**统一命名才能检索、才能治理**；**事件名用"过去式"**（已发生的事实），**命令用"祈使式"**（CreateOrder）。
 *   - **生命周期**：**ACTIVE（在用）-> DEPRECATED（标记废弃）-> RETIRED（停止）**——**废弃要先通知消费者 + 双写过渡，不能直接删**（78-07）。
 *   - **owner 与 SLA**：**每个事件必须有负责团队**（出了事找谁）、**schema 变更要走评审**、**消费方要登记**——**事件是"跨团队接口"，治理成本必须有人承担**。
 *   - 与 64 章的关系：64 章的"API 治理"（版本/契约/配额）在事件侧同样适用——**API 与事件是"同步/异步"两种契约**。
 *   - 易错点：事件命名随意（orderCreated/OrderCreated/order_created 混用）；无 owner（变更无人评审）；不登记消费者（删除事件才知道有人用）；生命周期不落地（废弃事件永远在发）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

public class EventGovernance {
    enum Lifecycle { ACTIVE, DEPRECATED, RETIRED }

    record EventDef(String name, String owner, int version, Lifecycle lifecycle,
                    List<String> consumers) {
        boolean validName() {
            return Pattern.matches("^[a-z]+\\.[a-z]+\\.[a-z]+\\.v\\d+$", name);
        }

        String shortName() {
            int idx = name.lastIndexOf(".v");
            return idx < 0 ? name : name.substring(0, idx);
        }
    }

    static final List<EventDef> catalog = new ArrayList<>();

    static void register(EventDef def) {
        catalog.add(def);
        System.out.printf("  注册 %-28s owner=%-10s %s 消费者=%s%n",
                def.name(), def.owner(), def.lifecycle(), def.consumers());
    }

    static void validate() {
        System.out.println();
        System.out.println("【命名校验】");
        for (EventDef d : catalog) {
            System.out.printf("  %-30s %s%n", d.name(), d.validName() ? "合法" : "非法（应为 domain.entity.action.vN）");
        }
    }

    static void transitions() {
        System.out.println();
        System.out.println("【生命周期流转】");
        EventDef d = catalog.get(0);
        System.out.println("  " + d.name() + " ACTIVE -> DEPRECATED（通知消费者，双写）");
        System.out.println("  " + d.name() + " DEPRECATED -> RETIRED（消费者全部迁移后停止）");
        System.out.println("  禁止：ACTIVE 直接 RETIRED（消费者会断供）");
    }

    static void ownership() {
        System.out.println();
        System.out.println("【治理检查】");
        for (EventDef d : catalog) {
            List<String> issues = new ArrayList<>();
            if (d.owner() == null || d.owner().isBlank()) {
                issues.add("缺 owner");
            }
            if (d.consumers().isEmpty()) {
                issues.add("无消费者登记");
            }
            if (!d.validName()) {
                issues.add("命名不合规");
            }
            if (d.lifecycle() == Lifecycle.RETIRED && !d.consumers().isEmpty()) {
                issues.add("已退役但仍有消费者");
            }
            System.out.printf("  %-30s %s%n", d.name(),
                    issues.isEmpty() ? "通过" : "问题：" + issues);
        }
    }

    public static void main(String[] args) {
        System.out.println("【事件目录】");
        register(new EventDef("order.order.created.v1", "交易团队", 1,
                Lifecycle.ACTIVE, List.of("库存服务", "积分服务", "推荐服务")));
        register(new EventDef("order.order.paid.v1", "交易团队", 1,
                Lifecycle.ACTIVE, List.of("履约服务", "财务服务")));
        register(new EventDef("order.order.created.v2", "交易团队", 2,
                Lifecycle.ACTIVE, List.of("积分服务")));
        register(new EventDef("OrderCreated", "未知", 1,
                Lifecycle.ACTIVE, List.of()));
        register(new EventDef("stock.stock.deducted.v1", "库存团队", 1,
                Lifecycle.DEPRECATED, List.of("履约服务")));
        register(new EventDef("legacy.event.retired.v1", "老系统", 1,
                Lifecycle.RETIRED, List.of("报表服务")));

        validate();
        transitions();
        ownership();

        System.out.println();
        System.out.println("【事件 vs API 治理】");
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("契约形式", "同步 OpenAPI / 异步 Schema");
        rows.put("版本策略", "URL/Header / 事件名 vN");
        rows.put("兼容性", "加可选安全 / 同左");
        rows.put("Owner", "接口负责人 / 事件 owner");
        rows.put("下线", "灰度 + 通知 / DEPRECATED + 双写");
        rows.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));

        System.out.println();
        System.out.println("【结论】事件目录 = 资产清单 + 责任人 + 生命周期；治理从登记开始");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的系统建一个事件目录（名称/owner/版本/消费者/生命周期），校验命名是否合规。
 * 2. 设计一次事件下线流程：DEPRECATED -> 通知 -> 双写 -> 消费者迁移 -> RETIRED。
 * 3. 思考题：为什么"没有 owner 的事件"最危险？从"变更无人评审 + 故障无人负责"分析。
 * ============================================================
 */
