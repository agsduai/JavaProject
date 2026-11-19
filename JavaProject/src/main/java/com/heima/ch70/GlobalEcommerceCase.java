/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-11
 * 课名：综合案例：出海电商多语言改造
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一次改造**：**文案抽离**（70-02）-> **格式本地化**（货币 70-05/日期 70-04）-> **排序适配**（70-06）-> **搜索归一化**（70-10）-> **翻译工作流**（70-07）-> **多端一致**（70-08）-> **测试**（70-09）。
 *   - **改造的顺序很重要**：**先抽离**（否则后面都白做）-> **再格式化**（显示正确）-> **再搜索**（用户能找到）-> **最后测试与流程**（持续可维护）——**抽离是地基**（70-02）。
 *   - **验收清单**：文案无硬编码、金额按币种小数位、日期带时区、排序用 Collator、搜索归一化、翻译完整、伪本地化无溢出、缓存键含语言。
 *   - **常见返工点**：UI 文案长度溢出（德语）、货币小数位写死、时区用服务器默认、搜索重音不匹配——**这四项占 i18n bug 的大半**（70-05/06/10）。
 *   - 易错点：只改后端不改前端（前端硬编码文案）；只测英文；货币与日期格式混用（`¥1,234.56` 出现在德语页）；上线后才发现 RTL 没适配。
 * ============================================================
 */

package com.heima.ch70;

import java.text.Collator;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ==================== 示例代码 ====================

public class GlobalEcommerceCase {
    record Product(String id, String name, long priceMinor, String currency, int digits) {
        String displayPrice(Locale locale) {
            NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
            nf.setCurrency(Currency.getInstance(currency));
            nf.setMaximumFractionDigits(digits);
            nf.setMinimumFractionDigits(digits);
            return nf.format(priceMinor / Math.pow(10, digits));
        }
    }

    static void priceAcrossMarkets() {
        List<Product> products = List.of(
                new Product("p-1", "Phone Case", 6900, "CNY", 2),
                new Product("p-2", "Phone Case", 999, "USD", 2),
                new Product("p-3", "Phone Case", 1500, "JPY", 0));
        System.out.println("【同一商品在不同市场的标价】");
        for (Product p : products) {
            System.out.printf("  %-4s %-6s 存储=%d(%s,%d位) -> 本地展示=%s%n",
                    p.id(), p.currency(), p.priceMinor(), p.currency(), p.digits(),
                    p.displayPrice(localeOf(p.currency())));
        }
        System.out.println();
    }

    static Locale localeOf(String currency) {
        return switch (currency) {
            case "CNY" -> Locale.CHINA;
            case "USD" -> Locale.US;
            case "JPY" -> Locale.JAPAN;
            default -> Locale.US;
        };
    }

    static void dateAcrossZones() {
        Instant instant = Instant.ofEpochMilli(1_763_000_000_000L);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
        Map<String, ZoneId> zones = new LinkedHashMap<>();
        zones.put("CNY 站点", ZoneId.of("Asia/Shanghai"));
        zones.put("USD 站点", ZoneId.of("America/New_York"));
        zones.put("JPY 站点", ZoneId.of("Asia/Tokyo"));
        System.out.println("【同一订单时间在不同站点】");
        zones.forEach((site, zone) -> System.out.printf("  %-8s %s%n", site,
                ZonedDateTime.ofInstant(instant, zone).format(fmt)));
        System.out.println();
    }

    static void sortingAdapted() {
        List<String> products = new ArrayList<>(List.of(
                "Apple Case", "äpfel Leder", "Zebra Stand", "árbol Funda"));
        List<String> raw = new ArrayList<>(products);
        raw.sort(String::compareTo);
        List<String> localized = new ArrayList<>(products);
        localized.sort(Collator.getInstance(Locale.GERMAN));
        System.out.println("【商品名排序】");
        System.out.println("  compareTo：  " + raw);
        System.out.println("  Collator(de)：" + localized);
        System.out.println();
    }

    static void acceptance() {
        System.out.println("【改造验收清单】");
        List<String> gates = List.of(
                "文案无硬编码（伪本地化后全部变形）",
                "金额按币种小数位（JPY 0 位、KWD 3 位）",
                "日期展示带时区（按站点）",
                "排序用 Collator（多语言列表不乱序）",
                "搜索归一化（Café 能搜到 cafe）",
                "翻译完整 + 占位符一致（CI 校验）",
                "UI 无溢出（德语加长 30% 仍容纳）",
                "缓存键含语言（切换后不读旧文案）");
        gates.forEach(g -> System.out.println("  [ ] " + g));
        System.out.println();
        System.out.println("【常见返工点 Top4】");
        Map<String, String> rework = new LinkedHashMap<>();
        rework.put("文案溢出", "德语/俄语长 30%，UI 截断");
        rework.put("小数位写死", "日元显示 100.00");
        rework.put("时区默认", "不同机房显示不同日期");
        rework.put("重音不匹配", "搜 cafe 找不到 Café");
        rework.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
    }

    public static void main(String[] args) {
        priceAcrossMarkets();
        dateAcrossZones();
        sortingAdapted();
        acceptance();
        System.out.println();
        System.out.println("  改造顺序：抽离 -> 格式化 -> 排序/搜索 -> 流程与测试（抽离是地基）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现多语言商品展示（价格/日期/排序），在 CNY/USD/JPY 三个站点验证各项本地化正确。
 * 2. 为你的项目做一次 i18n 体检：按 8 项验收清单逐项打分，列出返工优先级。
 * 3. 思考题：为什么"抽离"必须最先做？从"后续所有工作都依赖文案可配置"的角度分析。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 把一个电商系统做成全球化：多语言与文案管理、时区与货币、本地化格式（日期/数字/地址）、多区域部署与数据合规、翻译回退链、RTL 布局。要求「同一份代码服务所有地区」。
 * 【任务】
 * 1. 实现多语言：资源文件按 locale 组织、运行时切换语言、缺失文案的回退链（zh-HK → zh → en），验证不出现 key 裸露。
 * 2. 处理时区：所有存储用 UTC、展示按用户时区转换、跨时区下单与统计口径，说明「今天」在不同时区不是同一段时间。
 * 3. 处理货币与格式：金额用最小单位整数存储、按 locale 格式化、汇率换算与舍入规则。
 * 4. 设计多区域部署：数据驻留（欧盟数据不出境）、就近接入、跨区一致性策略，说明哪些数据可以跨区、哪些不能。
 * 【验收清单】
 * [ ] 切换到任一语言后界面无未翻译的 key，回退链生效。
 * [ ] 同一时刻在不同时区展示的「今天订单数」解释得清，不出现跨天错账。
 * [ ] 金额计算无浮点误差，格式化符合当地习惯（如 1.234,56）。
 * [ ] 数据驻留要求被满足，跨区调用的数据范围有明确清单。
 * ============================================================
 */
