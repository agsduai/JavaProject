/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-02
 * 课名：CAP 与一致性谱系
 * ------------------------------------------------------------
 * 知识点：
 *   - **CAP 的准确读法**：网络分区（P）发生时，**一致性（C）与可用性（A）只能二选一**——这不是"三选二"的选择题，而是"分区时你选哪边"的承诺。无分区时 C 和 A 可以兼得。**CAP 是分区预案的设计约束，不是架构选型的口号**。
 *   - **一致性是一个谱系而非开关**：**线性一致**（读到的值与所有进程的操作顺序一致，像单机）> **顺序一致**（同客户端有序，跨客户端可乱）> **因果一致**（有因果关系的操作有序）> **最终一致**（停止写入后收敛）。**不同接口可以承诺不同级别**——同一系统里"配置读取"线性一致、"计数"最终一致很常见。
 *   - **线性一致的代价**：每次读都要与多数派交互（或读主），延迟 = 一次 RTT 往上，吞吐受限。**只对真正需要的接口承诺线性一致**（选主、锁、配置变更确认），其余用本地读。
 *   - **最终一致的业务后果要先想清楚**：余额查询最终一致 = 用户刚充 100 元查不到（客诉）；库存扣减最终一致 = 超卖。**"读自己的写"（read-your-writes）是最常见的最低要求**：用户提交后必须看到自己刚写的数据（39-04 sticky routing 的理论根基）。
 *   - **BASE 与 ACID 的适用边界**：跨服务的业务流程用 BASE（最终一致 + 补偿，39-09 的本地消息表），单库事务仍用 ACID。**"放弃强一致"不是放弃正确性**，而是把正确性从"数据库保证"变成"架构保证"（幂等、对账、补偿）。
 *   - **PACELC 补充了无分区时的取舍**：分区时选 C 或 A；**无分区时选延迟（L）或一致性（C）**——线性一致每次多一次 RTT，这就是"为什么默认读本地副本"的理论解释。
 *   - 易错点：把 CAP 当"三选二"选型表（P 是必选项不是选项）；以为最终一致 = 数据可以随便错（收敛性与收敛时间是有契约的）；所有接口都上线性一致（延迟与吞吐双输）；把"读写主库"当成唯一解（读自己的写只需要会话粘性）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class CapSpectrum {
    record Read(String scenario, String level, String cost, String verdict) {
    }

    static void consistencyLadder() {
        System.out.println("【一致性谱系：同一系统允许分级承诺】");
        List<Read> reads = List.of(
                new Read("选主结果查询", "线性一致", "每次读多数派（+1 RTT）", "必须：脑裂的防线"),
                new Read("配置读取", "顺序一致", "读主或版本号校验", "变更后短暂窗口可接受"),
                new Read("商品详情页浏览数", "最终一致", "读本地副本（无额外代价）", "偏差分钟级，业务无感"),
                new Read("库存扣减后查询", "读自己的写", "会话粘性（39-04）", "用户感知的底线"));
        for (Read r : reads) {
            System.out.printf("  %-12s %-8s %-20s %s%n", r.scenario(), r.level(), r.cost(), r.verdict());
        }
        System.out.println("  原则：一致性级别按接口的业务后果定价，不整体一刀切");
        System.out.println();
    }

    static void partitionChoice() {
        System.out.println("【分区时的选择：CP 与 AP 的真实含义】");
        record SystemChoice(String name, String choice, String behavior, String scene) {
        }
        List<SystemChoice> systems = List.of(
                new SystemChoice("ZooKeeper/etcd", "CP", "少数派分区时拒绝服务", "配置与协调，错比停更糟"),
                new SystemChoice("Cassandra 类", "AP", "分区时两侧都可写，恢复后收敛", "社交流、计数类"),
                new SystemChoice("ES", "可调", "38-10：write 块与水位", "按索引用途权衡"));
        for (SystemChoice s : systems) {
            System.out.printf("  %-14s 选%s  %s%n      %s%n", s.name(), s.choice(), s.behavior(), s.scene());
        }
        System.out.println("  同一个公司里 CP 与 AP 系统并存：按数据类型选，不是全局站队");
        System.out.println();
    }

    static void pacelc() {
        System.out.println("【PACELC：无分区时延迟与一致性的交换】");
        double rtt = 1.5;    // ms，同机房一次往返
        List<String> rows = List.of(
                "线性一致读：+1 次多数派 RTT ≈ +" + rtt + "ms（P99 受最慢副本拖累）",
                "读主：0 额外 RTT，但主所在机房抖动时全量读受影响",
                "读本地副本：0 RTT，可能读到旧值（需要业务容忍）",
                "写路径：同步写多数派 = 1 RTT；异步复制 = 立即返回但可能丢");
        for (String r : rows) {
            System.out.println("  " + r);
        }
        System.out.println("  实践：把“哪些接口愿意多付 1 RTT”写进设计文档");
        System.out.println();
    }

    static void readYourWrites() {
        System.out.println("【读自己的写：最低一致性契约】");
        List<String> steps = List.of(
                "1) 用户提交订单 -> 写主库成功（版本 v17）",
                "2) 30ms 后用户刷新订单页 -> 若路由到从库（可能还在 v16）",
                "3) 会话粘性：写操作后 N 秒内该用户请求全走主库（39-04）",
                "4) 或版本校验：读到 v<17 就回主库重读");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：充值 100 元后余额未变 —— 客诉率最高的“最终一致”误用");
    }

    public static void main(String[] args) {
        consistencyLadder();
        partitionChoice();
        pacelc();
        readYourWrites();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的系统列出接口一致性分级表（≥6 个接口），标注级别、代价与业务理由，找出一个"可以降级"与一个"必须升级"的接口。
 * 2. 模拟读自己的写：写主库后立刻读从库（复现旧值），实现会话粘性修复，记录客诉场景的消除过程。
 * 3. 思考题：为什么"分区时 CP"的系统在大促高峰反而更稳？从"拒绝服务 vs 返回错误数据"的下游影响链分析。
 * ============================================================
 */
