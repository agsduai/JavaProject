/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-05
 * 课名：网络模式与端口映射
 * ------------------------------------------------------------
 * 知识点：
 *   - **四种网络模式**：`bridge`（默认，容器接在 docker0 网桥上，有独立 IP）、`host`（直接用宿主机网络栈，无独立 IP、无端口映射，性能最好）、`none`（只有 lo，完全离线）、`container:xxx`（与指定容器共享网络栈，K8s Pod 内多容器就是这个模式）。
 *   - **bridge 的真实链路**：每个容器有一对 veth（一端在容器里叫 eth0，一端插在 docker0 网桥），容器出网走 NAT（源地址被替换成宿主机 IP），外部访问容器走端口映射 DNAT。所以**容器 IP 外部不可直连，-p 映射才是对外入口**。
 *   - **端口映射 `-p 宿主端口:容器端口` 是一张 DNAT 表**：宿主机收到 8081 的包 → 查表改目标地址为容器 IP:8080 → 转发进容器。同一宿主机端口只能被一个映射占用，冲突直接报 "port is already allocated"。
 *   - **容器互访用服务名，不用 IP**：compose/自定义网络内置 DNS，按容器名/服务名解析到当前 IP。容器 IP 每次重建都会变，**写死容器 IP 是最常见的联调事故**。
 *   - **默认 bridge 与自定义 bridge 的区别**：默认 bridge（docker0）没有内置 DNS，只能用古老(link)或 IP 互访；`docker network create` 出来的自定义网络才有按名字解析。**compose 项目默认创建自定义网络**，所以服务名互访"开箱即用"。
 *   - **host 模式的取舍**：免去 NAT 与网桥开销、延迟最低，代价是失去网络隔离、端口自己管、不同宿主机之间没有统一抽象 —— 一般只在性能敏感的边缘场景用。
 *   - **发布 vs 暴露**：EXPOSE 只是文档；`-p/--publish` 才真正把端口映射到宿主机；compose 里 `ports:` 是发布，`expose:` 只是容器网络内可见。
 *   - 易错点：本机能 curl 容器 IP 就以为外部也能（NAT 只在宿主机上生效）；用默认 bridge 又想按名字互访（不支持，建自定义网络）；映射 0.0.0.0 却以为只有内网能访问（云主机的安全组才是最后一道门）；一个容器里写 `-p 8080:8080 -p 8080:8080`（第二个必冲突）。
 * ============================================================
 */

package com.heima.ch37;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DockerNetwork {
    record Mapping(String hostPort, String container, int containerPort, String proto) {
    }

    record Route(String from, String to, String path, String note) {
    }

    static void modes() {
        System.out.println("【四种网络模式】");
        String[][] modes = {
                {"bridge", "docker0 网桥+独立 IP+可映射端口", "默认模式，90% 场景"},
                {"host", "与宿主机共享网络栈，无 NAT", "极致性能，牺牲隔离"},
                {"none", "只有 lo 回环", "离线任务、安全敏感"},
                {"container:xx", "复用另一容器的网络栈", "K8s Pod 内多容器协作"},
        };
        for (String[] m : modes) {
            System.out.printf("  %-12s %-32s %s%n", m[0], m[1], m[2]);
        }
        System.out.println();
    }

    static void bridgeTopology() {
        System.out.println("【bridge 拓扑与一进一出两条链路】");
        List<Route> routes = List.of(
                new Route("容器 app -> 数据库容器", "容器网段直连", "app(172.17.0.2) -> docker0 -> db(172.17.0.3)",
                        "同网桥二层转发，不经 NAT"),
                new Route("外部用户 -> 容器 app", "入站 DNAT", "client -> host:8081 -> DNAT -> app:8080",
                        "由 -p 8081:8080 的映射表驱动"),
                new Route("容器 app -> 外网 API", "出站 SNAT", "app -> docker0 -> SNAT(宿主机IP) -> internet",
                        "回包按连接跟踪还原"));
        for (Route r : routes) {
            System.out.printf("  %-16s %-8s %s%n", r.from(), r.to(), r.path());
            System.out.printf("    %s%n", r.note());
        }
        System.out.println();
    }

    static void portMapping() {
        System.out.println("【-p 映射表：宿主机端口是稀缺资源】");
        List<Mapping> table = List.of(
                new Mapping("8081", "app(172.17.0.2)", 8080, "tcp"),
                new Mapping("8082", "app-2(172.17.0.4)", 8080, "tcp"),
                new Mapping("6379", "redis(172.17.0.3)", 6379, "tcp"));
        for (Mapping m : table) {
            System.out.printf("  宿主:%-5s -> %-20s 容器:%-5d %s%n",
                    m.hostPort(), m.container(), m.containerPort(), m.proto());
        }
        System.out.println("  两个 app 容器内部都用 8080 —— 端口空间是每个容器独立的");
        System.out.println("  冲突示例：再映射 -p 8081:80 会报 port is already allocated");
        System.out.println("  安全建议：redis 这类内部服务只映射 127.0.0.1:6379:6379");
        System.out.println();
    }

    static void dnsBetweenContainers() {
        System.out.println("【容器互访：按名字解析，不要写 IP】");
        Map<String, String> dns = Map.of(
                "db", "172.20.0.2",
                "redis", "172.20.0.3",
                "app", "172.20.0.4");
        for (Map.Entry<String, String> e : dns.entrySet()) {
            System.out.printf("  app 调用 %s -> 内置 DNS 解析为 %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  容器重建后 IP 会变：db 重启换成 172.20.0.9");
        System.out.println("  app 配置写 jdbc:mysql://db:3306 仍然连通，写死 IP 直接断联");
        System.out.println("  注意：默认 bridge(docker0) 没有这个 DNS；自定义网络/compose 才有");
        System.out.println();
    }

    static void commonMistakes() {
        System.out.println("【四个真实事故场景】");
        List<String> mistakes = List.of(
                "① 本机 curl 172.17.0.2:8080 通了，外部用户访问不通 —— 容器 IP 只在宿主机可达，必须 -p 发布",
                "② -p 6379:6379 忘了限定 127.0.0.1，redis 裸奔公网，被扫到无密码直接写入恶意 key",
                "③ compose 里服务名写错（db_1），DNS 解析失败，一直 connection refused",
                "④ host 模式两个服务都监听 8080，第二个起不来 —— 共享宿主机端口空间");
        for (String m : mistakes) {
            System.out.println("  " + m);
        }
        System.out.println("  共同根因：把“容器网络”当成“宿主机网络”来理解");
    }

    public static void main(String[] args) {
        modes();
        bridgeTopology();
        portMapping();
        dnsBetweenContainers();
        commonMistakes();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 起两个 nginx 容器分别映射 8081/8082，宿主机 curl 验证；再尝试把第二个映射到同一个 8081，记录报错原文。
 * 2. 用 `docker network create` 建自定义网络，跑一个 redis 和一个 `redis-cli` 客户端容器，让客户端用服务名连接；再换到默认 bridge 重试，解释差异。
 * 3. 用 `-p 127.0.0.1:6379:6379` 与 `-p 6379:6379` 分别启动 redis，用外部机器（或公网 IP）探测，写出两种写法的安全差异。
 * 4. 思考题：K8s Pod 里的两个容器可以直接用 localhost 互访，它们用了本章的哪种网络模式？这个设计给 Sidecar 模式带来什么便利？
 * ============================================================
 */
