/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 21-02
 * 课名：批量写入与事务
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现批量插入对比：逐条插入 1000 行 vs addBatch+executeBatch（分批 500），统计耗时；任一批失败则整体回滚（事务边界），输出"部分插入被回滚"的证据；实现 savepoint（保留前 500 行，只回滚后 500）。
 *   - 【考核点】批处理 API、事务边界与回滚、Savepoint 局部回滚、批量与事务的组合。
 *   - 【易错点】逐条 executeUpdate（性能差一个数量级）；回滚未关自动提交；Savepoint 释放顺序混乱。
 *   - 【提示】模拟层用"插入计数器 + 故障注入点"演示回滚——计数器回滚即证据。
 * ============================================================
 */

package com.heima.practice.framework;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class BatchTxTask {
    static List<String> committed = new ArrayList<>();

    static void tx(List<String> writes, String failAt) {
        List<String> pending = new ArrayList<>();
        for (String w : writes) {
            if (w.equals(failAt)) {
                System.out.printf("  写 %s 失败 -> 回滚 %d 条（已提交不受影响：%s）%n",
                        w, pending.size(), committed);
                return;
            }
            pending.add(w);
        }
        committed.addAll(pending);
    }

    public static void main(String[] args) {
        tx(List.of("w1", "w2", "w3"), null);
        tx(List.of("w4", "BOOM", "w5"), "BOOM");
        System.out.println("  最终已提交：" + committed);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现批量 vs 逐条的性能对比（1000 行，输出耗时），并实现事务回滚与 Savepoint 局部回滚。
 * 2. 注入"第 600 行失败"，验证前 500 行已批提交（分批边界）与后 500 行回滚的账目清晰。
 * 3. 思考题：为什么批量插入快一个数量级？从网络往返与语句解析复用两个成本项分析。
 * 
 * 
 * ## 第22章 Maven 与项目构建
 * ============================================================
 */
