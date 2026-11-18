/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-03
 * 课名：深分页优化
 * ------------------------------------------------------------
 * 知识点：
 *   - **`LIMIT 1000000, 20` 的真实成本**：不是"跳过一百万行很快"，而是**扫完前 1000020 行、扔掉前 100 万行**。每多翻一页成本线性增长，且页越深锁与 buffer pool 压力越大。页深上万时，一次查询就能把数据库拖挂。
 *   - **游标分页（keyset pagination）是根本解**：记住上一页最后一条的排序键（如 `id=100023`），下一页查询 `WHERE id > 100023 ORDER BY id LIMIT 20`。成本恒定 O(页大小)，配合主键索引完美。代价：**只能顺序翻页，不能跳页**——这与第 38 章 ES 的 search_after 是同一个思想。
 *   - **跳页需求用延迟关联**：`SELECT t.* FROM t JOIN (SELECT id FROM t ORDER BY id LIMIT 1000000, 20) tmp ON t.id=tmp.id`。内层只扫覆盖索引（窄），回表只剩 20 次。再配合"业务上限制可跳页数"基本解决。
 *   - **排序键设计决定游标可用性**：按 `create_time DESC, id DESC` 翻页时，条件是 `(create_time, id) < (last_time, last_id)` 的行序元组比较，MySQL 8.0 支持行构造器写法 `WHERE (create_time, id) < (?, ?)`；老版本用等价展开写法。**排序键必须带唯一列兜底**，否则同时刻数据翻页会漏。
 *   - **总数（count(*)）是另一个坑**：深表 `SELECT COUNT(*)` 全索引扫描，页面每次请求都算一次总数是灾难。方案：近似总数（估算值 + "约"字）、缓存计数、或产品上去掉总数只留"下一页"。
 *   - **与 ES 深分页的统一认知**：任何存储的深分页本质都一样——**offset 方案的成本随深度线性涨，游标方案成本恒定**。选型取决于产品需要跳页还是流式浏览。
 *   - 易错点：前端强制要"总条数+跳页"就默默用 offset 硬扛（深页必炸）；游标分页的排序键不含唯一列导致漏数据；缓存 count 后写路径忘了失效（数字长期不准）；用 `LIMIT` 做数据导出循环（offset 越翻越慢，应按 id 游标批量拉）。
 * ============================================================
 */

package com.heima.ch39;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ==================== 示例代码 ====================

public class DeepPaginationFix {
    record Order(long id, String status, long createTime) {
    }

    /** 模拟一张有序表：id 递增 */
    static List<Order> table(int n) {
        List<Order> rows = new ArrayList<>();
        for (long i = 1; i <= n; i++) {
            rows.add(new Order(i, i % 3 == 0 ? "PAID" : "CREATED", 1700000000L + i));
        }
        return rows;
    }

    static void offsetCost() {
        System.out.println("【offset 分页的成本曲线】");
        List<Order> rows = table(2_000_000);
        int[] offsets = {20, 20_000, 200_000, 1_000_000};
        for (int off : offsets) {
            long scanned = Math.min(off + 20, rows.size());
            System.out.printf("  LIMIT %7d, 20 -> 必须扫过 %8d 行（命中率 %.4f%%）%n",
                    off, scanned, 20.0 / scanned * 100);
        }
        System.out.println("  扫描行数与 offset 同阶增长；offset=百万时一次查询等于半次全表扫描");
        System.out.println();
    }

    static void keysetPaging() {
        System.out.println("【游标分页：WHERE id > lastId LIMIT 20，成本恒定】");
        List<Order> rows = table(2_000_000);
        long cursor = 0;
        int page = 0;
        while (page < 3 && cursor < rows.size()) {
            int start = (int) cursor;
            List<Order> window = rows.subList(start, Math.min(start + 20, rows.size()));
            System.out.printf("  第 %d 页: id %d ~ %d（只扫 20 行）%n",
                    page + 1, window.get(0).id(), window.get(window.size() - 1).id());
            cursor = start + 20;
            page++;
        }
        System.out.println("  SQL：SELECT * FROM orders WHERE id > ? ORDER BY id LIMIT 20");
        System.out.println("  任何页深都只扫描 20 行，这就是游标的全部意义");
        System.out.println();
    }

    static void compositeKeyset() {
        System.out.println("【复合排序键的游标：按 create_time DESC 翻页】");
        List<Order> sorted = table(100).stream()
                .sorted(Comparator.comparingLong(Order::createTime).reversed()
                        .thenComparingLong(Order::id))
                .limit(6).toList();
        Order last = sorted.get(2);
        System.out.printf("  上一页最后一条：(create_time=%d, id=%d)%n", last.createTime(), last.id());
        List<Order> next = sorted.stream()
                .sorted(Comparator.comparingLong(Order::createTime).reversed()
                        .thenComparingLong(Order::id))
                .skip(3).limit(3).toList();
        System.out.println("  WHERE (create_time,id) < (?, ?) ORDER BY create_time DESC, id DESC LIMIT 3");
        System.out.println("  下一页: " + next.stream().map(o -> o.id()).toList());
        System.out.println("  唯一键兜底：同一毫秒的多条订单不会漏、不会重");
        System.out.println();
    }

    static void delayedJoin() {
        System.out.println("【必须跳页时：延迟关联只回表 20 次】");
        long total = 2_000_000, offset = 1_000_000, pageSize = 20;
        long narrowScan = offset + pageSize;
        System.out.printf("  原写法：扫描 %d 行 × 全字段宽行 -> 回表 %d 次%n", narrowScan, narrowScan);
        System.out.printf("  延迟关联：内层覆盖索引扫 %d 行（窄），回表 %d 次%n", narrowScan, pageSize);
        System.out.println("  SQL：SELECT o.* FROM orders o JOIN");
        System.out.println("       (SELECT id FROM orders ORDER BY id LIMIT 1000000, 20) t ON o.id=t.id");
        System.out.println("  配套产品约束：可跳页范围限制在最近 N 页（跳到 50 万条之外本就是伪需求）");
    }

    public static void main(String[] args) {
        offsetCost();
        keysetPaging();
        compositeKeyset();
        delayedJoin();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在 100 万行测试表上实测 `LIMIT 0,20` / `LIMIT 999980,20` / 游标分页三种写法的耗时，把数字记下来作为团队的深分页证据。
 * 2. 为订单列表实现复合游标分页（create_time + id），处理"同一时间多条订单"的漏数据问题，并用并发插入验证。
 * 3. 设计 count 优化方案：首屏给"约 128 万+"近似值，翻页后隐藏总数。写出近似值的计算方式（information_schema 估算或采样）。
 * 4. 思考题：为什么导出任务用 `LIMIT offset 循环` 会越跑越慢？用本章公式推导第 100 万次循环的扫描量。
 * ============================================================
 */
