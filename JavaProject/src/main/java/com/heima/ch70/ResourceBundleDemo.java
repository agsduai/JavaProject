/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-02
 * 课名：资源束与消息格式化
 * ------------------------------------------------------------
 * 知识点：
 *   - **ResourceBundle 是文案抽离的标准机制**：`messages_zh_CN.properties`、`messages_en_US.properties`——**按 Locale 自动匹配**（找不到 `zh_CN` 会退化到 `zh` 再到默认）；**属性文件用 UTF-8**（老版本需 `native2ascii`，现在 JDK 9+ 默认 UTF-8）。
 *   - **`MessageFormat` 解决占位符**：`"Hello, {0}! You have {1} new messages."`——**支持位置占位、数字/日期格式、复数选择**（`{1,number,integer}`）；**占位符序号比命名更常用**（兼容性更好）。
 *   - **`ChoiceFormat` 处理区间**：`"no files | one file | {2} files"`——**按数值区间选文案**；更复杂用 ICU 的复数规则（70-03）。
 *   - **文案治理的三条**：**key 命名规范**（`order.status.paid` 而非 `msg1`）、**禁止在代码里写字面量**（CI 扫描）、**翻译完整性校验**（缺 key 时构建失败，而不是运行期显示 `!key!`）。
 *   - **与 69/55 章的关系**：多语言是 SaaS 多租户的常见需求（55-01 的租户配置可以指定语言）；广告/推荐的多语言内容也是同一套机制（69-01）。
 *   - 易错点：用 `String.format` 代替 MessageFormat（不支持复数/选择性文案）；properties 编码错误（中文乱码）；缺失 key 静默显示 `!key!`；占位符序号错位（参数顺序搞反）。
 * ============================================================
 */

package com.heima.ch70;

import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

// ==================== 示例代码 ====================

public class ResourceBundleDemo {
    /** 模拟三个语言包（真实项目放 properties 文件） */
    static final Map<String, Map<String, String>> bundles = new LinkedHashMap<>();

    static {
        Map<String, String> zh = new LinkedHashMap<>();
        zh.put("order.paid", "订单 {0} 已支付，金额 {1,number,#.##} 元");
        zh.put("msg.unread", "你有 {0} 条未读消息");
        zh.put("file.count", "没有文件|一个文件|{2} 个文件");
        bundles.put("zh_CN", zh);

        Map<String, String> en = new LinkedHashMap<>();
        en.put("order.paid", "Order {0} has been paid: {1,number,#.##} CNY");
        en.put("msg.unread", "You have {0} unread messages");
        en.put("file.count", "no files|one file|{2} files");
        bundles.put("en_US", en);

        Map<String, String> de = new LinkedHashMap<>();
        de.put("order.paid", "Bestellung {0} wurde bezahlt: {1,number,#.##} EUR");
        de.put("msg.unread", "Sie haben {0} ungelesene Nachrichten");
        de.put("file.count", "keine Dateien|eine Datei|{2} Dateien");
        bundles.put("de_DE", de);
    }

    /** 模拟 ResourceBundle 的查找：精确 -> 语言 -> 默认 */
    static String get(Locale locale, String key) {
        String tag = locale.toString();
        String lang = locale.getLanguage();
        Map<String, String> exact = bundles.get(tag);
        if (exact != null && exact.containsKey(key)) {
            return exact.get(key);
        }
        for (Map.Entry<String, Map<String, String>> e : bundles.entrySet()) {
            if (e.getKey().startsWith(lang) && e.getValue().containsKey(key)) {
                return e.getValue().get(key);
            }
        }
        throw new MissingResourceException("缺少文案：", "Bundle", key);
    }

    static String message(Locale locale, String key, Object... args) {
        return new MessageFormat(get(locale, key), locale).format(args);
    }

    /** 区间选择：0/1/N 用不同文案 */
    static String choiceText(int count) {
        double[] limits = {0, 1, 2};
        String[] formats = {"没有文件", "一个文件", "{2} 个文件"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);
        return cf.format(count);
    }

    public static void main(String[] args) {
        System.out.println("【同一 key 在三种语言下】");
        for (Locale l : new Locale[]{Locale.CHINA, Locale.US, Locale.GERMANY}) {
            String paid = message(l, "order.paid", "9001", 69.5);
            String unread = message(l, "msg.unread", 5);
            System.out.printf("  %-6s %s%n         %s%n", l, paid, unread);
        }

        System.out.println();
        System.out.println("【ChoiceFormat 区间文案】");
        for (int n : new int[]{0, 1, 3}) {
            System.out.printf("  %d 个文件 -> %s%n", n, choiceText(n));
        }

        System.out.println();
        System.out.println("【缺失 key 的两种处理】");
        try {
            message(Locale.US, "not.exist", 1);
        } catch (MissingResourceException e) {
            System.out.println("  严格模式：抛异常（推荐，CI 期就能发现）");
        }
        System.out.println("  宽松模式：显示 !key!（运行期才发现，体验差）");

        System.out.println();
        System.out.println("【文案治理】");
        System.out.println("  1) key 用点分语义命名（order.status.paid）");
        System.out.println("  2) 禁止字面量（CI 扫描硬编码中文/英文）");
        System.out.println("  3) 翻译完整性校验：缺 key 构建失败");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三语言资源束（zh/en/de），用 MessageFormat 渲染带占位符的文案，验证异常与缺失 key 处理。
 * 2. 实现"翻译完整性校验器"：对比基准语言与目标语言的 key 集合，输出缺失与多余。
 * 3. 思考题：为什么"代码里写字面量"是 i18n 的最大障碍？从"抽离成本"与"翻译流程"两个角度分析。
 * ============================================================
 */
