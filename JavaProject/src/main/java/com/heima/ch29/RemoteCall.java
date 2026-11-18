/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-04
 * 课名：远程调用：RPC 与 HTTP
 * ------------------------------------------------------------
 * 知识点：
 *   - **RPC（Dubbo/gRPC）**：基于接口的代理调用，像调本地方法；序列化用 Protobuf/Hessian，二进制、体积小、性能高；强契约（接口 + DTO）。
 *   - **HTTP（REST/OpenFeign）**：文本协议（JSON），跨语言、可读、易调试、网关友好；性能略低，但生态和可观测性更好。
 *   - 选择：内部高频调用、追求性能用 RPC；对外 API、跨团队、跨语言用 HTTP。很多公司两者并存。
 *   - 一次远程调用的组成：**建连/复用**（连接池）、**序列化**、**网络传输**、**反序列化**。耗时里最容易被忽略的是建连——没有连接池时每次 TCP 三次握手 + TLS 握手能占掉大半。
 *   - 超时必须显式设置，且要小于上游的超时（超时预算逐层递减），否则上游先断开、下游还在白干。
 *   - 重试只能用于**幂等**接口，且要限制次数 + 退避，否则雪崩时重试会放大流量 3 倍。
 *   - 易错：不设超时（默认无限等，线程池耗尽）；把非幂等的 `POST /orders` 加入自动重试，用户被扣两次钱；传输大对象（整个聚合根）而不是只传需要的字段。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class RemoteCall {
    /** 模拟一次调用的耗时分解 */
    static class CallProfile {
        long connectUs;
        long serializeUs;
        long networkUs;
        long deserializeUs;

        long totalUs() {
            return connectUs + serializeUs + networkUs + deserializeUs;
        }

        @Override
        public String toString() {
            return String.format("建连=%dus 序列化=%dus 网络=%dus 反序列化=%dus 合计=%dus",
                    connectUs, serializeUs, networkUs, deserializeUs, totalUs());
        }
    }

    static CallProfile httpCall(boolean reuseConnection, int payloadKb) {
        CallProfile p = new CallProfile();
        p.connectUs = reuseConnection ? 5 : 2000;          // 无连接池要三次握手
        p.serializeUs = payloadKb * 12L;                    // JSON 文本，体积大
        p.networkUs = payloadKb * 8L;
        p.deserializeUs = payloadKb * 10L;
        return p;
    }

    static CallProfile rpcCall(boolean reuseConnection, int payloadKb) {
        CallProfile p = new CallProfile();
        p.connectUs = reuseConnection ? 3 : 1500;
        p.serializeUs = payloadKb * 2L;                     // 二进制，体积小
        p.networkUs = payloadKb * 4L;
        p.deserializeUs = payloadKb * 2L;
        return p;
    }

    public static void main(String[] args) {
        System.out.println("=== 同一次调用：HTTP vs RPC ===");
        System.out.println("  HTTP " + httpCall(true, 20));
        System.out.println("  RPC  " + rpcCall(true, 20));

        System.out.println();
        System.out.println("=== 连接池的影响（最容易忽略的一项）===");
        System.out.println("  无连接池 HTTP " + httpCall(false, 20));
        System.out.println("  有连接池 HTTP " + httpCall(true, 20));
        long saved = httpCall(false, 20).totalUs() - httpCall(true, 20).totalUs();
        System.out.println("  单次省 " + saved + "us；1000 QPS 下每秒省 " + saved * 1000 / 1000 + "ms 的线程占用");

        System.out.println();
        System.out.println("=== 大对象传输的代价 ===");
        int[] sizes = {5, 20, 100, 500};
        for (int kb : sizes) {
            CallProfile h = httpCall(true, kb);
            System.out.println("  payload " + kb + "KB -> HTTP " + h.totalUs() / 1000 + "ms，RPC "
                    + rpcCall(true, kb).totalUs() / 1000 + "ms");
        }
        System.out.println("  建议：只传需要的字段，列表接口分页，别把整个聚合根丢过去");

        System.out.println();
        System.out.println("=== 超时预算：逐层递减 ===");
        List<int[]> budget = new ArrayList<>();
        budget.add(new int[]{3000, 800, 300});
        System.out.println("  网关 3000ms > 订单服务 800ms > 商品服务 300ms");
        System.out.println("  上游必须比下游长，否则上游已断开、下游还在白干");
        for (int[] b : budget) {
            System.out.println("  层超时 " + b[0] + " / " + b[1] + " / " + b[2] + "ms");
            break;
        }

        System.out.println();
        System.out.println("=== 重试只能用于幂等接口 ===");
        System.out.println("  GET  /items/1       可重试");
        System.out.println("  PUT  /items/1       可重试（幂等）");
        System.out.println("  POST /orders        不可自动重试（会重复下单），需幂等键");
        System.out.println("  重试放大：3 次重试 + 上游超时 = 下游流量变成 4 倍");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用示例的数据算出"payload 100KB、500 QPS、无连接池"时每秒浪费多少建连时间。
 * 2. 设计一套超时预算：网关 2s、订单服务、商品服务、库存服务分别设多少？说明依据。
 * 3. 为什么 `POST /orders` 不能自动重试？如果业务方要求"必须重试"，你会怎么改造。
 * ============================================================
 */
