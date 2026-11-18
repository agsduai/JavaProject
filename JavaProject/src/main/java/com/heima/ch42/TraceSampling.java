/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-08
 * 课名：采样与链路成本
 * ------------------------------------------------------------
 * 知识点：
 *   - **全量追踪的成本不可承受**：每个请求几十个 span、每天几十亿请求——全量存储的硬件成本超过业务本身。**采样是链路追踪的必备设计**，采样策略直接决定"你想抓的问题能不能抓到"。
 *   - **头部采样（head sampling）**：入口处按概率决定整条链路是否记录（如 1%），实现简单、成本确定；缺点是**低频异常靠撞运气**——每秒 5 次的错误在 1% 采样下平均 20 秒才有一条。
 *   - **尾部采样（tail sampling）**：请求结束后再决定是否保留，可以**按结果采样**——所有 error 100% 保留、慢请求（P99 以上）100% 保留、正常请求采样 1%。这是问题驱动的理想方案，代价是采集器要**缓存整条链路等它结束**（内存与延迟成本）。
 *   - **两种策略组合是生产标准**：头部 10% 打底 + 尾部"error/慢请求全保"。**漏斗要和 SLO 对齐**：你承诺 P99<300ms，那 >300ms 的请求就该全保留。
 *   - **采样的代价意识**：span 的存储量 = QPS × span 数 × 采样率 × 平均大小。降本的杠杆按大到小：降采样率 > 减 span 数（合并客户端 span）> 压缩。**保留期一般 7~14 天**（与日志同步）。
 *   - **采样影响统计正确性**：1% 采样的 P99 统计不可信（样本太少）。原则：**指标从全量数据算（Prometheus 抓取不分采样），采样只影响"链路明细"的保留**。两者混用是常见误用。
 *   - 易错点：全量追踪直接上生产（成本爆炸）；只配了头部采样导致排查"偶发 500"无链路可看；尾部采样缓存队列被打爆（高流量时 OOM）；把采样率当 SLO 统计口径（统计失真）。
 * ============================================================
 */

package com.heima.ch42;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// ==================== 示例代码 ====================

public class TraceSampling {
    record Request(String id, int costMs, boolean error, boolean keep) {
    }

    static void headVsTail() {
        System.out.println("【头部 10% vs 尾部按结果：同样 20 个请求的保留差异】");
        List<Request> reqs = new ArrayList<>();
        Random r = new Random(42);
        for (int i = 0; i < 20; i++) {
            int cost = i == 7 ? 850 : 80 + r.nextInt(150);
            boolean err = i == 11;
            reqs.add(new Request("r" + i, cost, err, false));
        }
        int headKept = 0;
        for (Request req : reqs) {
            boolean keep = r.nextInt(100) < 10;
            if (keep) {
                headKept++;
            }
        }
        int tailKept = 0;
        for (Request req : reqs) {
            boolean keep = req.error() || req.costMs() > 300;
            if (keep) {
                tailKept++;
            }
            reqs.set(reqs.indexOf(req), new Request(req.id(), req.costMs(), req.error(), keep));
        }
        System.out.printf("  头部采样 10%%：保留 %d 条（随机，错误 r11 大概率丢失）%n", headKept);
        System.out.println("  尾部采样（error 全保 + >300ms 全保）：");
        for (Request req : reqs) {
            if (req.keep()) {
                System.out.printf("    保留 %s cost=%dms error=%s%n",
                        req.id(), req.costMs(), req.error());
            }
        }
        System.out.println("  你要抓的问题（偶发错误、慢请求）100% 在保留集合里");
        System.out.println();
    }

    static void costModel() {
        System.out.println("【成本模型：QPS × span数 × 采样率 × 大小】");
        long qps = 2000, spansPerReq = 30;
        double sampleRate = 0.01;
        long dailySpans = Math.round(qps * 86400 * spansPerReq * sampleRate);
        long dailyGb = dailySpans * 512 / 1024 / 1024 / 1024;
        System.out.printf("  QPS=%d, span/请求=%d, 采样 1%%%n", qps, spansPerReq);
        System.out.printf("  每日 span 数 ≈ %d 亿条级 -> 存储约 %d GB/天（按 512B/span）%n",
                dailySpans / 100_000_000, dailyGb);
        System.out.println("  降本杠杆：采样 1%->0.5% 减半；合并客户端 span 减 span 数；保留期 14d->7d 减半");
        System.out.println();
    }

    static void statsIntegrity() {
        System.out.println("【采样与统计的正确边界】");
        List<String> rules = List.of(
                "指标（QPS/P99/错误率）：从全量请求算，不经过采样",
                "链路明细：按策略采样，用于个案排查",
                "错误率告警如果基于采样数据 -> 统计失真，绝对禁止",
                "尾部采样的缓存队列要有上限与降级（超限退回头部采样）");
        for (String s : rules) {
            System.out.println("  - " + s);
        }
    }

    public static void main(String[] args) {
        headVsTail();
        costModel();
        statsIntegrity();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现一个尾部采样器：规则（error 100%、P99 慢请求 100%、其余 1%）+ 缓存上限（超限退头部采样），用 10 万模拟请求统计各类样本的保留率。
 * 2. 用本章成本模型算你公司的追踪预算：代入真实 QPS 与 span 数，给出三个降本杠杆的量化效果。
 * 3. 思考题：为什么"错误率告警基于采样数据"是绝对禁止的？从统计一致性与告警可信度推导指标与链路的数据边界。
 * ============================================================
 */
