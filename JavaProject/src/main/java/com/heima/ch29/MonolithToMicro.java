/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-01
 * 课名：从单体到微服务：拆与不拆
 * ------------------------------------------------------------
 * 知识点：
 *   - **单体应用**：一个进程、一个数据库、一次部署。优点是简单——本地方法调用、事务天然一致、排查一条日志链；缺点是耦合——改一个模块要全量回归、扩一个热点功能要整体扩容、一个死循环拖垮全站、技术栈被锁死。
 *   - **微服务**：按业务能力拆成多个独立部署的服务，各自有进程、数据库、发布节奏。优点是可以独立扩缩容、故障隔离、团队并行；代价是**分布式复杂度**——远程调用会超时、网络会分区、事务不再原子、链路变长排查变难。
 *   - 康威定律：系统架构会向组织沟通结构收敛。团队没拆开就强行拆服务，只会得到"分布式单体"——服务拆了但每次发版还得一起发。
 *   - 判断标准不是"大不大"而是"变化频率是否不同、扩缩容需求是否不同、故障是否应该隔离"。日活几千的内部系统，单体 + 模块化往往是最优解。
 *   - 演进路径：单体（模块化分包）→ 抽出第一个真正独立的服务（通常是用户/认证或消息通知）→ 按瓶颈逐步拆 → 配套注册中心、网关、配置中心、链路追踪。
 *   - 易错：一上来就按技术分层拆（controller 服务、service 服务、dao 服务），这会让一次请求跨 3 个服务，延迟翻倍还没有任何隔离收益。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MonolithToMicro {
    record Module(String name, int changePerMonth, int qps, boolean critical) { }

    public static void main(String[] args) {
        List<Module> modules = new ArrayList<>();
        modules.add(new Module("用户与认证", 2, 300, true));
        modules.add(new Module("商品", 6, 2000, true));
        modules.add(new Module("订单", 8, 800, true));
        modules.add(new Module("积分", 1, 100, false));
        modules.add(new Module("消息通知", 1, 150, false));
        modules.add(new Module("报表导出", 3, 5, false));

        System.out.println("=== 单体：所有模块一个进程 ===");
        int totalQps = 0;
        for (Module m : modules) {
            totalQps += m.qps();
        }
        System.out.println("  总 QPS " + totalQps + "，扩缩容只能整体进行");
        System.out.println("  报表导出（5 QPS）跑满 CPU 时，商品（2000 QPS）一起变慢");

        System.out.println();
        System.out.println("=== 拆分判断：变化频率 / 流量特征 / 故障隔离 ===");
        System.out.printf("  %-12s %-10s %-10s %-10s %s%n", "模块", "月变更", "QPS", "核心链路", "建议");
        for (Module m : modules) {
            String advice;
            if (m.qps() >= 1500 || m.changePerMonth() >= 6) {
                advice = "优先拆（流量或变更频率独立）";
            } else if (!m.critical() && m.qps() < 200) {
                advice = "可后拆（旁路，故障不该影响主链路）";
            } else {
                advice = "先留在单体，模块化分包即可";
            }
            System.out.printf("  %-12s %-10d %-10d %-10s %s%n",
                    m.name(), m.changePerMonth(), m.qps(), m.critical() ? "是" : "否", advice);
        }

        System.out.println();
        System.out.println("=== 拆分后的调用代价 ===");
        int localCallUs = 1;
        int remoteCallMs = 5;
        System.out.println("  本地方法调用约 " + localCallUs + "us，跨服务 HTTP 调用约 " + remoteCallMs + "ms");
        System.out.println("  一次请求跨 3 个服务 = " + (3 * remoteCallMs) + "ms 纯网络开销（还没算业务）");
        System.out.println("  -> 拆得越细，越要用聚合/批量调用把往返次数压下来");

        System.out.println();
        System.out.println("=== 反面案例：按技术分层拆 ===");
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("controller-service", "只做参数校验和转发");
        bad.put("service-service", "只做业务编排");
        bad.put("dao-service", "只做数据访问");
        for (Map.Entry<String, String> e : bad.entrySet()) {
            System.out.println("  " + e.getKey() + "：" + e.getValue());
        }
        System.out.println("  一次请求跨 3 跳，任何一层扩容都要连带扩容，事务无法保证，纯亏");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给你做过的项目列一张模块表，标注月变更次数与 QPS，用示例里的规则判断先拆哪个。
 * 2. 说明"分布式单体"的典型特征，以及它比单体更糟的原因。
 * 3. 为什么按技术分层拆分服务是反模式？举一个具体的坏处。
 * ============================================================
 */
