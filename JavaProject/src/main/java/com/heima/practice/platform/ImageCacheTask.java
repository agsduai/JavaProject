/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 37-01
 * 课名：镜像构建缓存评估器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】给定 Dockerfile 指令序列（FROM/COPY/RUN/EXPOSE 等）与两次构建间“哪些文件变了”的描述，输出第二次构建每一层是 CACHED 还是重算，并统计重算层数与预估耗时（每层固定开销 5s，COPY 层重算另加 8s）。
 *   - 【考核点】构建缓存判定规则（父层相同且指令未变才命中；COPY 按文件内容校验和）、指令顺序即缓存策略、层不可变。
 *   - 【易错点】一条失配其后全部重算；EXPOSE 等元数据层也依赖父层；把“文件没变”当成“层命中”而忽略父层失效。
 *   - 【提示】把每条指令建模为 (类型, 内容指纹, 依赖父层指纹)，自底向上传播“失效”标记。
 * ============================================================
 */

package com.heima.practice.platform;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ImageCacheTask {
    record Step(String cmd, String fingerprint, boolean changed) {
    }

    static void evaluate(List<Step> steps) {
        boolean broken = false;
        int recompute = 0, cached = 0;
        for (Step s : steps) {
            if (s.changed()) {
                broken = true;
            }
            if (broken) {
                recompute++;
                System.out.printf("  %-10s -> 重算（%s）%n", s.cmd(),
                        s.changed() ? "内容变更" : "父层失效");
            } else {
                cached++;
                System.out.printf("  %-10s -> CACHED%n", s.cmd());
            }
        }
        System.out.printf("  结果：命中 %d 层 / 重算 %d 层%n%n", cached, recompute);
    }

    public static void main(String[] args) {
        List<Step> steps = List.of(
                new Step("FROM base", "base-v1", false),
                new Step("COPY pom.xml", "pom-a1", false),
                new Step("RUN mvn dep", "deps-a1", false),
                new Step("COPY src", "src-b2", true),
                new Step("RUN mvn pkg", "pkg-c3", false));
        evaluate(steps);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整评估器：输入 Dockerfile 文本（自己定义紧凑格式）与变更文件清单，输出逐层判定与总耗时；用“改 pom.xml”与“只改 src”两组输入对比命中差异。
 * 2. 在真实 Docker（或虚拟机演示）验证评估器：同一项目两次构建，`docker build` 输出与你的推演逐层比对。
 * 3. 思考题：为什么“COPY . . 放第一行”是缓存杀手？结合失效传播机制写一段 300 字说明。
 * ============================================================
 */
