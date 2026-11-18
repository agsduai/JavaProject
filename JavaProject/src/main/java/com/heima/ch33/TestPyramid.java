/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-06
 * 课名：测试分层：单元、集成与端到端
 * ------------------------------------------------------------
 * 知识点：
 *   - **测试金字塔**：底层大量单元测试（快、稳、定位准），中层少量集成测试（验证组件协作），顶层极少量端到端测试（验证用户视角的完整链路）。倒金字塔（大量 E2E）会导致构建慢、失败难定位。
 *   - **单元测试**：单类/单方法，依赖全部替换（Stub/Fake），毫秒级，不碰网络/文件/数据库。
 *   - **集成测试**：真实组件协作，但外部系统可替换。典型是"Service + 真实 Repository（内存库）+ 真实事务"，验证 SQL/映射/事务边界。
 *   - **端到端测试**：从 HTTP 入口到数据库全链路。数量要少（10~30 个核心链路），因为慢且脆弱。
 *   - 分层的**判定标准**不是"用了多少层"，而是"**失败时能否快速定位**"。单元测试红了指向一个方法，E2E 红了只能知道"下单不行"。
 *   - 集成测试的隔离手段：每个用例独立事务回滚（`@Transactional`）、独立临时库/schema、内存实现（H2/SQLite）、Testcontainers（真容器）。
 *   - **测试数据隔离**：用例间共享数据是 flaky 的头号原因。方案：每用例造唯一数据（UUID 后缀）、`@BeforeEach` 清表、临时目录用 `@TempDir`。
 *   - **测试顺序无关**：单独跑和全量跑结果必须一致。用固定随机种子（`new Random(42)`）而不是无种子随机。
 *   - 端到端测试的稳定性技巧：不用 `Thread.sleep`（改轮询 + 超时）、给 HTTP 客户端设超时、测试端口用 0（随机端口）、失败时保留现场（日志、临时文件不删）。
 *   - 易错：把集成测试当单元测试写（每个方法都起 Spring 容器，构建 10 分钟）；E2E 覆盖所有分支；用例间靠执行顺序传数据；集成测试连生产库。
 * ============================================================
 */

package com.heima.ch33;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TestPyramid {
    /** ---------- 领域层 ---------- */
    static class StockRepo {
        private final Map<String, Integer> stock = new LinkedHashMap<>();

        void set(String sku, int qty) {
            stock.put(sku, qty);
        }

        int get(String sku) {
            return stock.getOrDefault(sku, 0);
        }

        boolean tryDeduct(String sku, int qty) {
            int cur = get(sku);
            if (cur < qty) {
                return false;
            }
            stock.put(sku, cur - qty);
            return true;
        }
    }

    static class StockService {
        private final StockRepo repo;

        StockService(StockRepo repo) {
            this.repo = repo;
        }

        String deduct(String sku, int qty) {
            if (qty <= 0) {
                return "BAD_QTY";
            }
            return repo.tryDeduct(sku, qty) ? "OK" : "NO_STOCK";
        }
    }

    /** ---------- 应用层（HTTP 入口）---------- */
    static class OrderApi {
        private final StockService service;

        OrderApi(StockService service) {
            this.service = service;
        }

        HttpServer start(int port) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/deduct", this::handle);
            server.setExecutor(null);
            server.start();
            return server;
        }

        private void handle(HttpExchange ex) throws IOException {
            URI uri = ex.getRequestURI();
            String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
            String sku = "A";
            int qty = 1;
            for (String kv : query.split("&")) {
                int i = kv.indexOf('=');
                if (i < 0) {
                    continue;
                }
                String k = kv.substring(0, i);
                String v = kv.substring(i + 1);
                if ("sku".equals(k)) {
                    sku = v;
                } else if ("qty".equals(k)) {
                    try {
                        qty = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        respond(ex, 400, "BAD_PARAM");
                        return;
                    }
                }
            }
            respond(ex, 200, service.deduct(sku, qty));
        }

        private void respond(HttpExchange ex, int code, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static int passed;
    static final List<String> failures = new ArrayList<>();

    static void check(String layer, String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.printf("    [%s] %-46s OK%n", layer, name);
        } else {
            failures.add(layer + " / " + name + " -> " + detail);
            System.out.printf("    [%s] %-46s FAIL: %s%n", layer, name, detail);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 单元测试：Stub 替身，不碰真实仓储 ===");
        StockRepo stub = new StockRepo() {
            @Override
            int get(String sku) {
                return 10;
            }

            @Override
            boolean tryDeduct(String sku, int qty) {
                return qty <= 10;
            }
        };
        StockService unitSvc = new StockService(stub);
        check("UNIT", "qty=3 扣减成功", "OK".equals(unitSvc.deduct("A", 3)), "返回值不符");
        check("UNIT", "qty=11 库存不足", "NO_STOCK".equals(unitSvc.deduct("A", 11)), "返回值不符");
        check("UNIT", "qty=0 参数非法", "BAD_QTY".equals(unitSvc.deduct("A", 0)), "返回值不符");
        check("UNIT", "qty=-1 参数非法", "BAD_QTY".equals(unitSvc.deduct("A", -1)), "返回值不符");

        System.out.println();
        System.out.println("=== 2. 集成测试：真实仓储 + 真实服务，验证状态变化 ===");
        StockRepo realRepo = new StockRepo();
        realRepo.set("SKU-1", 5);
        StockService integSvc = new StockService(realRepo);
        check("INTEG", "扣 2 返回 OK", "OK".equals(integSvc.deduct("SKU-1", 2)), "返回值不符");
        check("INTEG", "扣减后库存 = 3", realRepo.get("SKU-1") == 3, "实际=" + realRepo.get("SKU-1"));
        check("INTEG", "再扣 4 失败", "NO_STOCK".equals(integSvc.deduct("SKU-1", 4)), "返回值不符");
        check("INTEG", "失败后库存不变 = 3", realRepo.get("SKU-1") == 3, "实际=" + realRepo.get("SKU-1"));
        check("INTEG", "扣 3 后库存 = 0", "OK".equals(integSvc.deduct("SKU-1", 3))
                && realRepo.get("SKU-1") == 0, "库存=" + realRepo.get("SKU-1"));

        System.out.println();
        System.out.println("=== 3. 端到端测试：真起 HTTP 服务 + 真发请求 ===");
        StockRepo e2eRepo = new StockRepo();
        e2eRepo.set("A", 2);
        HttpServer server = new OrderApi(new StockService(e2eRepo)).start(0);
        int port = server.getAddress().getPort();
        System.out.println("    HttpServer 随机端口 = " + port + "（端口 0 避免冲突）");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        try {
            String r1 = get(client, port, "/deduct?sku=A&qty=1");
            check("E2E", "第 1 次扣减", "OK".equals(r1), "body=" + r1);
            String r2 = get(client, port, "/deduct?sku=A&qty=1");
            check("E2E", "第 2 次扣减", "OK".equals(r2), "body=" + r2);
            String r3 = get(client, port, "/deduct?sku=A&qty=1");
            check("E2E", "第 3 次扣减库存不足", "NO_STOCK".equals(r3), "body=" + r3);
            String r4 = get(client, port, "/deduct?sku=A&qty=abc");
            check("E2E", "非法参数", "BAD_PARAM".equals(r4), "body=" + r4);
            check("E2E", "最终库存 = 0", e2eRepo.get("A") == 0, "库存=" + e2eRepo.get("A"));
        } finally {
            server.stop(0);
            System.out.println("    服务已停止（finally 里清理，避免端口泄漏）");
        }

        System.out.println();
        System.out.println("=== 4. 三层对比 ===");
        String[][] t = {
                {"单元", "毫秒", "单类，依赖全替身", "方法级定位", "大量（几百）"},
                {"集成", "百毫秒~秒", "真实组件协作", "组件边界", "中量（几十）"},
                {"端到端", "秒~分钟", "HTTP 到存储全链路", "链路级定位", "少量（核心 10~30）"}
        };
        System.out.printf("  %-6s %-14s %-22s %-14s %s%n", "层级", "耗时", "范围", "失败定位", "建议数量");
        for (String[] r : t) {
            System.out.printf("  %-6s %-14s %-22s %-14s %s%n", r[0], r[1], r[2], r[3], r[4]);
        }

        System.out.println();
        System.out.println("=== 5. 防 flaky 检查清单 ===");
        String[] tips = {
                "每用例造唯一数据（UUID 后缀），不依赖上一个用例",
                "固定随机种子，失败可复现",
                "异步用轮询 + 超时，不用 sleep",
                "HTTP 端口用 0 让系统分配，避免占用冲突",
                "临时文件用 Files.createTempDirectory，结束删除",
                "集成测试每用例事务回滚或清表",
                "客户端设置超时，避免测试挂死",
                "断言失败信息带上实际值"
        };
        for (String s : tips) {
            System.out.println("    - " + s);
        }

        System.out.println();
        System.out.println("=== 结果 ===");
        System.out.println("  通过 " + passed + "，失败 " + failures.size());
        for (String f : failures) {
            System.out.println("    " + f);
        }
    }

    static String get(HttpClient client, int port, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body().trim();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为 `OrderApi` 增加 `POST /deduct`（JSON body），写集成测试验证非法 JSON 返回 400 而不是 500。
 * 2. 把 E2E 用例改成"并发 10 个请求扣库存 2"，断言成功数恰好 2（提示：`StockRepo` 需改成线程安全）。
 * 3. 说明为什么"每个测试方法都加 `@SpringBootTest`"会让构建从 30 秒变成 10 分钟，并给出分层改造方案。
 * ============================================================
 */
