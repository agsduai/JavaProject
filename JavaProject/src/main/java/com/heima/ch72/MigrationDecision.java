/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-10
 * 课名：迁移评估与决策
 * ------------------------------------------------------------
 * 知识点：
 *   - **迁移评估的四个维度**：**收益**（启动/内存/成本）、**成本**（构建时间/配置工作量/团队学习）、**风险**（动态特性不兼容/行为差异）、**回退能力**（能否双版本并存）——**四维打分，而不是"技术上很酷"**（52-02 ADR 的方法）。
 *   - **评估的量化方法**：**启动与内存的收益可量化**（72-01/07 的模型）；**配置成本**（反射/代理/资源的配置条数 × 每条工时）；**风险**（动态特性清单的占比）。
 *   - **迁移路线**：**先小服务试点**（低风险验证）-> **再核心服务**（有试点经验）-> **保留 JVM 版本并行**（灰度 + 回滚）——**与 49-10 演进纪律一致**。
 *   - **不迁移的信号**：动态特性极多（配置成本爆炸）、构建时间不可接受、峰值性能要求高、团队无 native 经验——**"不迁移"也是正确决策**。
 *   - 与 52 章的关系：这是一个典型的**架构决策记录（ADR）**：背景/选项/决策/负面后果/回顾条件——**把本章的评估结论写成 ADR**。
 *   - 易错点：没量化收益就迁移（为技术而技术）；低估配置成本（反射配置是长尾工作）；无并行运行期（出问题无法回退）；迁移后不做压测（峰值劣化未发现）。
 * ============================================================
 */

package com.heima.ch72;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MigrationDecision {
    record Candidate(String service, double startupSec, long memoryMb,
                     int dynamicFeatures, int configItems, double peakQpsNeed) {
    }

    /** 四维打分（0~10 分，越高越适合迁移） */
    static Map<String, Integer> score(Candidate c) {
        Map<String, Integer> s = new LinkedHashMap<>();
        // 收益：启动越慢、内存越大，收益越高
        int benefit = (int) Math.min(10, c.startupSec() / 3 + c.memoryMb() / 50.0);
        // 成本：动态特性与配置项越多，成本越高 -> 得分低
        int cost = (int) Math.max(0, 10 - c.dynamicFeatures() * 2 - c.configItems() / 2);
        // 风险：动态特性多则风险高 -> 得分低
        int risk = (int) Math.max(0, 10 - c.dynamicFeatures() * 2.5);
        // 峰值要求高 -> 不适合（JIT 更强）
        int peak = c.peakQpsNeed() > 5000 ? 3 : 9;
        s.put("收益", Math.min(10, benefit));
        s.put("成本可控", Math.min(10, cost));
        s.put("风险可控", Math.min(10, risk));
        s.put("峰值适配", peak);
        return s;
    }

    static String verdict(Map<String, Integer> s) {
        int total = s.values().stream().mapToInt(Integer::intValue).sum();
        if (total >= 28) {
            return "推荐迁移（试点验证）";
        }
        if (total >= 18) {
            return "谨慎评估（先解决动态特性）";
        }
        return "不迁移（JVM 更合适）";
    }

    public static void main(String[] args) {
        List<Candidate> candidates = List.of(
                new Candidate("CLI 图片处理工具", 0.5, 80, 1, 2, 10),
                new Candidate("后台管理 API", 25, 600, 6, 20, 300),
                new Candidate("订单核心服务", 18, 800, 12, 45, 8000),
                new Candidate("定时任务 Worker", 12, 400, 3, 8, 200));
        System.out.println("【迁移评估】");
        for (Candidate c : candidates) {
            Map<String, Integer> s = score(c);
            System.out.printf("  %-18s %s%n", c.service(), s);
            System.out.printf("      启动=%.1fs 内存=%dMB 动态特性=%d 配置项=%d 峰值=%.0f%n",
                    c.startupSec(), c.memoryMb(), c.dynamicFeatures(), c.configItems(),
                    c.peakQpsNeed());
            System.out.printf("      结论：%s%n", verdict(s));
        }

        System.out.println();
        System.out.println("【迁移路线】");
        Map<String, String> roadmap = new LinkedHashMap<>();
        roadmap.put("第 1 步", "选低风险服务试点（CLI / Worker）");
        roadmap.put("第 2 步", "沉淀配置模板与排障手册（72-03/06）");
        roadmap.put("第 3 步", "评估核心服务（订单）的配置成本与峰值影响");
        roadmap.put("第 4 步", "双版本灰度（JVM 兜底 41-07）");
        roadmap.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));

        System.out.println();
        System.out.println("【决策记录（ADR 要素）】背景/选项/决策/负面后果/回顾条件（52-02）");
        System.out.println("  不迁移的信号：动态特性极多、构建时间不可接受、峰值要求高");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用四维打分评估你团队的 4 个服务，输出"迁移优先级"排序与理由。
 * 2. 为一个服务写 ADR：背景（启动慢）、选项（JVM 优化/native/保留）、决策、负面后果、回顾条件。
 * 3. 思考题：为什么"不迁移"也是正确决策？从"配置成本长尾"与"峰值性能取舍"分析。
 * ============================================================
 */
