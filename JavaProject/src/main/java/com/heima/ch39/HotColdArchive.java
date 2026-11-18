/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-10
 * 课名：冷热分离与数据归档
 * ------------------------------------------------------------
 * 知识点：
 *   - **80/20 法则在业务数据上同样成立**：绝大多数系统 80% 以上的行是"一年内没被读过"的冷数据，却占着主表的 B+ 树、缓存与备份窗口。**归档是性价比最高的"分库分表替代方案"**——很多声称需要分表的系统，归档完主表直接回到千万行以内。
 *   - **冷热分离的判定标准**：访问频率（近 90 天命中率）、查询模式（是否必须与热数据同表查询，如"最近三个月订单"）、法规要求（交易数据保留 N 年）。冷数据特点：只读、查询低频且通常按时间批量。
 *   - **三种归档形态**：**同库归档表**（orders -> orders_archive，最简单，但同一实例仍有压力）；**归档库/实例**（独立实例，主库直接瘦身，最常用）；**对象存储/数据仓库**（压缩转储 + 查询走数仓，适合超长保留期）。按"查询需求有多强"选择。
 *   - **归档任务的正确姿势**：按时间批次（每天搬一天前已完结订单），`INSERT INTO archive SELECT ... LIMIT N` + `DELETE ... LIMIT N` 分批执行，**绝不一个事务搬千万行**（大事务锁表 + 主从延迟爆炸）。搬运要有进度表与断点续跑。
 *   - **一致性窗口**：搬走的那一刻起，查询"历史订单"要去归档库——**应用层路由必须先上线**（按时间判断查主表还是归档库），否则用户查不到已归档订单。稳妥顺序：双查期（先查主表查不到再查归档）→ 验证 → 停双查。
 *   - **归档不是删除**：法规要求的保留期要真保留；物理删除要有不可逆的备份（对象存储 + 校验和）。"归档完就从备份里也删掉"是重大合规事故。
 *   - 易错点：归档任务无监控（半夜失败没人发现，主表继续膨胀）；归档后索引没同步建（归档库查询慢）；把"可能还要用的数据"归档到查询极慢的存储（用户投诉）；回捞流程没演练（真要恢复一条订单时手忙脚乱）。
 * ============================================================
 */

package com.heima.ch39;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class HotColdArchive {
    record Order(long id, String status, long createTime) {
    }

    static final long ARCHIVE_BEFORE = 1704000000000L;   // 2024-01-01

    static boolean isCold(Order o) {
        return o.createTime() < ARCHIVE_BEFORE;
    }

    static List<Order> hot = new ArrayList<>();
    static List<Order> archive = new ArrayList<>();

    static void initData() {
        hot.clear();
        archive.clear();
        // 前 499 条在归档线之前（冷），其余在归档线之后（热）
        for (long i = 1; i <= 1000; i++) {
            long t = ARCHIVE_BEFORE + (i - 500) * 900_000L;
            hot.add(new Order(i, i % 5 == 0 ? "DONE" : "PAID", t));
        }
        System.out.printf("  初始主表 %d 行（其中 499 行是归档线之前的冷数据）%n", hot.size());
    }

    static void batchArchive() {
        System.out.println("【分批归档：每批 N 行，绝不一个事务搬全量】");
        initData();
        int batchSize = 100, moved = 0, round = 0;
        while (round < 20) {
            List<Order> batch = new ArrayList<>();
            for (Order o : hot) {
                if (isCold(o) && batch.size() < batchSize) {
                    batch.add(o);
                }
            }
            if (batch.isEmpty()) {
                break;
            }
            hot.removeAll(batch);
            archive.addAll(batch);
            moved += batch.size();
            round++;
            System.out.printf("  批次 %2d：搬走 %d 行，主表剩 %d 行，归档 %d 行%n",
                    round, batch.size(), hot.size(), archive.size());
        }
        System.out.printf("  完成：主表 %d 行（瘦了 %.0f%%），归档库 %d 行%n",
                hot.size(), 100.0 * moved / (moved + hot.size()), archive.size());
        System.out.println("  SQL 模板：INSERT INTO orders_archive SELECT ... WHERE id BETWEEN a AND b;");
        System.out.println("          DELETE FROM orders WHERE id BETWEEN a AND b;（同一事务，每批 1000）");
        System.out.println();
    }

    static void queryRouting() {
        System.out.println("【查询路由：应用必须知道冷热边界】");
        Order recent = hot.get(hot.size() - 1);
        Order old = archive.get(0);
        Order probe = recent;
        String route = isCold(probe) ? "归档库" : "主库";
        System.out.printf("  查订单 %d（%s）-> 路由：%s -> %s%n",
                probe.id(), isCold(probe) ? "冷" : "热", route, "命中");
        probe = old;
        route = isCold(probe) ? "归档库" : "主库";
        System.out.printf("  查订单 %d（%s）-> 路由：%s -> %s%n",
                probe.id(), isCold(probe) ? "冷" : "热", route, "命中");
        System.out.println("  过渡期双查：主表查不到 -> 归档库兜底 -> 两边都有以主表为准");
        System.out.println("  上线顺序：路由改造先于归档任务，禁止先搬数据再改代码");
        System.out.println();
    }

    static void checklist() {
        System.out.println("【归档上线检查单】");
        List<String> items = List.of(
                "冷热判定规则经业务确认（法务：保留期）",
                "归档库索引按查询模式建（时间+订单号，不是照抄主表）",
                "应用路由/双查逻辑已上线并验证",
                "批次大小压测过（主从延迟在阈值内）",
                "进度表 + 失败重跑 + 归档告警",
                "回捞流程演练过一次（归档 -> 主库恢复单条）",
                "备份与合规：归档数据保留与销毁策略书面化");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        batchArchive();
        queryRouting();
        checklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个真实归档脚本（SQL 或程序）：分批搬运 + 进度记录 + 失败重跑，在测试库跑通并记录每批的主从延迟变化。
 * 2. 复现"先归档后改路由"的事故：归档一批数据但应用仍查主表，观察用户视角现象，然后按正确顺序重做。
 * 3. 思考题：为什么归档库的索引通常不需要照抄主表？从冷数据的查询模式推导索引设计原则。
 * ============================================================
 */
