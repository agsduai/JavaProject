/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-03
 * 课名：可靠投递：发送确认、持久化与重试
 * ------------------------------------------------------------
 * 知识点：
 *   - 消息可能丢在三个地方：**生产者到 Broker**（网络抖动、Broker 未落盘）、**Broker 内部**（内存写成功但宕机）、**Broker 到消费者**（消费失败但已 ack）。三处要分别兜底。
 *   - 生产端可靠投递三板斧：**发送确认**（RabbitMQ publisher confirm、Kafka `acks=all`）、**失败重试**（有限次 + 退避）、**本地消息表兜底**（28-09）。
 *   - 持久化：交换机、队列、消息三者都要持久化，只持久化消息而不持久化队列，重启后队列没了消息一样丢。
 *   - `acks` 语义：`acks=0` 发出去就算成功（最快最不安全）；`acks=1` leader 落盘即返回（leader 挂了可能丢）；`acks=all` 等所有 ISR 副本确认（最安全最慢）。
 *   - 重试要有上限和退避，否则一次 Broker 抖动会引发无限重投，把刚恢复的集群再打挂。
 *   - 易错：只在 catch 里重试一次就放弃；重试时没有记录原始消息 ID，导致下游无法幂等（见 28-05）。
 * ============================================================
 */

package com.heima.ch28;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ReliableSend {
    /** 模拟 Broker：内存队列 + 磁盘日志 */
    static class Broker {
        final List<String> memory = new ArrayList<>();
        final List<String> disk = new ArrayList<>();
        boolean persistent = true;

        /** 前两次投递模拟网络超时，第三次成功 */
        boolean publish(String msg, int attempt) {
            if (attempt <= 2) {
                System.out.println("    第 " + attempt + " 次投递失败：连接超时，消息未落盘");
                return false;
            }
            memory.add(msg);
            if (persistent) {
                disk.add(msg);
            }
            System.out.println("    第 " + attempt + " 次投递成功，收到 confirm，落盘=" + persistent);
            return true;
        }
    }

    public static void main(String[] args) {
        Broker broker = new Broker();
        String msg = "ORDER-1001";
        boolean ok = false;
        for (int attempt = 1; attempt <= 4 && !ok; attempt++) {
            ok = broker.publish(msg, attempt);
            if (!ok) {
                System.out.println("    -> 退避 200ms 后重试");
            }
        }
        System.out.println("最终投递=" + ok + "，内存=" + broker.memory + "，磁盘=" + broker.disk);

        System.out.println();
        System.out.println("=== 关掉持久化：confirm 依然成功，但重启后消息没了 ===");
        Broker noDisk = new Broker();
        noDisk.persistent = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (noDisk.publish(msg, attempt)) {
                break;
            }
        }
        System.out.println("  broker 重启后可从磁盘恢复的消息=" + noDisk.disk + "（空 => 消息丢了）");
        System.out.println("  结论：confirm 只证明 Broker 收到了，能不能活过重启取决于持久化");

        System.out.println();
        System.out.println("=== acks 三档对比 ===");
        System.out.println("  acks=0   吞吐最高，leader 没收到也可能算成功，宕机必丢");
        System.out.println("  acks=1   等 leader 落盘，leader 挂掉且未同步时丢");
        System.out.println("  acks=all 等 ISR 全部确认，最安全，延迟最高");
        System.out.println("  订单、支付类消息用 all；日志、埋点可以放宽到 1");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给重试加上指数退避（200ms、400ms、800ms），并统计总等待时间。
 * 2. 说明"消息持久化 + 队列非持久化"在 Broker 重启后会发生什么。
 * 3. 为什么 `acks=all` 也不是绝对不丢？从 ISR 收缩的角度解释。
 * ============================================================
 */
