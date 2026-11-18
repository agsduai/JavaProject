/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-09
 * 课名：数据同步：让搜索库跟上数据库
 * ------------------------------------------------------------
 * 知识点：
 *   - **同步的目标是一致性体验**：用户下单成功后，搜不到刚改的库存/状态，就是同步问题。四种主流方案：**同步双写**（业务代码里写完 MySQL 再写 ES）、**异步双写（MQ）**、**基于 binlog 的 CDC（Canal → MQ → ES）**、**定时全量/增量拉取**。
 *   - **同步双写的问题在代码结构**：ES 故障会拖垮主业务（要 try-catch 降级）、成功标准不一致（DB 成功 ES 失败算什么）、耦合了存储细节。适合小系统过渡，不适合作为长期架构。
 *   - **MQ 异步双写是最常用的平衡点**：业务事务只保证 MySQL，发一条领域事件（如 `ProductChanged`）到 MQ，消费者更新 ES。**丢消息要靠可靠投递**（本地消息表，第 28 章的方法）+ **消费失败重试 + 死信告警**。
 *   - **CDC（Canal）方案解耦最彻底**：伪装成 MySQL 从库订阅 binlog，业务代码零侵入；天然捕获所有写入（包括别的服务/脚本改的）。代价是引入组件链路、binlog 格式要求（ROW）、以及"删库脚本也会被同步"（要白名单表）。
 *   - **顺序与幂等是同步的正确性底线**：同一商品两次变更必须按 binlog/MQ 顺序应用（按商品 ID 分区）；消费者必须幂等（版本号/更新时间比较，旧事件丢弃）。**乱序 + 不幂等 = 数据悄悄回退**。
 *   - **兜底是定时校验修复**：任何方案都可能丢一条，每天/每小时跑对账任务（比对 MySQL 与 ES 的版本号/更新时间），差异记录并修复。**架构上承认"最终一致"，工程上用对账收敛误差**。
 *   - 易错点：删了 MySQL 数据 ES 里还在（同步管道只处理 insert/update）；ES 写入失败被 try-catch 吞掉（连日志都没有，数据悄悄落后）；大促批量改价直接打爆 ES 写入（要削峰与 bulk 合并）；同步表全量订阅（应按业务白名单过滤，减少无谓写入）。
 * ============================================================
 */

package com.heima.ch38;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DataSync {
    record Row(String status, long version, long updatedAt) {
    }

    /** 简化事件：商品变更 + 版本号 */
    record Event(long productId, long version, String op) {
    }

    static final Map<Long, Row> MYSQL = new LinkedHashMap<>();
    static final Map<Long, Row> ES = new LinkedHashMap<>();
    static final List<String> SYNC_LOG = new ArrayList<>();

    static void compareApproaches() {
        System.out.println("【四种同步方案对比】");
        String[][] rows = {
                {"同步双写", "低", "ES 故障拖垮主业务，代码耦合", "小系统过渡"},
                {"MQ 异步", "中", "要处理可靠投递/幂等/顺序", "绝大多数业务（推荐起步）"},
                {"CDC binlog", "中高", "多一套组件链路，运维成本", "大系统、多写入方"},
                {"定时拉取", "低", "延迟分钟级，无实时性", "兜底对账、报表类索引"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s 实时性%-3s %s%n      %s%n",
                    r[0], r[1], r[2], r[3]);
        }
        System.out.println();
    }

    static void mqSync() {
        System.out.println("【MQ 异步同步：事件 -> 消费 -> 幂等写 ES】");
        updateDb(1001, 1, "ONSALE");
        updateDb(1001, 2, "OFFSALE");
        List<Event> mq = List.of(new Event(1001, 2, "OFFSALE"), new Event(1001, 1, "ONSALE"));
        System.out.println("  MQ 里事件乱序到达：" + mq);
        for (Event e : mq) {
            Row current = ES.get(e.productId());
            if (current != null && e.version() <= current.version()) {
                SYNC_LOG.add("事件 v" + e.version() + " 丢弃（版本过旧，幂等生效）");
                continue;
            }
            ES.put(e.productId(), new Row(e.op(), e.version(), e.version()));
            SYNC_LOG.add("事件 v" + e.version() + " 应用 -> status=" + e.op());
        }
        for (String s : SYNC_LOG) {
            System.out.println("  " + s);
        }
        System.out.println("  MySQL 现状：" + MYSQL.get(1001) + "  ES 现状：" + ES.get(1001));
        System.out.println("  结论：按版本幂等，乱序也不回退；顺序问题退化为“旧事件被丢弃”");
        System.out.println();
    }

    static void updateDb(long id, long version, String status) {
        MYSQL.put(id, new Row(status, version, version));
    }

    static void reconcile() {
        System.out.println("【对账兜底：承认丢失，定时修复】");
        // 模拟 ES 漏了一条更新
        MYSQL.put(1002L, new Row("ONSALE", 5, 5));
        ES.put(1002L, new Row("OFFSALE", 4, 4));
        System.out.println("  1002：MySQL " + MYSQL.get(1002L) + " vs ES " + ES.get(1002L));
        if (ES.get(1002L).version() < MYSQL.get(1002L).version()) {
            ES.put(1002L, MYSQL.get(1002L));
            System.out.println("  对账任务发现版本落后 -> 以 MySQL 为准修复 ES");
        }
        System.out.println("  对账 SQL 思路：两边各取 version/updatedAt，diff 出落后清单");
        System.out.println("  原则：同步管道保证“快”，对账任务保证“最终对”");
    }

    public static void main(String[] args) {
        compareApproaches();
        mqSync();
        reconcile();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把示例扩展成"本地消息表"版本：业务事务里写消息表，后台投递 MQ，失败重试，画出状态机并实现核心表结构。
 * 2. 实现按商品 ID 分区的顺序消费模拟：多线程乱序消费同 ID 事件，验证无版本幂等时会回退、有版本幂等时结果正确。
 * 3. 思考题：CDC 方案里，某同事直接 UPDATE MySQL 修数据，ES 会不会跟着变？这是优点还是隐患？给出治理规则。
 * ============================================================
 */
