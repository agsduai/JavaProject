### 70-01 i18n 全景：语言、地区与格式

**知识点**
- **国际化（i18n）与本地化（l10n）是两件事**：**i18n 是"架构上支持多语言"**（把文案抽离、格式化可配置、布局可伸缩）；**l10n 是"具体某个市场的适配"**（翻译、货币、日期格式、配色、合规）。**先有 i18n 能力，才能高效做 l10n**。
- **`Locale` 是语言 + 地区的二元组**：`zh_CN`（中文-中国）、`en_US`（英语-美国）、`en_GB`（英语-英国）、`pt_BR`（葡语-巴西）——**同一个语言在不同地区格式不同**（日期/货币/数字），**只用语言不用地区是常见错误**。
- **五个必须本地化的维度**：**文案**（ResourceBundle 70-02）、**数字与货币**（70-05）、**日期时间与时区**（70-04）、**排序与比较**（Collator 70-06）、**复数与性别**（70-03）——**少一个就会在某个市场"看起来不对劲"**。
- **不要做的事**：**在代码里拼句子**（`"Hello " + name` 在日语/德语语序不同，应该用占位符 + 翻译）；**用字符串比较排序**（中文/德文排序规则不同 70-06）；**用 `new Date()` 无时区**（70-04）；**假设文案长度固定**（德语通常比英语长 30%，UI 会溢出）。
- **与 55/67 章的关系**：多租户 SaaS 的"区域部署"（55-10）与位置数据的"坐标系差异"（67-02）都是本地化问题——**i18n 是出海系统的地基**。
- 易错点：只翻文案不管格式（日期显示成美式，用户看不懂）；代码拼句子（翻译后语序错乱）；硬编码货币符号；忽略 RTL（阿拉伯语从右往左）。

**示例代码**
```java
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class I18nOverview {
    static void localeAnatomy() {
        System.out.println("【Locale = 语言 + 地区】");
        Map<String, String> locales = new LinkedHashMap<>();
        locales.put("zh_CN", "中文（中国）：¥1,234.56 / 2026年11月18日");
        locales.put("en_US", "English (US)：$1,234.56 / Nov 18, 2026");
        locales.put("en_GB", "English (UK)：£1,234.56 / 18 Nov 2026");
        locales.put("de_DE", "Deutsch：1.234,56 € / 18.11.2026");
        locales.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println("  同一语言不同地区格式不同 -> 必须带地区");
        System.out.println();
    }

    static void fiveDimensions() {
        System.out.println("【五个必须本地化的维度】");
        String[][] rows = {
                {"文案", "ResourceBundle / 翻译文件", "70-02"},
                {"数字与货币", "NumberFormat / 货币符号位置", "70-05"},
                {"日期时间", "DateFormat / 时区", "70-04"},
                {"排序比较", "Collator / 语言排序规则", "70-06"},
                {"复数与性别", "复数规则 / 性别变化", "70-03"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-28s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    /** 演示：同一份数据在三个 Locale 下的呈现 */
    static void demo() {
        double amount = 1234.56;
        Date date = new Date(1_763_000_000_000L);
        Locale[] locales = {Locale.CHINA, Locale.US, Locale.GERMANY};
        System.out.println("【同一数据的三种呈现】");
        for (Locale l : locales) {
            NumberFormat nf = NumberFormat.getCurrencyInstance(l);
            DateFormat df = DateFormat.getDateInstance(DateFormat.DEFAULT, l);
            System.out.printf("  %-6s 金额=%-14s 日期=%s%n",
                    l.toString(), nf.format(amount), df.format(date));
        }
        System.out.println();
        System.out.println("【反例】在代码里拼句子");
        String name = "Alice";
        System.out.println("  \"Hello \" + name -> 日语应为「こんにちは、Alice さん」，德语应调整语序");
        System.out.println("  正解：\"greeting=こんにちは、{0}さん\" 占位符 + 翻译（70-02）");
    }

    public static void main(String[] args) {
        localeAnatomy();
        fiveDimensions();
        demo();
    }
}
```

**课后练习**
1. 用 `Locale` 与 `NumberFormat`/`DateFormat` 输出同一数据在 4 个地区的呈现，对比差异。
2. 检查项目里 5 处"代码拼句子"的写法，改造成占位符 + 资源文件的形式。
3. 思考题：为什么"只翻译文案"不足以支撑出海？从日期格式与货币位置的用户认知差异分析。

---

### 70-02 资源束与消息格式化

**知识点**
- **ResourceBundle 是文案抽离的标准机制**：`messages_zh_CN.properties`、`messages_en_US.properties`——**按 Locale 自动匹配**（找不到 `zh_CN` 会退化到 `zh` 再到默认）；**属性文件用 UTF-8**（老版本需 `native2ascii`，现在 JDK 9+ 默认 UTF-8）。
- **`MessageFormat` 解决占位符**：`"Hello, {0}! You have {1} new messages."`——**支持位置占位、数字/日期格式、复数选择**（`{1,number,integer}`）；**占位符序号比命名更常用**（兼容性更好）。
- **`ChoiceFormat` 处理区间**：`"no files | one file | {2} files"`——**按数值区间选文案**；更复杂用 ICU 的复数规则（70-03）。
- **文案治理的三条**：**key 命名规范**（`order.status.paid` 而非 `msg1`）、**禁止在代码里写字面量**（CI 扫描）、**翻译完整性校验**（缺 key 时构建失败，而不是运行期显示 `!key!`）。
- **与 69/55 章的关系**：多语言是 SaaS 多租户的常见需求（55-01 的租户配置可以指定语言）；广告/推荐的多语言内容也是同一套机制（69-01）。
- 易错点：用 `String.format` 代替 MessageFormat（不支持复数/选择性文案）；properties 编码错误（中文乱码）；缺失 key 静默显示 `!key!`；占位符序号错位（参数顺序搞反）。

**示例代码**
```java
import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

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
```

**课后练习**
1. 实现三语言资源束（zh/en/de），用 MessageFormat 渲染带占位符的文案，验证异常与缺失 key 处理。
2. 实现"翻译完整性校验器"：对比基准语言与目标语言的 key 集合，输出缺失与多余。
3. 思考题：为什么"代码里写字面量"是 i18n 的最大障碍？从"抽离成本"与"翻译流程"两个角度分析。

---

### 70-03 复数规则与性别

**知识点**
- **"1 个"和"2 个"在不同语言里规则不同**：英语 2 种（one/other）、中文 1 种、俄语 3 种（one/few/many）、阿拉伯语 6 种（zero/one/two/few/many/other）——**只写"单复数"会漏掉大部分语言**。
- **`PluralRules`（ICU/JDK 的 `java.text` 扩展）**：按 Locale + 数量选出类别（`one/few/many/other`），再查对应文案——**JDK 原生能力有限，复杂场景用 ICU4J**（本章用简化实现演示规则）。
- **复数规则的三种类型**：**基数（cardinal）**（数量：1 file / 2 files）、**序数（ordinal）**（第 1 / 第 2 / 第 3）、**范围（range）**（1-2 files）——**序数在日期/排名里必用**。
- **性别（gender）**：法语/德语/西班牙语的形容词与冠词随名词性别变化（`le/un` vs `la/une`）——**翻译系统要支持"带变量的句子片段"**，而不是整句替换（占位符插入时语法要正确）。
- **UI 文案的性别与敬语**：日语敬语层级（です/ます vs だ/である）、韩语敬语——**同一功能的文案在不同场景可能需要多版本**。
- 易错点：只用 if (n == 1) 判断单复数；中文翻译也套英文复数（中文没有复数）；序数用 `n + "th"`（1st/2nd/3rd 特殊）；忽略敬语层级。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PluralRulesDemo {
    /** 简化复数规则：语言 -> (数量分类函数) */
    static String pluralCategory(String lang, int n) {
        return switch (lang) {
            case "zh", "ja", "ko" -> "other";                     // 无复数变化
            case "en" -> n == 1 ? "one" : "other";
            case "ru" -> {
                int mod10 = n % 10;
                int mod100 = n % 100;
                if (mod10 == 1 && mod100 != 11) {
                    yield "one";
                }
                if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
                    yield "few";
                }
                yield "many";
            }
            case "ar" -> n == 0 ? "zero" : (n == 1 ? "one" : (n == 2 ? "two"
                    : (n % 100 >= 3 && n % 100 <= 10 ? "few" : "many")));
            default -> "other";
        };
    }

    static final Map<String, Map<String, String>> pluralTexts = new LinkedHashMap<>();

    static void initTexts() {
        Map<String, String> en = new LinkedHashMap<>();
        en.put("one", "{0} file");
        en.put("other", "{0} files");
        pluralTexts.put("en", en);

        Map<String, String> zh = new LinkedHashMap<>();
        zh.put("other", "{0} 个文件");
        pluralTexts.put("zh", zh);

        Map<String, String> ru = new LinkedHashMap<>();
        ru.put("one", "{0} файл");
        ru.put("few", "{0} файла");
        ru.put("many", "{0} файлов");
        pluralTexts.put("ru", ru);

        Map<String, String> ar = new LinkedHashMap<>();
        ar.put("zero", "لا ملفات");
        ar.put("one", "ملف واحد");
        ar.put("two", "ملفان");
        ar.put("few", "{0} ملفات");
        ar.put("many", "{0} ملفًا");
        pluralTexts.put("ar", ar);
    }

    static String render(String lang, int n) {
        String category = pluralCategory(lang, n);
        String template = pluralTexts.getOrDefault(lang, Map.of())
                .getOrDefault(category, "{0}");
        return template.replace("{0}", String.valueOf(n));
    }

    /** 序数：1st/2nd/3rd/nth（英语） */
    static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }

    public static void main(String[] args) {
        initTexts();
        System.out.println("【复数规则：同一数量在不同语言】");
        for (int n : new int[]{0, 1, 2, 3, 5, 11, 21, 22}) {
            System.out.printf("  n=%-2d en=%-12s zh=%-10s ru=%-12s ar=%s%n",
                    n, render("en", n), render("zh", n), render("ru", n), render("ar", n));
        }

        System.out.println();
        System.out.println("【序数规则】");
        List<Integer> ns = List.of(1, 2, 3, 4, 11, 12, 13, 21, 22, 23);
        StringBuilder sb = new StringBuilder("  ");
        ns.forEach(n -> sb.append(ordinal(n)).append(" "));
        System.out.println(sb);
        System.out.println("  注意 11/12/13 都是 th（不是 11st/12nd/13rd）");

        System.out.println();
        System.out.println("【性别与敬语】");
        System.out.println("  法语：un fichier（阳）/ une image（阴）-> 冠词随名词性别");
        System.out.println("  日语：です/ます（敬语）vs だ（简体）-> 同一功能可能需要两版文案");
        System.out.println("  做法：翻译片段而非整句 + 变量带语法信息（性别/数/敬语级）");

        System.out.println();
        System.out.println("【易错点】");
        System.out.println("  if (n == 1) 只适配英语；中文也套复数规则是多余；实现序数不能只看 n%10");
    }
}
```

**课后练习**
1. 实现多语言复数渲染（en/zh/ru/ar），用 8 个数量验证各语言分类正确。
2. 实现英语序数（含 11/12/13 特例）与"第 N 名"的中文文案，写 10 个用例验证。
3. 思考题：为什么"整句翻译"难以处理性别与敬语？从"变量插入后语法要正确"的角度分析。

---

### 70-04 时区与时间处理

**知识点**
- **时间的三层概念**：**瞬时（Instant）**（UTC 时间点，唯一）、**本地时间（LocalDateTime）**（无时区的墙上时间）、**带时区的时间（ZonedDateTime）**——**存储用 Instant/UTC，展示用带时区**，这是铁律。
- **时区的两大坑**：**夏令时（DST）**（某些时区一年切换两次，当天有 23 或 25 小时，`02:30` 可能不存在或出现两次）；**时区数据更新**（国家会改时区规则，JDK 的 tzdata 需要升级 35-08）。
- **`java.time` 的正确用法**：`Instant.now()`（当前瞬时时）、`ZonedDateTime.ofInstant(instant, zone)`（转本地展示）、`Duration.between`（时长）、**禁止用 `new Date()` 做计算**（老 API 混乱）。
- **跨时区的业务规则**：**"今天"的定义**（按用户时区还是服务器时区？）、**每日结算时间**（按哪个时区 00:00？）、**定时任务**（cron 按哪个时区？）——**必须显式声明**（61 章调度的时区问题）。
- **与 43/61 章的关系**：流处理的"事件时间"（43-02）与时区无关（都是 UTC 毫秒）；调度的 cron（61-02）必须指定时区，否则跨时区出事故。
- 易错点：用 `LocalDateTime` 存数据库（丢失时区信息）；用服务器默认时区格式化（不同机器不一致）；忽略 DST（定时任务在切换日跑错）；用 `new Date()` 加减天数（DST 下差一小时）。

**示例代码**
```java
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class TimeZoneDemo {
    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    static final ZoneId UTC = ZoneId.of("UTC");

    static void threeConcepts() {
        System.out.println("【三层时间概念】");
        Instant instant = Instant.ofEpochMilli(1_763_000_000_000L);
        LocalDateTime local = LocalDateTime.ofInstant(instant, SHANGHAI);
        ZonedDateTime zoned = ZonedDateTime.ofInstant(instant, NEW_YORK);
        System.out.printf("  Instant（UTC 瞬时）：%s%n", instant);
        System.out.printf("  LocalDateTime（上海墙上时间）：%s%n", local);
        System.out.printf("  ZonedDateTime（纽约）：%s%n", zoned);
        System.out.println("  存储用 Instant（UTC 毫秒），展示用 ZonedDateTime（按用户时区）");
        System.out.println();
    }

    static void crossZoneDisplay() {
        System.out.println("【同一瞬时在不同时区展示】");
        Instant instant = Instant.ofEpochMilli(1_763_000_000_000L);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        Map<String, ZoneId> zones = new LinkedHashMap<>();
        zones.put("上海", SHANGHAI);
        zones.put("纽约", NEW_YORK);
        zones.put("伦敦", ZoneId.of("Europe/London"));
        zones.put("东京", ZoneId.of("Asia/Tokyo"));
        zones.forEach((name, zone) -> System.out.printf("  %-4s %s%n",
                name, ZonedDateTime.ofInstant(instant, zone).format(fmt)));
        System.out.println("  用户看到的「今天」取决于他的时区 -> 业务规则必须显式声明");
        System.out.println();
    }

    static void dst() {
        System.out.println("【夏令时（DST）：纽约 2026 年 3 月切换日】");
        ZonedDateTime before = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, NEW_YORK);
        ZonedDateTime after = before.plusHours(1);
        System.out.printf("  切换前：%s%n", before);
        System.out.printf("  加 1 小时后：%s（时间跳到了 3:30，2 点不存在）%n", after);
        System.out.printf("  实际经过：%s%n", Duration.between(before, after));
        System.out.println("  教训：不要用「加减小时」做时间计算，用 Instant/Duration");

        System.out.println();
        System.out.println("【「今天」的定义】");
        Instant now = Instant.ofEpochMilli(1_763_000_000_000L);
        for (Map.Entry<String, ZoneId> e : Map.of("上海", SHANGHAI).entrySet()) {
            System.out.printf("  %s 的今天：%s%n", e.getKey(),
                    LocalDateTime.ofInstant(now, e.getValue()).toLocalDate());
        }
        System.out.printf("  UTC 的今天：%s%n", LocalDateTime.ofInstant(now, UTC).toLocalDate());
        System.out.println("  两者可能差一天 -> 日切/结算/定时任务必须指定时区（61-02）");
    }

    public static void main(String[] args) {
        threeConcepts();
        crossZoneDisplay();
        dst();
    }
}
```

**课后练习**
1. 实现跨时区时间展示：同一 Instant 在 4 个时区格式化，验证日期可能不同。
2. 复现 DST 事故：用 `LocalDateTime` 加减 24 小时，在 Nashville 切换日验证"实际不是 24 小时"。
3. 思考题：为什么"每日 0 点结算"必须指定时区？用"用户分布多时区"的场景说明歧义。

---

### 70-05 货币与数字格式

**知识点**
- **货币的三要素**：**金额（最小单位整数）** + **币种（ISO 4217 代码：CNY/USD/EUR/JPY）** + **展示格式（符号/位置/小数位）**——**金额绝不用浮点存**（14/51 章的分单位原则）。
- **各币种的小数位不同**：多数 2 位（CNY/USD/EUR），**日元 0 位**（JPY 无小数），**部分币种 3 位**（如科威特第纳尔）——**写死 2 位是常见 bug**。
- **`NumberFormat.getCurrencyInstance(Locale)`**：自动处理符号位置（`¥1,234.56` / `1.234,56 €`）、千分位分隔符（`1,234` vs `1.234`）、货币符号——**必须用 Locale 而非硬编码**。
- **数字格式的地域差异**：**千分位与小数点的符号相反**（美式 `1,234.56`，德式 `1.234,56`）；**印度式分组**（`12,34,567` 而非 `123,4567`）；**负号表示**（`-100` vs `(100)`，会计格式）——**不允许用字符串拼接**。
- **汇率与多币种结算**：**汇率是有时效的**（每天更新），**结算要记录"当时的汇率"**（不是用当前汇率重算历史）——**与 51 章的资金快照思想一致**。
- 易错点：金额用 double（精度丢失）；货币符号硬编码 `¥`（美元也用）；小数位写死 2 位（日元错）；汇率不存快照（历史账单对不上）。

**示例代码**
```java
import java.text.NumberFormat;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class CurrencyDemo {
    /** 金额：最小单位（分/日元）整数 + 币种 */
    record Money(long amountInMinorUnit, String currencyCode, int minorDigits) {
        String display(Locale locale) {
            NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
            Currency c = Currency.getInstance(currencyCode);
            nf.setCurrency(c);
            nf.setMaximumFractionDigits(c.getDefaultFractionDigits());
            nf.setMinimumFractionDigits(c.getDefaultFractionDigits());
            double major = amountInMinorUnit / Math.pow(10, minorDigits);
            return nf.format(major);
        }

        /** 展示给用户的最小单位换算（日元 0 位，人民币 2 位） */
        String raw() {
            return amountInMinorUnit + " " + currencyCode
                    + "（最小单位，小数位 " + minorDigits + "）";
        }
    }

    static void digitsMatter() {
        System.out.println("【不同币种的小数位】");
        Map<String, Integer> digits = new LinkedHashMap<>();
        digits.put("CNY", Currency.getInstance("CNY").getDefaultFractionDigits());
        digits.put("USD", Currency.getInstance("USD").getDefaultFractionDigits());
        digits.put("JPY", Currency.getInstance("JPY").getDefaultFractionDigits());
        digits.put("KWD", Currency.getInstance("KWD").getDefaultFractionDigits());
        digits.forEach((c, d) -> System.out.printf("  %-4s 小数位=%d%n", c, d));
        System.out.println("  写死 2 位 -> 日元显示 100.00（错），科威特丢精度");
        System.out.println();
    }

    static void displayAcrossLocales() {
        System.out.println("【同一金额在不同 Locale 的展示】");
        Money cny = new Money(123_456, "CNY", 2);      // 1234.56 元
        Money usd = new Money(123_456, "USD", 2);      // 1234.56 美元
        Money jpy = new Money(123_456, "JPY", 0);      // 123456 日元
        Locale[] locales = {Locale.CHINA, Locale.US, Locale.GERMANY};
        for (Locale l : locales) {
            System.out.printf("  %-6s CNY=%-14s USD=%-14s JPY=%s%n",
                    l, cny.display(l), usd.display(l), jpy.display(l));
        }
        System.out.println("  注意德式：1.234,56 €（千分位与小数点相反）");
        System.out.println();
        System.out.println("  原始存储（不含格式）：");
        System.out.println("    " + cny.raw());
        System.out.println("    " + jpy.raw());
    }

    static void exchangeRate() {
        System.out.println("【汇率快照：结算用历史汇率】");
        record Order(String id, long cnyCents, double rateAtOrder, String date) {
        }
        Order o = new Order("9001", 69_000, 7.14, "2026-11-18");
        double usd = o.cnyCents() / 100.0 / o.rateAtOrder();
        System.out.printf("  订单 %s：%.2f CNY，下单日汇率 %.2f -> %.2f USD%n",
                o.id(), o.cnyCents() / 100.0, o.rateAtOrder(), usd);
        System.out.println("  若用今日汇率 7.30 重算 -> 账单变化，历史对不上");
        System.out.println("  原则：汇率随订单落库快照（51-08 资金快照同源）");
    }

    public static void main(String[] args) {
        digitsMatter();
        displayAcrossLocales();
        exchangeRate();
    }
}
```

**课后练习**
1. 实现 Money 类型（最小单位 + 币种 + 小数位），在 3 个 Locale 下展示 CNY/USD/JPY/KWD。
2. 复现"日元写死 2 位小数"的 bug，展示给日本用户的错误显示并修复。
3. 思考题：为什么汇率必须随订单快照？从"历史账单可复现"与"多币种对账"分析。

---

### 70-06 排序与比较（Collator）

**知识点**
- **字符串的默认排序（`compareTo`）按 Unicode 码点**，**不等于语言排序**：中文按拼音还是笔画？德语 `ä` 排在 `a` 还是 `z`？瑞典语 `å` 在 `z` 之后——**用 `compareTo` 排序多语言列表会得到"看起来随机"的顺序**。
- **`Collator` 按语言规则排序**：`Collator.getInstance(Locale.CHINA)` 按拼音（或笔画，取决于 JDK 实现）、`Locale.GERMAN` 让 `ä` 等同 `a`、`Locale.SWEDISH` 让 `å` 在 `z` 后——**排序规则是语言文化的一部分**。
- **排序强度的三级**：**PRIMARY**（忽略大小写与重音：`a` = `A` = `á`）、**SECONDARY**（区分重音）、**TERTIARY**（区分大小写）——**列表排序通常用 PRIMARY，搜索匹配用更低强度**。
- **分解（Normalization）**：同一个字符有"预组合"（`é` 一个码点）与"组合"（`e` + 组合重音）两种表示——**排序/比较前要 `Normalizer.normalize(s, NFC)`**，否则相等字符串判不相等（35-xx Unicode 的坑）。
- **与 54/38 章的关系**：搜索的"前缀匹配/排序"在多语言下都要 Collator（70-10）；推荐列表的多语言排序同理。
- 易错点：用 `String.compareTo` 排多语言列表；不归一化就做字典序比较（同字不同编码）；排序强度选错（区分大小写导致 `Apple` 与 `apple` 不相等）；数据库排序规则与代码不一致。

**示例代码**
```java
import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CollationDemo {
    static void unicodeVsCollator() {
        List<String> words = new ArrayList<>(Arrays.asList(
                "apple", "Banana", "cherry", "Äpfel", "Zebra", "árbol"));
        List<String> byCodePoint = new ArrayList<>(words);
        byCodePoint.sort(Comparator.naturalOrder());
        List<String> byEnglish = new ArrayList<>(words);
        byEnglish.sort(Collator.getInstance(Locale.ENGLISH));
        List<String> byGerman = new ArrayList<>(words);
        byGerman.sort(Collator.getInstance(Locale.GERMAN));

        System.out.println("【同一批词的不同排序】");
        System.out.println("  原始：" + words);
        System.out.println("  compareTo（码点）：" + byCodePoint);
        System.out.println("  Collator(en)：    " + byEnglish);
        System.out.println("  Collator(de)：    " + byGerman);
        System.out.println("  差异：大写字母位置、Ä 与 A 的关系因语言而变");
        System.out.println();
    }

    static void strength() {
        System.out.println("【排序强度】");
        Collator c = Collator.getInstance(Locale.ENGLISH);
        String[][] pairs = {{"Apple", "apple"}, {"cafe", "café"}, {"a", "á"}};
        for (int s : new int[]{Collator.PRIMARY, Collator.SECONDARY, Collator.TERTIARY}) {
            c.setStrength(s);
            String label = s == Collator.PRIMARY ? "PRIMARY（忽略大小写与重音）"
                    : (s == Collator.SECONDARY ? "SECONDARY（区分重音）" : "TERTIARY（区分大小写）");
            StringBuilder sb = new StringBuilder();
            for (String[] p : pairs) {
                sb.append(p[0]).append(" vs ").append(p[1]).append("=")
                        .append(c.compare(p[0], p[1])).append("  ");
            }
            System.out.printf("  %-30s %s%n", label, sb);
        }
        System.out.println("  列表排序用 PRIMARY；登录名/唯一校验要 TERTIARY 或显式规则");
        System.out.println();
    }

    static void normalization() {
        String composed = "\u00e9";                  // é 单码点
        String decomposed = "e\u0301";               // e + 组合重音
        System.out.println("【归一化】");
        System.out.printf("  composed   = %s 长度=%d%n", composed, composed.length());
        System.out.printf("  decomposed = %s 长度=%d%n", decomposed, decomposed.length());
        System.out.println("  裸 equals：" + composed.equals(decomposed));
        String n1 = Normalizer.normalize(composed, Normalizer.Form.NFC);
        String n2 = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
        System.out.println("  NFC 后 equals：" + n1.equals(n2));
        System.out.println("  规则：比较/排序/存库前统一归一化（NFC）");
        System.out.println();
    }

    public static void main(String[] args) {
        unicodeVsCollator();
        strength();
        normalization();
        System.out.println("【实践建议】");
        System.out.println("  1) 展示排序：Collator.getInstance(userLocale)");
        System.out.println("  2) 数据库排序规则要与代码一致（否则分页错乱）");
        System.out.println("  3) 搜索/去重前先 NFC 归一化");
    }
}
```

**课后练习**
1. 用 Collator 对中英德三语混合列表排序，对比 `compareTo` 结果，说明差异原因。
2. 演示排序强度对"Apple/apple"与"cafe/café"比较结果的影响（三个强度各跑一次）。
3. 思考题：为什么"数据库排序规则与代码不一致"会导致分页错乱？从"两次查询的排序基准不同"分析。

---

### 70-07 翻译工作流与质量

**知识点**
- **翻译不是"把文件丢给翻译公司"**：完整流程是 **文案抽取 -> 翻译 -> 审校 -> 回填 -> 验证**——**每个环节都要有工具与校验**，否则漏翻/错翻/串行号。
- **文案抽取的两种方式**：**代码扫描**（正则找字面量，70-02 的 CI 检查）+ **资源文件导出**（从 properties/XLIFF 导出给翻译平台）——**业界标准格式是 XLIFF**（带上下文/状态/备注）。
- **翻译质量的三道关**：**完整性**（key 全覆盖）、**占位符一致性**（`{0}` 不能翻译丢）、**长度适配**（德语长 30%，UI 要能容纳）——**前两项可自动化，第三项靠 UI 测试**。
- **上下文缺失是最大质量杀手**：`"Close"` 是"关闭"还是"靠近"？**给翻译者上下文**（界面截图/字段说明）——**翻译平台要支持 context 字段**。
- **持续本地化（continuous localization）**：文案变更自动同步到翻译平台 -> 翻译完成自动回填 -> CI 校验完整性——**与 41 章流水线结合**。
- 易错点：占位符被翻译（`{0}` 变 `{0 }`）；漏翻（缺 key 显示原文或 `!key!`）；长度溢出（UI 截断）；无上下文（"Close"翻错）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslationWorkflow {
    record Entry(String key, String source, String target, String context) {
    }

    /** 校验一：完整性（target 是否缺失） */
    static List<String> checkCompleteness(List<Entry> entries) {
        List<String> issues = new ArrayList<>();
        for (Entry e : entries) {
            if (e.target() == null || e.target().isBlank()) {
                issues.add("缺翻译：" + e.key());
            }
        }
        return issues;
    }

    /** 校验二：占位符一致性 */
    static List<String> checkPlaceholders(List<Entry> entries) {
        Pattern p = Pattern.compile("\\{\\d+}");
        List<String> issues = new ArrayList<>();
        for (Entry e : entries) {
            if (e.target() == null) {
                continue;
            }
            List<String> src = new ArrayList<>();
            Matcher m1 = p.matcher(e.source());
            while (m1.find()) {
                src.add(m1.group());
            }
            List<String> tgt = new ArrayList<>();
            Matcher m2 = p.matcher(e.target());
            while (m2.find()) {
                tgt.add(m2.group());
            }
            if (!src.equals(tgt)) {
                issues.add("占位符不一致：" + e.key() + " 原文=" + src + " 译文=" + tgt);
            }
        }
        return issues;
    }

    /** 校验三：长度膨胀（德语/俄语通常更长） */
    static List<String> checkLength(List<Entry> entries, double maxRatio) {
        List<String> issues = new ArrayList<>();
        for (Entry e : entries) {
            if (e.target() == null || e.source().isEmpty()) {
                continue;
            }
            double ratio = e.target().length() / (double) e.source().length();
            if (ratio > maxRatio) {
                issues.add(String.format("长度超限：%s 膨胀 %.0f%%（阈值 %.0f%%）",
                        e.key(), (ratio - 1) * 100, (maxRatio - 1) * 100));
            }
        }
        return issues;
    }

    static void workflow() {
        System.out.println("【翻译工作流五步】");
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put("抽取", "扫描字面量 + 导出资源文件（XLIFF，带 context）");
        steps.put("翻译", "翻译平台；提供界面截图/字段说明作为上下文");
        steps.put("审校", "母语审校；重点检查术语一致性与语气");
        steps.put("回填", "自动回填资源文件（按 key 对应）");
        steps.put("验证", "CI 校验：完整性 + 占位符 + 长度；UI 走查");
        steps.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println();
    }

    public static void main(String[] args) {
        List<Entry> entries = List.of(
                new Entry("order.paid", "Order {0} paid: {1}", "订单 {0} 已支付：{1}",
                        "订单详情页标题"),
                new Entry("btn.close", "Close", "关闭", "对话框按钮"),
                new Entry("msg.hello", "Hello, {0}!", "こんにちは、{0}さん", "首页问候"),
                new Entry("msg.missing", "Welcome back", null, "登录后提示"),
                new Entry("btn.submit", "Submit", "提交", "表单按钮"),
                new Entry("err.timeout", "Request timed out, please retry", "请求超时，请重试",
                        "错误提示"),
                new Entry("label.long", "Settings", "Einstellungen und Benutzereinstellungen",
                        "设置入口"));

        System.out.println("【校验结果】");
        List<String> c1 = checkCompleteness(entries);
        List<String> c2 = checkPlaceholders(entries);
        List<String> c3 = checkLength(entries, 2.0);

        System.out.println("  完整性：" + (c1.isEmpty() ? "通过" : c1));
        System.out.println("  占位符：" + (c2.isEmpty() ? "通过" : c2));
        System.out.println("  长度：" + (c3.isEmpty() ? "通过" : c3));

        System.out.println();
        System.out.println("【占位符错误示例】把 {0} 误译成 {O}（字母 O）");
        Entry broken = new Entry("x", "Hello {0}", "你好 {O}", "测试");
        System.out.println("  " + checkPlaceholders(List.of(broken)));

        System.out.println();
        workflow();
        System.out.println("  最大质量杀手：无上下文（Close = 关闭 还是 靠近？）");
    }
}
```

**课后练习**
1. 实现三项校验器（完整性/占位符/长度），用 7 条翻译数据验证能检出全部问题。
2. 设计"翻译上下文"字段规范（页面/元素类型/长度上限/术语表），并给 5 个易错词写出上下文。
3. 思考题：为什么"漏翻"应该在 CI 阶段拦截而不是运行期兜底？从"用户可见的 `!key!`"与"构建成本"分析。

---

### 70-08 前端与多端一致性

**知识点**
- **多端一致性的三个层面**：**文案一致**（同一 key 在各端翻译相同）、**格式一致**（日期/货币/数字格式相同）、**时区一致**（同一瞬时在各端显示的时间）——**后端返回"数据 + Locale"比返回"已格式化字符串"更可靠**（各端本地格式化）。
- **两种架构选择的权衡**：**后端格式化**（返回 `$1,234.56`，各端直接展示——一致但无法按端适配）；**前端格式化**（返回 `1234.56 + USD`，各端用本地库格式化——灵活但要保证库行为一致）。
- **推荐：后端返回结构化数据 + 前端按 Locale 格式化**；**唯一例外**是"服务端生成的 PDF/邮件/推送文案"（必须后端格式化，因为不经过客户端）。
- **兜底与降级**：`Accept-Language` 协商（客户端偏好 -> 服务端匹配 -> 兜底默认语言）；**语言切换要清除缓存**（否则看到旧语言 55-01 的缓存键）。
- **与 55 章的关系**：多租户 SaaS 的租户可配置默认语言（55-06），用户可覆盖（用户级配置）——**语言也是配置分层的一层**。
- 易错点：后端返回已格式化的字符串（前端无法按用户 Locale 调整）；时区用服务器默认（不同机房显示不同）；语言切换不清缓存；`Accept-Language` 解析错误（q 权重）。

**示例代码**
```java
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MultiPlatformI18n {
    /** 后端返回的结构化数据（不预格式化） */
    record MoneyDto(long amountInMinorUnit, String currency, int minorDigits) {
    }

    record ApiResponse(MoneyDto amount, long timestampMs, String languageHint) {
    }

    /** 前端按 Locale 格式化 */
    static String formatForClient(ApiResponse resp, Locale clientLocale) {
        MoneyDto m = resp.amount();
        NumberFormat nf = NumberFormat.getCurrencyInstance(clientLocale);
        nf.setCurrency(java.util.Currency.getInstance(m.currency()));
        nf.setMaximumFractionDigits(m.minorDigits());
        nf.setMinimumFractionDigits(m.minorDigits());
        double major = m.amountInMinorUnit() / Math.pow(10, m.minorDigits());
        return nf.format(major);
    }

    /** Accept-Language 协商：解析 q 权重 */
    static Locale negotiate(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.US;
        }
        String best = null;
        double bestQ = -1;
        for (String part : acceptLanguage.split(",")) {
            String[] tokens = part.trim().split(";");
            String tag = tokens[0].trim();
            double q = 1.0;
            if (tokens.length > 1 && tokens[1].contains("q=")) {
                try {
                    q = Double.parseDouble(tokens[1].split("q=")[1].trim());
                } catch (NumberFormatException e) {
                    q = 0;
                }
            }
            if (q > bestQ) {
                bestQ = q;
                best = tag;
            }
        }
        if (best == null) {
            return Locale.US;
        }
        return best.contains("-")
                ? Locale.forLanguageTag(best)
                : new Locale(best);
    }

    public static void main(String[] args) {
        System.out.println("【后端返回结构化数据，前端按 Locale 格式化】");
        ApiResponse resp = new ApiResponse(new MoneyDto(123_456, "USD", 2),
                1_763_000_000_000L, "en");
        for (Locale l : new Locale[]{Locale.US, Locale.GERMANY, Locale.JAPAN}) {
            System.out.printf("  %-6s -> %s%n", l, formatForClient(resp, l));
        }

        System.out.println();
        System.out.println("【Accept-Language 协商】");
        String[] headers = {
                "zh-CN,zh;q=0.9,en;q=0.8",
                "en-US,en;q=0.9",
                "fr;q=0.5,de;q=0.8",
                ""
        };
        for (String h : headers) {
            System.out.printf("  %-32s -> %s%n", "[" + h + "]", negotiate(h));
        }

        System.out.println();
        System.out.println("【语言切换与缓存】");
        Map<String, String> cache = new LinkedHashMap<>();
        cache.put("u-1@zh_CN@order:9001", "订单已支付");
        cache.put("u-1@en_US@order:9001", "Order paid");
        System.out.println("  缓存键必须含语言：" + cache.keySet());
        System.out.println("  切换语言 -> 缓存键变 -> 自然读到新语言（无需清理）");

        System.out.println();
        System.out.println("【两种架构取舍】");
        List<String> notes = new ArrayList<>();
        notes.add("后端格式化：一致但无法按端调整（已牺牲灵活性）");
        notes.add("前端格式化：灵活但要保证各端库行为一致");
        notes.add("服务端产物（PDF/邮件/推送）必须后端格式化（不经过客户端）");
        notes.forEach(n -> System.out.println("  - " + n));
    }
}
```

**课后练习**
1. 实现 Accept-Language 解析（含 q 权重），用 4 组 header 验证协商结果。
2. 对比"后端格式化"与"前端格式化"两种 API 设计，各写一个响应示例并说明适用场景。
3. 思考题：为什么"缓存键必须含语言"？构造"切换语言后看到旧文案"的用例并给出修复方案。

---

### 70-09 测试：伪本地化与回归

**知识点**
- **伪本地化（Pseudo-localization）是 i18n 测试的核心手段**：把英文文案自动"伪翻译"成 `[Ĥéļļö ŵöŕļð !!!]`（加特殊字符 + 拉长 30%）——**一眼看出"哪里没被抽离""哪里布局会溢出"**，不需要等真实翻译。
- **伪本地化的三个检查**：**硬编码检测**（没被包裹的文案仍是英文）、**布局溢出**（加长后 UI 截断）、**字符渲染**（非 ASCII 字符的字体缺失）。
- **回归测试的三层**：**UI 走查**（截图对比各语言）、**功能回归**（关键流程在多语言下跑通）、**文案比对**（翻译完整性 CI 70-07）——**前两项靠人工/截图，第三项自动化**。
- **假语言（pseudolocale）与真实语言的差异**：伪本地化只能测"格式与布局"，**测不出翻译质量**（准确性/语气）——**质量仍要母语审校**（70-07）。
- **与 33/41 章的关系**：i18n 测试进 CI（翻译完整性）+ 截图回归（视觉对比）+ 多语言环境回归（41-03 的测试矩阵扩展）。
- 易错点：只测英文（其他语言上线才发现问题）；伪本地化只加长不检查字符（emoji/RTL 遗漏）；UI 截图回归不做（布局溢出无人知）；翻译质量靠机器翻译不审校。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class I18nTesting {
    static final String ACCENT = "áéíóúàèìòùâêîôûäëïöüñç";
    static final String ACCENT_UPPER = "ÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÄËÏÖÜÑÇ";

    /** 伪翻译：字符替换（保持 ASCII 之外的全角化） + 拉长 30% */
    static String pseudo(String source) {
        StringBuilder sb = new StringBuilder("[");
        boolean upper = true;
        for (char c : source.toCharArray()) {
            if (Character.isLetter(c)) {
                int idx = (c | 0x20) - 'a';
                if (idx >= 0 && idx < ACCENT.length()) {
                    char rep = upper && Character.isUpperCase(c)
                            ? ACCENT_UPPER.charAt(idx) : ACCENT.charAt(idx);
                    sb.append(rep);
                } else {
                    sb.append(c);
                }
                upper = false;
            } else {
                sb.append(c);
                upper = true;
            }
        }
        int pad = (int) Math.ceil(source.length() * 0.3);
        for (int i = 0; i < pad; i++) {
            sb.append('·');
        }
        return sb.append("]").toString();
    }

    /** 硬编码检测：伪翻译后仍是原英文 -> 说明没被抽离 */
    static List<String> detectHardcoded(List<String> rawTexts) {
        List<String> hardcoded = new ArrayList<>();
        for (String raw : rawTexts) {
            String p = pseudo(raw);
            // 简化的判定：若伪翻译结果中字母占比过高且与原文长度接近，视为"未生效"
            long letters = p.chars().filter(Character::isLetter).count();
            if (letters > raw.length() * 0.8) {
                hardcoded.add(raw);
            }
        }
        return hardcoded;
    }

    /** 布局溢出检测：伪翻译后长度超过容器上限 */
    static List<String> detectOverflow(Map<String, String> texts, int maxWidth) {
        List<String> overflow = new ArrayList<>();
        texts.forEach((key, text) -> {
            String p = pseudo(text);
            if (p.length() > maxWidth) {
                overflow.add(key + " 伪翻译后 " + p.length() + " > 上限 " + maxWidth);
            }
        });
        return overflow;
    }

    public static void main(String[] args) {
        System.out.println("【伪本地化示例】");
        List<String> texts = List.of(
                "Settings", "Order paid", "Close", "Submit", "Welcome back");
        texts.forEach(t -> System.out.printf("  %-14s -> %s%n", t, pseudo(t)));

        System.out.println();
        System.out.println("【硬编码检测】");
        List<String> uiTexts = new ArrayList<>(texts);
        uiTexts.add("Hello");                          // 未抽离（假设）
        List<String> hard = detectHardcoded(uiTexts);
        System.out.println("  疑似硬编码（伪翻译未生效）：" + hard);
        System.out.println("  实际做法：所有 UI 文案都走资源束，伪翻译后必然变形");

        System.out.println();
        System.out.println("【布局溢出检测（容器宽 18 字符）】");
        Map<String, String> keyed = new LinkedHashMap<>();
        keyed.put("btn.close", "Close");
        keyed.put("label.settings", "Settings");
        keyed.put("msg.long", "Please check your network connection");
        detectOverflow(keyed, 18).forEach(o -> System.out.println("  " + o));

        System.out.println();
        System.out.println("【i18n 测试三层】");
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("自动化", "翻译完整性 + 占位符校验（70-07，进 CI）");
        layers.put("伪本地化", "硬编码/溢出/字符渲染（本章）");
        layers.put("人工走查", "多语言截图对比 + 关键流程回归");
        layers.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println();
        System.out.println("  伪本地化测不了翻译质量（准确性/语气）-> 仍需母语审校");
    }
}
```

**课后练习**
1. 实现伪翻译器（字符变形 + 拉长 30%）与硬编码检测，用 6 条文案验证。
2. 实现布局溢出检测：给定 5 条文案与容器宽度，输出会溢出的 key 清单。
3. 思考题：为什么"伪本地化不能替代母语审校"？从"格式问题"与"语义/语气问题"的分工分析。

---

### 70-10 多语言下的搜索与匹配

**知识点**
- **多语言搜索的四个难题**：**分词**（中文/日文无空格，英文按空格 38-03）、**大小写与重音**（`Café` 应匹配 `cafe`）、**同义词与词形还原**（`running` 匹配 `run`）、**排序与高亮**（Collator 70-06）。
- **查询侧与分析侧的归一化**：**存入时归一化**（NFC 70-06 + 小写 + 去重音）**与查询时同样归一化**——**两边不一致就搜不到**（38-02 的 analyzer 不一致问题）。
- **多字段策略**：同一字段存两份——**原始字段**（展示用，保留重音与大小写）+ **归一化字段**（搜索用，小写去重音）——**用 `analyzer` 的 normalizer 或应用层处理**。
- **跨语言同义词**：`手机` / `mobile` / `smartphone` 映射到同一概念——**需要维护同义词表**（38-03 的自定义词典）；**语言识别**决定用哪套分词与同义词（70-08 的 Accept-Language）。
- **与 38 章的关系**：38 章讲倒排与分词，本章讲"多语言场景下的分词与归一化差异"——**技术相同，语言适配不同**。
- 易错点：只对查询归一化不对索引归一化（搜不到）；重音敏感导致 `café` 搜不到 `cafe`；中文用英文分词器（逐字切）；跨语言同义词缺失。

**示例代码**
```java
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultilingualSearch {
    /** 归一化：NFC + 小写 + 去重音（NFC 分解后去掉组合字符） */
    static String normalize(String text) {
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (char c : nfc.toCharArray()) {
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase();
    }

    /** 语言识别（简化：按字符集占比） */
    static String detectLanguage(String text) {
        long cjk = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long cyrillic = text.chars().filter(c -> c >= 0x0400 && c <= 0x04FF).count();
        if (cjk > 0) {
            return "zh";
        }
        if (cyrillic > 0) {
            return "ru";
        }
        return "en";
    }

    /** 分词：中文按二元切分（简化），英文按空格 */
    static List<String> tokenize(String text, String lang) {
        List<String> tokens = new ArrayList<>();
        if (lang.equals("zh")) {
            for (int i = 0; i < text.length() - 1; i++) {
                tokens.add(text.substring(i, i + 2));
            }
        } else {
            for (String t : text.split("\\s+")) {
                if (!t.isBlank()) {
                    tokens.add(t);
                }
            }
        }
        return tokens;
    }

    /** 同义词表：跨语言映射到统一概念 */
    static final Map<String, String> SYNONYMS = new LinkedHashMap<>();

    static {
        SYNONYMS.put("手机", "phone");
        SYNONYMS.put("mobile", "phone");
        SYNONYMS.put("smartphone", "phone");
        SYNONYMS.put("手提电话", "phone");
        SYNONYMS.put("cafe", "cafe");
        SYNONYMS.put("coffee shop", "cafe");
    }

    static void index(Map<String, List<String>> index, String docId, String text) {
        String lang = detectLanguage(text);
        String norm = normalize(text);
        for (String token : tokenize(norm, lang)) {
            String concept = SYNONYMS.getOrDefault(token, token);
            index.computeIfAbsent(concept, k -> new ArrayList<>()).add(docId);
        }
    }

    static Set<String> search(Map<String, List<String>> index, String query) {
        String lang = detectLanguage(query);
        String norm = normalize(query);
        Set<String> hits = new LinkedHashSet<>();
        for (String token : tokenize(norm, lang)) {
            String concept = SYNONYMS.getOrDefault(token, token);
            hits.addAll(index.getOrDefault(concept, List.of()));
        }
        return hits;
    }

    public static void main(String[] args) {
        System.out.println("【归一化】");
        for (String s : new String[]{"Café", "CAFÉ", "cafe"}) {
            System.out.printf("  %-6s -> %s%n", s, normalize(s));
        }

        System.out.println();
        System.out.println("【语言识别与分词】");
        String[] samples = {"手机壳", "Mobile case", "телефон"};
        for (String s : samples) {
            String lang = detectLanguage(s);
            System.out.printf("  %-14s lang=%-3s tokens=%s%n", s, lang,
                    tokenize(normalize(s), lang));
        }

        System.out.println();
        Map<String, List<String>> index = new LinkedHashMap<>();
        index(index, "d1", "苹果 手机 壳");
        index(index, "d2", "Mobile phone case");
        index(index, "d3", "smartphone 保护套");
        index(index, "d4", "Café latte");
        System.out.println("【倒排索引】");
        index.forEach((k, v) -> System.out.printf("  %-10s -> %s%n", k, v));

        System.out.println();
        System.out.println("【跨语言查询】");
        for (String q : new String[]{"手机", "phone", "mobile", "CAFE", "café"}) {
            System.out.printf("  查询 %-8s -> 命中 %s%n", q, search(index, q));
        }

        System.out.println();
        System.out.println("  要点：索引与查询用同一套归一化 + 分词 + 同义词（38-02 的 analyzer 一致性）");
    }
}
```

**课后练习**
1. 实现归一化 + 语言识别 + 分词 + 同义词索引，用中英混合文档验证跨语言查询命中。
2. 复现"只归一化查询不归一化索引"导致的搜不到，修复后验证 `Café` 能搜到 `cafe`。
3. 思考题：为什么"中文按二元切分"是简化的可用方案？对比词典分词（38-03）的召回与精度差异。

---

### 70-11 综合案例：出海电商多语言改造

**知识点**
- **把本章收口成一次改造**：**文案抽离**（70-02）-> **格式本地化**（货币 70-05/日期 70-04）-> **排序适配**（70-06）-> **搜索归一化**（70-10）-> **翻译工作流**（70-07）-> **多端一致**（70-08）-> **测试**（70-09）。
- **改造的顺序很重要**：**先抽离**（否则后面都白做）-> **再格式化**（显示正确）-> **再搜索**（用户能找到）-> **最后测试与流程**（持续可维护）——**抽离是地基**（70-02）。
- **验收清单**：文案无硬编码、金额按币种小数位、日期带时区、排序用 Collator、搜索归一化、翻译完整、伪本地化无溢出、缓存键含语言。
- **常见返工点**：UI 文案长度溢出（德语）、货币小数位写死、时区用服务器默认、搜索重音不匹配——**这四项占 i18n bug 的大半**（70-05/06/10）。
- 易错点：只改后端不改前端（前端硬编码文案）；只测英文；货币与日期格式混用（`¥1,234.56` 出现在德语页）；上线后才发现 RTL 没适配。

**示例代码**
```java
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
```

**课后练习**
1. 端到端实现多语言商品展示（价格/日期/排序），在 CNY/USD/JPY 三个站点验证各项本地化正确。
2. 为你的项目做一次 i18n 体检：按 8 项验收清单逐项打分，列出返工优先级。
3. 思考题：为什么"抽离"必须最先做？从"后续所有工作都依赖文案可配置"的角度分析。
