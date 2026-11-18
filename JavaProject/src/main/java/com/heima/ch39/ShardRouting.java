/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-06
 * 课名：分片路由与扩容
 * ------------------------------------------------------------
 * 知识点：
 *   - **路由算法两大流派**：**hash 取模**（`shard = hash(key) % N`）数据分布均匀，但扩容时几乎全部数据要迁移（模数变了）；**range 范围**（按时间/ID 区间）扩容只加新区间，但热点集中在最新区间，且需要维护区间表。
 *   - **一致性哈希解决"扩容只迁一小部分"**：把分片与 key 都映射到环上，key 归顺时针遇到的第一个节点。节点数从 N 变 N+1 时，只有"环上新节点与前一个节点之间"的区间数据需要迁移（约 1/N+1），其余不动。虚拟节点（每物理节点 100~200 个虚拟槽位）让分布更均匀。
 *   - **固定槽数（slot）方案是工程主流**：不直接对节点取模，而是对 1024/4096 个槽取模，槽再映射到节点。扩容时只调整"槽→节点"映射（迁移部分槽的数据），路由公式永远不变。Redis Cluster 的 16384 slot 就是这个思想。
 *   - **扩容动作三步**：① 新节点加入，重新分配槽；② 按映射双写（新数据同时写新旧槽位）；③ 后台迁移存量数据，迁完切读、下线旧槽。**全程不停服**的关键是双写期与对账。
 *   - **翻倍扩容最省心**：8 分片 -> 16 分片时，旧分片 i 的数据一半搬到 i+8（`hash % 16` 与 `hash % 8` 的关系保证只迁一半且目标可计算）。所以**分片数从一开始就选 2 的幂**，给翻倍留好路。
 *   - **range 按时间分片的天然搭档**：订单按月分表，最新表承载写入热点，历史表归档——查询带时间条件直接路由单表。适合"时间维度主导"的业务（日志、交易流水），不适合"最新数据最热且查询不带时间"的场景。
 *   - 易错点：直接 `% N` 后期扩容（全量迁移，等于重做一遍）；一致性哈希不配虚拟节点（分布倾斜）；迁移期间只双写不回滚旧写（故障时丢数据窗口）；按 range 分片但查询不带 range 条件（退化为广播）。
 * ============================================================
 */

package com.heima.ch39;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

public class ShardRouting {
    /** hash 取模：8 分片 */
    static int modRoute(long key, int n) {
        return (int) (Math.abs(hash(key)) % n);
    }

    static long hash(long key) {
        return key * 31 + 7;
    }

    static void expansionCost() {
        System.out.println("【取模扩容的迁移成本】");
        int n = 1_000;
        int moved = 0;
        for (long i = 1; i <= n; i++) {
            if (modRoute(i, 8) != modRoute(i, 16)) {
                moved++;
            }
        }
        System.out.printf("  8 分片 -> 16 分片：%d%% 的数据换了分片%n", moved);
        moved = 0;
        for (long i = 1; i <= n; i++) {
            if (modRoute(i, 8) != modRoute(i, 9)) {
                moved++;
            }
        }
        System.out.printf("  8 分片 -> 9  分片：%d%% 的数据换了分片（几乎全部）%n", moved);
        System.out.println("  结论：扩容要翻倍（8->16），不翻倍等于重做数据分布");
        System.out.println();
    }

    /** 一致性哈希：虚拟节点 + TreeMap 环 */
    static class ConsistentHash {
        final TreeMap<Long, String> ring = new TreeMap<>();

        ConsistentHash(List<String> nodes, int vnodes) {
            for (String n : nodes) {
                for (int v = 0; v < vnodes; v++) {
                    ring.put(Math.abs(hash(n.hashCode() * 1000L + v)), n);
                }
            }
        }

        String route(long key) {
            Map.Entry<Long, String> e = ring.ceilingEntry(hash(key));
            return e != null ? e.getValue() : ring.firstEntry().getValue();
        }

        long hash(long key) {
            return key * 1103515245L + 12345;
        }
    }

    static void consistentHashDemo() {
        System.out.println("【一致性哈希：加一个节点只迁 1/(N+1)】");
        ConsistentHash c3 = new ConsistentHash(List.of("db1", "db2", "db3"), 100);
        ConsistentHash c4 = new ConsistentHash(List.of("db1", "db2", "db3", "db4"), 100);
        int moved = 0, total = 10_000;
        for (long i = 0; i < total; i++) {
            if (!c3.route(i).equals(c4.route(i))) {
                moved++;
            }
        }
        System.out.printf("  %d 个 key 从 3 节点扩到 4 节点：迁移 %d%%（理论 25%%）%n",
                total, moved / (total / 100));
        for (long probe : new long[]{7, 77, 777}) {
            System.out.printf("  key %d: 3节点=%s -> 4节点=%s%n",
                    probe, c3.route(probe), c4.route(probe));
        }
        System.out.println("  虚拟节点 100 个/台：让环上分布均匀，避免某台独占半环");
        System.out.println();
    }

    static void slotPlan() {
        System.out.println("【固定槽位：路由公式永不变，扩容只改映射】");
        int slots = 1024;
        Map<Integer, String> slotMap = new LinkedHashMap<>();
        for (int s = 0; s < slots; s++) {
            slotMap.put(s, s < 512 ? "db-a" : "db-b");
        }
        long key = 9527;
        int slot = (int) (Math.abs(hash(key)) % slots);
        System.out.printf("  key %d -> slot %d -> 节点 %s%n", key, slot, slotMap.get(slot));
        System.out.println("  扩容 db-c：把 slot 512~682 划给它，只迁移这 171 个槽的数据");
        System.out.println("  双写期：写旧槽+新槽，对账完成后切读，停旧槽");
        System.out.println("  Redis Cluster(16384 slot) 与部分分库中间件都是此方案");
    }

    static void rangeByTime() {
        System.out.println("【range 按月分表：热点集中但扩容无痛】");
        String[][] months = {{"2026-01", "orders_202601（300 万行，仍在写）"},
                {"2026-02", "orders_202602（280 万行，仍在写）"},
                {"2025-12", "orders_202512（320 万行，只读可归档）"}};
        for (String[] m : months) {
            System.out.printf("  %s -> %s%n", m[0], m[1]);
        }
        System.out.println("  查询带时间条件 -> 直接定位单表；跨月查询 -> 联合多表（有限广播）");
        System.out.println("  新增一个月 = 加一张表，无需迁移任何存量数据");
    }

    public static void main(String[] args) {
        expansionCost();
        consistentHashDemo();
        slotPlan();
        rangeByTime();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用示例代码实测：10 万 key 从 8 分片扩到 16 分片与 9 分片各迁移多少，验证"翻倍只迁一半"。
 * 2. 实现一个 4096 槽的双节点槽位表，扩容到三节点（划出 1/3 槽位），写出迁移的槽位区间与对账步骤。
 * 3. 思考题：hash、range、slot 三种方案分别适合什么业务形态？给出你所在项目的主表选型与理由。
 * ============================================================
 */
