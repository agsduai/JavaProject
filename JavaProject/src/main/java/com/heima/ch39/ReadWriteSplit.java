/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-04
 * 课名：读写分离与主从延迟
 * ------------------------------------------------------------
 * 知识点：
 *   - **读写分离的本质是"按一致性要求路由"**：主库承担全部写入与强一致读，从库承担报表、列表等可容忍延迟的读。MySQL 一主多从是标配，复制基于 binlog（逻辑复制），从库重放主库的事务流。
 *   - **主从延迟无法归零，只能管理**：单线程回放（老版本）、大事务、从库跑重查询、DDL 都会拉大 `Seconds_Behind_Master`。典型场景：写库成功 → 立即读从库 → **读不到刚写的数据**，用户看到"订单消失"，客服工单随即到达。
 *   - **强一致读必须回主库**：写后立即读（下单成功页）、支付回调校验、唯一性检查，都走主库。实现手段：代码里用路由注解强制主库、或同一请求内写后读绑定主库（sticky routing，几秒内该用户请求全走主）。
 *   - **半同步复制是折衷**：异步复制主库提交即返回（可能丢最近事务）；半同步要求至少一个从库收到 binlog 才算提交成功，牺牲一点写延迟换 RPO 收敛；组复制（MGR）更严格但成本高。
 *   - **延迟的工程对策**：① 关键路径强制主库；② 版本号校验（读到的数据版本 < 本次写入版本就重试/回主库）；③ 从库只服务"不需要最新数据"的查询（历史订单、报表）；④ 监控 Seconds_Behind_Master 告警阈值（如 >10s）。
 *   - **路由实现层次**：框架层（Spring 的 AbstractRoutingDataSource / ShardingSphere 的主从分离）、代理层（ProxySQL/MaxScale）、云上直接买数据库代理。应用侧要做的核心是把"读写意图"表达清楚。
 *   - 易错点：写后读走了从库（下单页空白）；把报表大查询放主库（拖慢交易）；从库数量无脑加（每多一个从库主库 binlog 线程压力都增加，且延迟更难收敛）；故障切换后应用连接串没切（脑裂期双写）。
 * ============================================================
 */

package com.heima.ch39;

import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class ReadWriteSplit {
    /** 简化数据源：主库实时，从库带固定延迟 */
    static class DataSource {
        final String name;
        final long lagMs;
        final Map<String, String> data = new HashMap<>();

        DataSource(String name, long lagMs) {
            this.name = name;
            this.lagMs = lagMs;
        }

        void write(String key, String value, long nowMs) {
            data.put(key, value + "@" + nowMs);
        }

        String read(String key, long nowMs) {
            String v = data.get(key);
            if (v == null) {
                return null;
            }
            long writtenAt = Long.parseLong(v.substring(v.indexOf('@') + 1));
            return (nowMs - writtenAt) >= lagMs ? v : null;   // 还没复制过来 -> 读不到
        }
    }

    static DataSource master = new DataSource("master", 0);
    static DataSource replica = new DataSource("replica-1", 2000);   // 延迟 2 秒

    static void lagProblem() {
        System.out.println("【主从延迟事故：下单成功页读不到订单】");
        long t = 0;
        master.write("order:1001", "PAID", t);
        String fromReplica = replica.read("order:1001", t);
        System.out.println("  t+0ms   主库写入 order:1001=PAID");
        System.out.println("  t+50ms  用户跳转订单页 -> 读从库 -> "
                + (fromReplica == null ? "null（读不到！页面空白）" : fromReplica));
        System.out.println("  t+2000ms 从库追平 -> 读到 PAID");
        System.out.println("  用户感知：钱扣了，订单没了 -> 客服工单 +1");
        System.out.println();
    }

    static void stickyRouting() {
        System.out.println("【对策一：写后读绑定主库（sticky routing）】");
        long writeAt = 1000;
        master.write("order:1002", "PAID", writeAt);
        long readAt = writeAt + 300;
        boolean sticky = readAt - writeAt < 5000;
        String read = sticky ? master.read("order:1002", readAt)
                : replica.read("order:1002", readAt);
        System.out.printf("  写于 %dms，读于 %dms，窗口 %dms 内 -> %s%n",
                writeAt, readAt, 5000, sticky ? "路由主库" : "路由从库");
        System.out.println("  读到：" + read);
        System.out.println("  实现：会话级标记，写操作后 N 秒内该用户读请求全走主库");
        System.out.println();
    }

    static void versionGuard() {
        System.out.println("【对策二：版本号校验，读旧了就重试主库】");
        long writeAt = 5000;
        master.write("order:1003", "PAID", writeAt);
        String fromReplica = replica.read("order:1003", writeAt + 100);   // 未复制完 -> null
        if (fromReplica == null) {
            System.out.println("  从库尚未复制到该版本 -> 回退主库读");
            System.out.println("  主库读到：" + master.read("order:1003", writeAt + 100));
        }
        System.out.println("  实现细节：写入时记录版本号，读到旧版本（写时间 < 我写的版本）就回主库");
        System.out.println("  适合点查（订单详情），不适合列表（无法对每行做版本比较）");
        System.out.println();
    }

    static void routingMatrix() {
        System.out.println("【路由决策表：把一致性要求说清楚】");
        String[][] rows = {
                {"写入", "主库", "唯一写入点"},
                {"写后立即读", "主库", "下单页、支付回调、唯一性校验"},
                {"列表搜索", "从库", "延迟 2~5s 可接受，产品话术兜底"},
                {"报表/导出", "从库或专用从库", "大查询隔离，别污染交易链路"},
                {"后台管理", "从库", "运营查看允许少量延迟"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s -> %-14s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) {
        lagProblem();
        stickyRouting();
        versionGuard();
        routingMatrix();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 本机搭主从复制（或用 docker-compose），写入后立即读从库复现"读不到"，记录 Seconds_Behind_Master 变化曲线。
 * 2. 为你的项目实现主从路由：给"下单成功页"加 sticky routing，用接口测试证明写后读不再空白。
 * 3. 思考题：为什么列表页适合从库而点查不适合？从"能否对单行做版本比较"的角度分析两种对策的适用边界。
 * ============================================================
 */
