/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 50-01
 * 课名：虚拟线程扇出聚合
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现聚合网关虚拟线程版：4 个下游（不同耗时与可失败标记）并行调用，每下游独立 Semaphore 限流，总超时预算分级（关键 400ms/非关键 200ms），非关键超时返回占位数据（整体不失败），输出合并结果、降级清单与总耗时；与串行实现对比耗时。
 *   - 【考核点】虚拟线程扇出、每下游限流、分级超时预算、降级合并语义（44-08/48-07 思想落地）。
 *   - 【易错点】Future.get 无超时（慢下游拖死整批）；取消不传播（幽灵任务）；关键与非关键混用同一预算。
 *   - 【提示】先并行提交全部任务，再按各自预算 get；超时的非关键任务 cancel(true) 后合并占位。
 * ============================================================
 */

package com.heima.practice.graduation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// ==================== 示例代码 ====================

public class VtFanoutTask {
    public static void main(String[] args) throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> fast = pool.submit(() -> "fast-ok");
            Future<String> slow = pool.submit(() -> {
                Thread.sleep(800);
                return "slow-ok";
            });
            String a = fast.get(100, TimeUnit.MILLISECONDS);
            String b;
            try {
                b = slow.get(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                b = "placeholder";
                slow.cancel(true);
            }
            System.out.printf("  合并：%s / %s%n", a, b);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整聚合网关（4 下游 + 限流 + 分级预算 + 降级合并），压测对比串行/虚拟线程两版的耗时与成功率。
 * 2. 加入“取消传播”：模拟用户断开（取消总任务），验证全部下游任务被取消且无幽灵计算。
 * 3. 思考题：为什么聚合网关是并发模型试金石？从扇出数、延迟方差、失败语义三个维度对比虚拟线程与响应式。
 * ============================================================
 */
