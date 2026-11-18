/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-06
 * 课名：etcd 与线性一致读
 * ------------------------------------------------------------
 * 知识点：
 *   - **etcd 是 K8s 的元数据底座，也是 Raft 的参考实现**：与 ZK 定位相似（协调服务），但协议更现代（Raft）、支持 MVCC 多版本与事务（mini-transactions：if-then-else）、**gRPC 端到端**。K8s 的一切对象都存 etcd——它挂了 K8s 只读不写。
 *   - **MVCC 是 etcd 的重要设计**：每次写产生全局递增 revision，键值对保留历史版本——**watch 按 revision 流式消费不漏事件**（对比 ZK watch 的一次性触发），**compact 修剪旧版本**（不压缩历史会无限膨胀）。
 *   - **串行读 vs 线性一致读**：串行读（默认）只读本地状态——**便宜但可能读到旧主数据**（分区中旧 leader 的内存状态）；线性一致读走 ReadIndex：leader 向多数派确认自己权威再读（45-04）。**K8s 用 quorum 读保关键路径，监控用串行读省代价**——分级一致性的实践样本（45-02）。
 *   - **lease 是 etcd 的活性原语**：客户端申请带 TTL 的 lease，key attach 到 lease，续约失败过期则 key 自动删除——**与 ZK 临时节点同构的选主/存活性方案**。
 *   - **事务（txn）让"检查并写入"原子化**：`if value(k)==A then put(k,B) else put(k,C)`——**分布式锁、防并发写、leader 切换保护的实现基座**。没有 txn 时这些要靠外部锁。
 *   - **运维视角**：etcd 对磁盘延迟极敏感（fsync 是写路径关键段，磁盘 >10ms 延迟会导致心跳超时换届）——**专用 SSD + 隔离部署**是 K8s 生产铁律；compact 与 defrag 要排期。
 *   - 易错点：把 etcd 当通用数据库存业务数据（K8s 元数据专用定位）；不配 compact（revision 无限增长拖垮查询）；串行读用于选主结果判断（读到旧主）；lease TTL 太短（抖动即 key 删除，业务雪崩）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

public class EtcdLinearRead {
    /** MVCC：revision -> (key -> value) */
    static TreeMap<Integer, Map<String, String>> history = new TreeMap<>();
    static int rev = 0;

    static void put(String key, String value) {
        rev++;
        history.computeIfAbsent(rev, k -> new java.util.LinkedHashMap<>()).put(key, value);
        System.out.printf("  put %s=%s -> revision=%d%n", key, value, rev);
    }

    static void mvccWatch() {
        System.out.println("【MVCC：watch 按 revision 流式消费不漏事件】");
        put("config/timeout", "30");
        put("config/timeout", "60");
        put("config/retries", "3");
        System.out.println("  watch 从 revision=1 开始回放：");
        for (Map.Entry<Integer, Map<String, String>> e : history.entrySet()) {
            System.out.printf("    rev=%d %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  断线重连：从上次收到的 revision 续看 —— 不像 ZK watch 会丢中间变化");
        System.out.println("  compact(revision=2)：旧版本回收，watch 起点 <3 的客户端报错需全量同步");
        System.out.println();
    }

    static void txnAtomic() {
        System.out.println("【txn：检查并写入的原子化】");
        String stored = history.get(rev).get("config/timeout");
        boolean match = "60".equals(stored);
        System.out.printf("  txn: if config/timeout==60 then put(90) else put(read_current)%n");
        System.out.printf("  当前值=%s -> %s%n", stored,
                match ? "条件成立 -> 写入 90" : "条件不成立 -> 走 else 分支");
        if (match) {
            put("config/timeout", "90");
        }
        System.out.println("  用途：leader 切换保护、防并发写、锁的实现基座");
        System.out.println();
    }

    static void readModes() {
        System.out.println("【两种读模式：K8s 的分级实践】");
        String[][] rows = {
                {"串行读(默认)", "本地状态，0 RTT", "可能读到旧主数据", "监控/大盘"},
                {"线性一致读", "ReadIndex 过半确认，+1 RTT", "权威值", "选主判断/关键写前置"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-26s %-16s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println("  铁律：判断“谁是 leader”必须线性一致读，否则可能基于旧主做决策");
        System.out.println();
    }

    static void opsNotes() {
        System.out.println("【磁盘延迟是 etcd 的命门】");
        List<String> notes = List.of(
                "写路径关键段 = fsync：磁盘 >10ms -> 心跳超时 -> 反复换届",
                "生产铁律：专用 SSD/NVMe，与业务负载隔离部署",
                "compact 排期（保留 N 万 revision）；defrag 回收碎片空间",
                "监控：commit 耗时 P99、慢 fsync 告警（42 章方法论）");
        for (String n : notes) {
            System.out.println("  - " + n);
        }
    }

    public static void main(String[] args) {
        mvccWatch();
        txnAtomic();
        readModes();
        opsNotes();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现带 revision 的 MVCC 键值存储模拟器：支持按 revision 回放 watch 与 compact，验证断线重连不漏事件。
 * 2. 用 txn 实现"防并发写"：两个客户端同时尝试 `if value==old then put(new)`，验证只有一个成功。
 * 3. 思考题：为什么 etcd 对磁盘延迟比对网络延迟更敏感？从 Raft 落盘与心跳超时的关系推导。
 * ============================================================
 */
