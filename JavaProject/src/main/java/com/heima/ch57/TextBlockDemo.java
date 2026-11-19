/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-06
 * 课名：文本块与字符串处理
 * ------------------------------------------------------------
 * 知识点：
 *   - **文本块（Java 15 定稿）解决多行字符串的转义地狱**：`"""` 三引号包裹，**自动处理行首缩进**（按最左非空白行对齐）、**保留换行**、**无需 `\n` 拼接**——JSON/SQL/HTML/模板的书写体验质变。
 *   - **文本块的三个细节**：① 结束定界符位置决定缩进基准；② `\` 行尾表示"不换行"（拼接下一行）；③ `\s` 表示空格（防止编辑器去尾空格）。**`"""` 内的 `"` 无需转义**（除非连续三个）。
 *   - **字符串增强（Java 11~21）**：`isBlank/strip/repeat/lines`；**`formatted()`** 替代 `String.format`；**`stripIndent/translateEscapes`** 处理文本加工。
 *   - **文本块不是模板引擎**：它是"字面量"，值仍需拼接或 `formatted` 占位。**字符串模板（STR."..."）在 21 仍是预览**——生产用 `formatted` 或 `String.format`（40-03 Prompt 拼接的现代写法）。
 *   - **JSON 拼装的正确姿势**：**永远不要用字符串拼 JSON**（40-03/36-02 的注入教训）——文本块适合"静态模板 + 少量占位"，值要通过序列化器编码；**文本块里放 SQL 也一样，值用占位符**（21-01 参数绑定）。
 *   - 易错点：文本块缩进基准搞错（输出多出空格）；`"""` 里嵌入 `"""`（需转义或拆分）；把文本块当模板插值用（没有插值语义）；用文本块拼用户输入（注入）。
 * ============================================================
 */

package com.heima.ch57;

// ==================== 示例代码 ====================

public class TextBlockDemo {
    /** 一段多行 JSON：值用占位符 + 序列化思想 */
    static String orderJson(String orderId, long cents) {
        String id = orderId.replace("\"", "\\\"");        // 值必须转义（真实项目用序列化器）
        return """
                {
                  "orderId": "%s",
                  "amountCents": %d,
                  "currency": "CNY",
                  "items": []
                }""".formatted(id, cents);
    }

    /** SQL 模板：结构在文本块，值走占位符 */
    static final String SQL_FIND =
            """
            SELECT o.id, o.amount, u.name
              FROM orders o
              JOIN users u ON u.id = o.user_id
             WHERE o.tenant_id = ?
               AND o.status = ?
             ORDER BY o.created_at DESC
             LIMIT ?""";

    /** 文本块的行处理：lines / strip / isBlank */
    static void inspect(String text) {
        System.out.printf("  行数=%d 空行=%d 首行=[%s]%n",
                text.lines().count(),
                text.lines().filter(String::isBlank).count(),
                text.lines().findFirst().orElse(""));
    }

    public static void main(String[] args) {
        System.out.println(orderJson("9001", 6900));
        System.out.println("  ---- SQL 模板 ----");
        System.out.println(SQL_FIND);
        System.out.println("  占位符数 = " + SQL_FIND.chars().filter(c -> c == '?').count());
        inspect(orderJson("9002", 100));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用文本块重写项目里三段拼接的 SQL 与一段 JSON，统计可读性改善（行数与转义字符数）。
 * 2. 实现一个"文本块模板渲染器"：解析 `{name}` 占位符并替换，同时对值做 JSON 转义，验证注入字符被安全处理。
 * 3. 思考题：为什么"文本块 + 占位符"仍然不能替代参数化查询？从"结构安全"与"值编码"的分工分析。
 * ============================================================
 */
