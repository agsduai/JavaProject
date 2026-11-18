/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：22-05
 * 课名：依赖冲突与调解
 * ------------------------------------------------------------
 * 知识点：
 *   - 传递依赖：A 依赖 B，B 依赖 C，引入 A 时 C 会被自动引入，这是依赖管理最省事也最容易出问题的地方。
 *   - 同一个 jar 出现多个版本时，Maven 按两条规则选一个：**最短路径优先**（离项目层级最近的胜出），路径一样长时**先声明者优先**。
 *   - 冲突的典型症状：`NoSuchMethodError`、`ClassNotFoundException`、`AbstractMethodError`，编译能过但运行报错。
 *   - `<exclusions>` 主动排除某个传递依赖；`<optional>true</optional>` 让依赖不继续向下传递。
 *   - `<dependencyManagement>` 统一锁定版本，子模块不写 `version` 也能拿到指定版本，这是解决冲突最有效的手段。
 *   - 易错：`mvn dependency:tree` 是排查冲突的第一手段，不要靠猜。
 * ============================================================
 */

package com.heima.ch22;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DependencyMediation {
    public static void main(String[] args) {
        // 模拟：项目直接声明两个依赖，各自又带进来不同版本的 commons-lang3
        Node a = new Node("spring-boot-starter-web", "3.3.2");
        a.children.add(new Node("commons-lang3", "3.12.0"));

        Node b = new Node("some-utils", "1.0.0");
        Node mid = new Node("inner-lib", "2.0.0");
        mid.children.add(new Node("commons-lang3", "3.9"));
        b.children.add(mid);

        List<Node> roots = new ArrayList<>(List.of(a, b));
        System.out.println("依赖树：");
        for (Node n : roots) {
            print(n, 0);
        }

        // 最短路径优先：广度优先遍历，先到者胜；同层按声明顺序
        Map<String, String> resolved = new LinkedHashMap<>();
        List<Node> level = new ArrayList<>(roots);
        int depth = 1;
        while (!level.isEmpty()) {
            List<Node> next = new ArrayList<>();
            for (Node n : level) {
                resolved.putIfAbsent(n.artifactId, n.version + "（第 " + depth + " 层）");
                next.addAll(n.children);
            }
            level = next;
            depth++;
        }

        System.out.println();
        System.out.println("调解结果：");
        resolved.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
        System.out.println();
        System.out.println("结论：commons-lang3 取 3.12.0，因为它到项目的路径更短（3.9 在第三层被跳过）");
    }

    static class Node {
        String artifactId;
        String version;
        List<Node> children = new ArrayList<>();

        Node(String artifactId, String version) {
            this.artifactId = artifactId;
            this.version = version;
        }
    }

    static void print(Node n, int depth) {
        System.out.println("  ".repeat(depth) + (depth == 0 ? "" : "+- ")
                + n.artifactId + ":" + n.version);
        for (Node c : n.children) {
            print(c, depth + 1);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在你的工程里执行 `mvn dependency:tree`，找出重复出现的 artifactId，说明 Maven 最终选了哪个版本。
 * 2. 画出"最短路径优先"和"第一声明优先"两条规则的判定流程图。
 * 3. 用 `<dependencyManagement>` 把某个 jar 锁定到指定版本，然后验证子模块里的写法。
 * ============================================================
 */
