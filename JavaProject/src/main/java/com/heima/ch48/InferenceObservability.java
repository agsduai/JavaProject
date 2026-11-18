/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：48-10
 * 课名：推理可观测
 * ------------------------------------------------------------
 * 知识点：
 *   - **推理服务的 SLI 多于普通服务**：TTFT、TPOT、队列深度、批利用率、block 水位、prefill/decode 时间分解——**42 章四信号框架的推理特化**。核心原则不变：告警挂在用户体验（TTFT/错误率），排障看内部（水位/分解）。
 *   - **延迟分解是排障的第一刀**：总延迟 = 排队 + prefill + decode + 网络传输——**每段分别打点**：排队涨 = 容量不足；prefill 涨 = 长输入/干扰；decode 涨 = 批过大/带宽瓶颈。**不做分解就只能猜**。
 *   - **与输入特征联动**：TTFT 与输入长度强相关、总延迟与输出长度强相关——**按输入/输出长度分桶看指标**（42-04 维度设计的推理版），否则长尾被平均掩盖。
 *   - **质量观测与性能观测并行**：加速配置变更后**质量抽检率**与性能指标同屏（48-05 双门）；点踩率与 TTFT 异常的时间相关性排查。
 *   - **42 章方法论的全套复用**：错误预算（TTFT SLO）、告警降噪（聚合同一副本故障）、链路追踪（网关->引擎的 trace）、容量水位告警（48-06）——**本书观测体系在推理服务的一比一移植**，只是指标语义换成了生成过程。
 *   - 易错点：只监控总延迟不分解（无法定位瓶颈段）；不分桶看分位数（长请求污染 P99）；质量与性能割裂观测（加速变更后只看性能）；引擎内部指标不暴露（block/批利用率黑盒）。
 * ============================================================
 */

package com.heima.ch48;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class InferenceObservability {
    record Segment(String name, String signal, String highMeans) {
    }

    static void latencyDecomposition() {
        System.out.println("【延迟分解：总延迟 = 排队+prefill+decode+网络】");
        List<Segment> segs = List.of(
                new Segment("排队", "网关等待时间", "容量不足（48-06 扩容）"),
                new Segment("prefill", "输入编码时间", "输入过长/长 prefill 干扰（48-03）"),
                new Segment("decode", "TPOT × 输出长度", "批过大/带宽瓶颈（48-03/05）"),
                new Segment("网络", "传输与网关开销", "SSE 缓冲/中间设备（40-04）"));
        for (Segment s : segs) {
            System.out.printf("  %-6s 信号=%-18s 偏高=%s%n", s.name(), s.signal(), s.highMeans());
        }
        List<String> sample = List.of(
                "异常请求样本：排队 40ms + prefill 300ms + decode 2400ms + 网络 60ms",
                "结论：decode 占 86% -> 长输出 + TPOT 偏高 -> 查批配置或加速项");
        for (String s : sample) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void bucketByLength() {
        System.out.println("【按输入/输出长度分桶：消除长尾污染】");
        String[][] buckets = {
                {"短入短出（<500/300）", "TTFT 380ms / 总 12s", "交互对话主场景"},
                {"长入短出（8K/300）", "TTFT 1.9s / 总 13s", "RAG 大文档场景，TTFT 单独 SLO"},
                {"短入长出（500/2K）", "TTFT 350ms / 总 62s", "文案场景，总时长单独承诺"},
        };
        for (String[] b : buckets) {
            System.out.printf("  %-22s %s%n", b[0], b[1]);
        }
        System.out.println("  统一 SLO 会把三个场景搅在一起 —— 分桶后各自达标才叫达标");
        System.out.println();
    }

    static void qualityPerfTogether() {
        System.out.println("【质量与性能同屏：加速变更的看板】");
        record Change(String config, String tps, String tpot, String quality, String verdict) {
        }
        List<Change> changes = List.of(
                new Change("基线 FP16", "900 tok/s", "30ms", "94.2%", "基准"),
                new Change("+FP8 量化", "1650 tok/s", "31ms", "93.8%", "PASS：吞吐+83% 质量持平"),
                new Change("+投机解码", "2100 tok/s", "28ms", "88.5%", "FAIL：质量门未过 -> 关闭"));
        for (Change c : changes) {
            System.out.printf("  %-12s 吞吐=%-10s TPOT=%-6s 质量=%-7s %s%n",
                    c.config(), c.tps(), c.tpot(), c.quality(), c.verdict());
        }
        System.out.println("  48-05 双门的仪表化：变更前后同屏对比，一票否决");
        System.out.println();
    }

    static void observabilityChecklist() {
        System.out.println("【推理服务观测清单（42 章清单的特化）】");
        List<String> items = List.of(
                "TTFT/TPOT 分桶分位（输入长度 × 输出长度）",
                "排队深度与排队时长（网关层）",
                "引擎内部：批利用率、block 水位、抢占次数",
                "取消率与幽灵计算检测（48-07）",
                "质量抽检率 + 点踩率（与性能同屏）",
                "成本指标：tokens_in/out、每千次会话成本（48-09）");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  告警挂用户侧（TTFT/错误率），排障看内部（水位/分解）—— 42-05 原则");
    }

    public static void main(String[] args) {
        latencyDecomposition();
        bucketByLength();
        qualityPerfTogether();
        observabilityChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给你的推理服务补齐延迟分解打点（排队/prefill/decode），用一次慢请求样本做归因。
 * 2. 建立分桶看板（输入×输出长度），对比统一 SLO 与分桶 SLO 的达标判定差异。
 * 3. 思考题：为什么“质量抽检与性能同屏”是加速变更看板的必备设计？从 48-05 双门与 44-10 评测门禁的闭环关系分析。
 * ============================================================
 */
