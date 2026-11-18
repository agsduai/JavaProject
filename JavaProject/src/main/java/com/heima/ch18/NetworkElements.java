/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：18-01
 * 课名：网络编程三要素
 * ------------------------------------------------------------
 * 知识点：
 *   - IP 地址：网络中设备的唯一标识，IPv4 是 32 位（点分十进制），IPv6 是 128 位；`127.0.0.1` 表示本机回环地址。
 *   - 端口号：标识设备上的某个应用程序，范围 0~65535，其中 0~1023 是系统保留端口（HTTP 80、HTTPS 443）。
 *   - 协议：通信双方约定的数据格式规则，常见有 TCP、UDP、HTTP、FTP。
 *   - `InetAddress` 类表示 IP 地址：`getByName`、`getLocalHost`、`getHostName`、`getHostAddress`。
 *   - 一个程序要能被访问，必须"IP + 端口 + 协议"三者确定，缺一不可。
 *   - 易错：端口号被占用会抛 `BindException`；同一端口同一时刻只能被一个程序监听（TCP）。
 * ============================================================
 */

package com.heima.ch18;

import java.net.InetAddress;
import java.net.UnknownHostException;

// ==================== 示例代码 ====================

public class NetworkElements {
    public static void main(String[] args) {
        try {
            // 获取本机地址
            InetAddress local = InetAddress.getLocalHost();
            System.out.println("本机主机名：" + local.getHostName());
            System.out.println("本机 IP：" + local.getHostAddress());

            // 通过域名获取 IP
            InetAddress baidu = InetAddress.getByName("www.baidu.com");
            System.out.println("百度的 IP：" + baidu.getHostAddress());

            // 通过 IP 字符串获取
            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            System.out.println("回环地址：" + loopback.getHostAddress());

            // 判断是否可达（会发起网络请求，可能较慢）
            System.out.println("本机是否可达：" + local.isReachable(1000));
        } catch (UnknownHostException e) {
            System.out.println("域名解析失败：" + e.getMessage());
        } catch (java.io.IOException e) {
            System.out.println("网络异常：" + e.getMessage());
        }

        // 常用端口说明
        System.out.println("HTTP 默认端口：80");
        System.out.println("HTTPS 默认端口：443");
        System.out.println("MySQL 默认端口：3306");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写程序输出本机的主机名和 IP 地址，再查询三个常用网站的 IP 并打印。
 * 2. 说明 `127.0.0.1` 和 `localhost` 的区别，以及端口号 0~1023 为什么不能随便用。
 * 3. 用自己的话解释 TCP 和 UDP 的区别，各举两个实际应用场景。
 * ============================================================
 */
