/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-08
 * 课名：分页与深度分页
 * ------------------------------------------------------------
 * 知识点：
 *   - **from+size 的默认限制是 10000**：不是性能调优经验值，而是硬限制（`index.max_result_window`）。因为每个分片都要返回 from+size 条文档到协调节点做归并排序——查第 9900 条时，3 个分片各取 9910 条，归并内存与网络成本随页深线性暴涨。
 *   - **三种翻页方案**：`from+size` 只适合浅翻页（搜索结果前几页）；`search_after`（用上一页最后一条的排序值做游标）适合无限滚动与深页，代价 O(页大小)且**要求排序唯一**；`scroll` 快照式遍历（历史数据导出），新代码用带 `search_after` 的 point-in-time（PIT）替代。
 *   - **search_after 的排序必须唯一**：排序键相同就无法定位"下一页从哪开始"，标准做法是 `_score` 后面拼 `_id`（或业务唯一键）作为兜底排序。**两个文档排序值完全相同 = 翻页丢数据/重复数据**。
 *   - **跳页与游标页的取舍**：电商搜索页用户要跳第 5 页（from+size 可以做，页深有限）；日志平台是无限下拉（search_after 天然匹配）；导出百万行走 PIT+search_after 批量拉取。
 *   - **深度分页的正确产品解法**：限制可跳页数（Google 只给你到第几十页）；改成筛选 + 排序引导（用户深翻页本身说明排序或筛选没起作用）。
 *   - **scroll/PIT 的资源语义**：scroll 会保持快照上下文占资源，忘记 clear 就泄漏；PIT 也有 keep_alive，同样要在结束显式释放。
 *   - 易错点：把 from+size 的 10000 限制当成"ES 慢"（是方案选错了）；search_after 忘加唯一排序键导致翻页数据丢失；scroll 上下文不用了不清理（节点堆内存被吃光）；导出任务用 from+size 循环翻页（页深一超限直接报错）。
 * ============================================================
 */

package com.heima.ch38;

import java.util.Comparator;
import java.util.List;

// ==================== 示例代码 ====================

public class DeepPagination {
    record Doc(long id, String title, double score) {
    }

    static final List<Doc> ALL = List.of(
            new Doc(1, "苹果手机壳", 12.5),
            new Doc(2, "小米手机壳", 11.8),
            new Doc(3, "华为手机壳", 11.2),
            new Doc(4, "透明手机壳", 10.9),
            new Doc(5, "硅胶手机壳", 10.5),
            new Doc(6, "金属手机壳", 10.1),
            new Doc(7, "皮纹手机壳", 9.8));

    static void fromSizeCost() {
        System.out.println("【from+size 的真实成本：归并窗口随页深放大】");
        int shards = 3;
        for (int page : new int[]{1, 100, 1000}) {
            int from = (page - 1) * 10;
            int perShard = from + 10;
            System.out.printf("  取第 %3d 页：每分片返回 %5d 条 -> 协调节点归并 %5d 条%n",
                    page, perShard, perShard * shards);
        }
        System.out.println("  max_result_window=10000：from+size 超过即 400 错误，是保护不是刁难");
        System.out.println();
    }

    static void searchAfterPaging() {
        System.out.println("【search_after：游标式翻页，成本与页深无关】");
        Comparator<Doc> cmp = Comparator.comparingDouble(Doc::score).reversed()
                .thenComparingLong(Doc::id);            // 分数 + id 双键保证唯一
        List<Doc> sorted = ALL.stream().sorted(cmp).toList();
        int pageSize = 3;
        int start = 0;
        for (int page = 1; start < sorted.size(); page++) {
            List<Doc> window = sorted.subList(start, Math.min(start + pageSize, sorted.size()));
            Doc last = window.get(window.size() - 1);
            System.out.printf("  第 %d 页: %s  after=%d%n", page,
                    window.stream().map(Doc::id).toList(), last.id());
            start += pageSize;
        }
        System.out.println("  每次只取 pageSize 条，翻到第 10000 页成本不变");
        System.out.println("  前提：排序键必须唯一（score+id），否则两文档同分无法定位游标");
        System.out.println();
    }

    static void pitIdea() {
        System.out.println("【PIT/scroll：全量导出的正确姿势】");
        String[] steps = {
                "1) POST index/_pit?keep_alive=5m  -> 打开快照上下文，得到 pit_id",
                "2) 循环：search 带 pit_id + search_after，每批 1000 条",
                "3) 期间新写入的文档不出现在本次遍历（一致性快照）",
                "4) 结束后 DELETE _pit 释放资源（忘记释放 = 堆内存泄漏）"};
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  适用：数仓导出、重建索引；不适用：用户翻页（会被快照语义困惑）");
        System.out.println();
    }

    static void decisionTable() {
        System.out.println("【选型决策表】");
        String[][] rows = {
                {"搜索结果前几页 + 跳页", "from+size", "页深 ≤ 数百，产品上限制可跳页数"},
                {"无限下拉 / 信息流", "search_after", "排序键唯一，成本恒定"},
                {"全量导出 / reindex", "PIT + search_after", "快照一致性，批量拉取"},
                {"老代码维护", "scroll", "记得 clear，新项目不再选用"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-22s %-20s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) {
        fromSizeCost();
        searchAfterPaging();
        pitIdea();
        decisionTable();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实测：建 10 万文档索引，用 from+size 翻到 9000 与用 search_after 翻同样深度，对比耗时，解释成本曲线差异。
 * 2. 复现"search_after 排序键不唯一导致翻页丢文档"：去掉 id 兜底排序，构造同分文档，观察翻页结果，再修复验证。
 * 3. 写一个 PIT 导出工具：带进度与速率控制，结束后确保释放 PIT；故意不释放并用 _cat/nodes 观察 JVM 堆变化。
 * 4. 思考题：为什么产品层面"允许跳到任意页"通常是伪需求？从用户行为与排序质量两个角度分析。
 * ============================================================
 */
