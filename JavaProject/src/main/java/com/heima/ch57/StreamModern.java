/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-09
 * 课名：Stream 与 Optional 的现代用法
 * ------------------------------------------------------------
 * 知识点：
 *   - **`Stream.toList()`（Java 16）**：替代 `collect(Collectors.toList())`，**返回不可变列表**——这是与老写法的关键差异（想可变要 `new ArrayList<>(stream.toList())`）。
 *   - **`mapMulti`（Java 16）**：一对多展开（`flatMap` 的替代），**不产生中间流**，性能更好且支持条件展开；`Stream.ofNullable` 处理可空元素。
 *   - **Optional 的现代 API**：`orElseThrow()`（无参版本，空则抛 NoSuchElementException）、`ifPresentOrElse(consumer, runnable)`、`or(Supplier<Optional>)`、`stream()`（0/1 元素的流，**消除 `if (opt.isPresent())` 分支**）。
 *   - **Optional 的使用纪律**（15 章的老话题，现代写法强化）：**只用于返回值**，不用于字段/参数/集合元素（`List<Optional<T>>` 是反模式）；不要 `opt.get()`（用 orElse/ifPresent/orElseThrow）；**Optional 链式优于嵌套 if**。
 *   - **Stream 的工程纪律**：保持"声明式管道"短而清晰（超过 5 个操作符考虑拆方法）、副作用收在终端操作（`peek` 只用于调试）、并行流只在"大数据量 + 无共享状态 + 纯计算"时用（50-09 已验）。
 *   - 易错点：`toList()` 后尝试修改（不可变）；`Optional.get()` 滥用（空值 NPE）；`mapMulti` 与 `flatMap` 混用选错（有 IO 副作用时反而要 flatMap）；并行流里写共享集合（线程安全问题）。
 * ============================================================
 */

package com.heima.ch57;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// ==================== 示例代码 ====================

public class StreamModern {
    record User(String name, String email) {
    }

    /** Optional.stream：把"可能没有"自然融入流管道 */
    static List<String> emailsOf(List<User> users, String domain) {
        return users.stream()
                .map(u -> Optional.ofNullable(u.email()))
                .flatMap(Optional::stream)                 // 空的自动消失
                .filter(e -> e.endsWith(domain))
                .map(String::toLowerCase)
                .toList();
    }

    /** mapMulti：条件展开，无中间集合 */
    static List<String> splitTags(String csvTags) {
        return Stream.of(csvTags.split(";"))
                .<String>mapMulti((token, downstream) -> {
                    String trimmed = token.strip();
                    if (!trimmed.isEmpty()) {
                        downstream.accept(trimmed);
                    }
                })
                .toList();
    }

    /** ifPresentOrElse：无 if/else 的"有则处理，无则兜底" */
    static String display(Optional<User> user) {
        StringBuilder sb = new StringBuilder();
        user.ifPresentOrElse(
                u -> sb.append("用户：").append(u.name()),
                () -> sb.append("匿名用户"));
        return sb.toString();
    }

    public static void main(String[] args) {
        List<User> users = List.of(
                new User("alice", "Alice@Ex.COM"),
                new User("bob", null),
                new User("carol", "carol@other.cn"));
        System.out.println("  @ex.com 邮箱：" + emailsOf(users, "@ex.com"));
        System.out.println("  标签解析：" + splitTags(" java ;; spring ;jvm "));
        System.out.println("  " + display(Optional.of(users.get(0))));
        System.out.println("  " + display(Optional.empty()));
        System.out.println("  toList 不可变：" + tryAdd(emailsOf(users, "@ex.com")));
    }

    static String tryAdd(List<String> list) {
        try {
            list.add("x");
            return "意外成功";
        } catch (UnsupportedOperationException e) {
            return "不可变（预期）";
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把项目里 5 处 `isPresent()` + `get()` 的写法改为 `ifPresentOrElse` / `orElseThrow` / `Optional.stream`，各写单测保证行为等价。
 * 2. 用 `mapMulti` 实现"把每行文本按逗号拆分并过滤空项"，对比 `flatMap` 版本的内存分配（记录代码长度与可读性）。
 * 3. 思考题：为什么 `List<Optional<T>>` 是反模式？从"空值语义"与"集合已能表达 0~N 元素"的重叠分析。
 * ============================================================
 */
