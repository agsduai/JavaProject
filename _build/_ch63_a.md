### 63-01 维度建模总览：从业务过程到星型模型

**知识点**
- **维度建模的出发点不是"表结构"而是"业务过程"**：先问"业务上发生了什么事"（下单、支付、退款、发货），再为每个过程建一张**事实表**（度量）和一组**维度表**（描述）——**这是与"第三范式建模"的根本差异**（范式面向"无冗余存储"，维度建模面向"分析查询快"）。
- **星型模型的结构**：中心是**事实表**（外键 + 度量），周围是**维度表**（描述性属性）——**一个事实表关联多个维度**，形状像星星；**雪花模型**是把维度再规范化拆成多层（63-07 权衡）。
- **事实 vs 维度的判据**：**"能用数字累加/求平均的是事实，用来描述'谁在何时何地做了什么'的是维度"**——金额、数量、时长是事实；用户、商品、日期、渠道是维度。
- **粒度是建模的第一决策**（63-02 第二步）：一张事实表只表达**一个粒度**（"每个订单一行"还是"每个订单每个商品一行"）——**不同粒度不能混在一张事实表**（会导致度量重复计算）。
- **与 46 章分层的关系**：46-01 的 DWD/DWS/ADS 是"数据流的分层"，维度建模是"每层内部怎么组织表"——**DWD 常按维度建模建事实+维度，DWS 按主题做聚合**。
- 易错点：把业务库表直接当维度模型（OLTP 范式表 join 复杂、查询慢）；一张事实表混多个粒度（汇总翻倍）；维度表里放度量（无法聚合）；没有日期维度（分析必备）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DimensionalOverview {
    record Dimension(String name, List<String> attributes) {
    }

    record FactTable(String name, List<String> foreignKeys, List<String> measures,
                     String grain) {
    }

    static void starSchema() {
        System.out.println("【订单主题星型模型】");
        FactTable fact = new FactTable("fact_order_item",
                List.of("date_key", "user_key", "product_key", "channel_key", "order_id"),
                List.of("quantity", "amount", "discount_amount", "cost_amount"),
                "每个订单的每个商品一行");
        System.out.printf("  事实表：%s%n    粒度：%s%n    外键：%s%n    度量：%s%n",
                fact.name(), fact.grain(), fact.foreignKeys(), fact.measures());

        List<Dimension> dims = List.of(
                new Dimension("dim_date", List.of("date_key", "year", "month", "week", "is_holiday")),
                new Dimension("dim_user", List.of("user_key", "level", "city", "register_date")),
                new Dimension("dim_product", List.of("product_key", "category", "brand", "price_band")),
                new Dimension("dim_channel", List.of("channel_key", "channel_name", "is_online")));
        System.out.println("  维度表：");
        for (Dimension d : dims) {
            System.out.printf("    %-14s 属性=%s%n", d.name(), d.attributes());
        }
        System.out.println("  形状：事实表居中，四个维度环绕（星型）");
        System.out.println();
    }

    static void factVsDimension() {
        System.out.println("【事实与维度的判据】");
        String[][] rows = {
                {"金额/数量/时长", "事实（可累加/可平均）", "度量"},
                {"用户/商品/渠道", "维度（描述谁/什么/哪里）", "外键 + 属性"},
                {"订单号", "退化维度（无独立维度表，放事实表）", "63-03"},
                {"状态", "维度（可枚举，做状态维度表）", "便于按状态切片"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-28s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        starSchema();
        factVsDimension();
    }
}
```

**课后练习**
1. 选一个业务过程（下单/支付/发货），画出星型模型：事实表（粒度+度量）与 4 个维度表（属性）。
2. 判断 10 个字段"是事实还是维度"，对边界字段（状态、订单号）说明处理方式。
3. 思考题：为什么"一张事实表只能有一个粒度"？用"订单粒度 + 商品粒度混表"导致金额重复计算的例子说明。

---

### 63-02 四步建模法

**知识点**
- **Kimball 四步法**：① **选择业务过程**（订单/支付/退款——不是"部门"也不是"报表"）；② **声明粒度**（每一行代表什么：一个订单？一个订单商品？一次支付尝试？）；③ **确定维度**（谁/什么/何时/何地/如何——对应 63-01 的维度）；④ **确定事实**（可加性度量 + 不可加性度量）。
- **粒度的三层选择**：**事务粒度**（每次业务事件一行，最细，最灵活）、**周期快照**（每天/每月一行，便于趋势）、**累积快照**（一个订单一行，随状态更新）——**粒度越细越灵活但数据量越大**（63-06 三种事实表）。
- **"声明粒度"是最容易做错的一步**：报告说"按订单统计"不代表事实表就按订单粒度——**要先问"能否拆到更细且业务仍成立"**（订单商品粒度能拆出订单级，反之不行）；**粒度定错的代价是重建事实表**。
- **维度确定要覆盖"分析会怎么切"**：按城市/品类/渠道/时间切片——**维度是查询的"切片方向"**；漏一个维度就要改表（49-10 演进成本）。
- **事实的三种可加性**：**可加**（金额，可跨所有维度求和）、**半可加**（余额，可跨维度但不能跨时间累加）、**不可加**（比率、单价，必须用分子分母分别聚合再计算）——**比率类指标存分子分母，不存比率**（63-09）。
- 易错点：跳过粒度声明直接建表（改需求就重建）；把比率存成事实（63-09 的坑）；维度只按当前报表需要设计（漏维度）；事实表放"订单总金额"又在商品粒度（重复计算）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FourStepModeling {
    record Step(int no, String name, String question, String example) {
    }

    static void fourSteps() {
        System.out.println("【四步法：以订单过程为例】");
        List<Step> steps = List.of(
                new Step(1, "选择业务过程", "业务上真实发生了什么事？", "下单（OrderPlaced）"),
                new Step(2, "声明粒度", "每一行代表什么？", "一个订单的一个商品（order_item）"),
                new Step(3, "确定维度", "分析会按什么切？", "日期/用户/商品/渠道/门店"),
                new Step(4, "确定事实", "每次事件的度量是什么？", "数量/金额/优惠/成本"));
        for (Step s : steps) {
            System.out.printf("  第%d步 %-12s %-24s 示例：%s%n",
                    s.no(), s.name(), s.question(), s.example());
        }
        System.out.println();
    }

    static void grainChoice() {
        System.out.println("【同一过程的不同粒度】");
        Map<String, String> grains = new LinkedHashMap<>();
        grains.put("订单粒度", "一个订单一行：金额=订单总额，无法按商品分析");
        grains.put("订单商品粒度", "一个订单一个商品一行：可汇总出订单级，也能按商品分析（推荐）");
        grains.put("支付尝试粒度", "一次支付尝试一行：可分析失败重试，但金额会重复（一笔订单多次尝试）");
        grains.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
        System.out.println();
        System.out.println("  原则：业务允许的前提下，粒度越细越灵活；但不同粒度必须分表");
    }

    static void additivity() {
        System.out.println("【度量的三种可加性】");
        String[][] rows = {
                {"金额/数量", "可加", "跨所有维度求和都正确"},
                {"账户余额", "半可加", "可跨维度，不能跨时间累加（会重复计余额）"},
                {"客单价/转化率", "不可加", "存分子与分母，查询时再计算"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-6s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  反模式：把客单价（金额/订单数）存成事实 -> 汇总时平均值再平均（错）");
    }

    public static void main(String[] args) {
        fourSteps();
        grainChoice();
        additivity();
    }
}
```

**课后练习**
1. 对"退款"过程走一遍四步法，输出粒度声明与事实表设计，并说明"为什么退款金额要与订单金额分表"。
2. 用同一份订单数据分别建"订单粒度"与"订单商品粒度"两张事实表，验证"商品粒度可汇总出订单级、反之不可"。
3. 思考题：为什么"比率类指标存分子分母"？用一个"平均客单价错误汇总"的算例说明。

---

### 63-03 维度表设计：代理键与退化维度

**知识点**
- **代理键（Surrogate Key）是无业务含义的自增整数**：维度表用 `product_key` 而不是 `product_id`（业务主键）——**代理键让维度可以保留历史版本**（同一商品多次变更 -> 多个代理键，63-04），业务主键仍然保留为普通属性。
- **代理键的生成与稳定性**：数仓内部统一生成（自增/哈希），**不依赖业务库的 id**（业务库可能分库分表 39 章、id 可能复用）；代理键一旦分配**永不复用**。
- **退化维度（Degenerate Dimension）**：没有独立维度表的维度（订单号、支付流水号）——**直接放在事实表里**，因为它没有描述属性、只用于"精确查找某笔单据"。
- **维度表的其他常见属性**：**自然键**（业务 id）、**维度属性**（名称/类目/等级）、**管理属性**（`etl_time`/`is_current`/`version`）、**默认成员**（`-1` 未知/`0` 占位，处理 NULL 外键）。
- **维度表的"宽"与"稳定"**：维度表通常**宽而稳定**（几百列、变化慢）；**事实表窄而长**（几列 + 海量行）——**这是维度建模的性能来源**（宽表减少 join、事实表小而快）。
- 易错点：用业务 id 当代理键（历史版本冲突）；NULL 外键直接入事实表（关联不上，需默认成员）；退化维度硬造维度表（无意义的空表）；维度表频繁更新属性（未用 SCD 63-04）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DimensionDesign {
    /** 维度行：代理键 + 自然键 + 属性 + 管理字段 */
    record ProductDim(int productKey, String productId, String name, String category,
                      String brand, boolean isCurrent) {
    }

    /** 事实行：外键（代理键）+ 退化维度 + 度量 */
    record OrderItemFact(long orderId, int dateKey, int userKey, int productKey,
                         int quantity, long amountCents) {
    }

    static void surrogateKey() {
        System.out.println("【代理键与历史版本】");
        List<ProductDim> dim = new ArrayList<>();
        dim.add(new ProductDim(1001, "P-100", "手机壳 A", "配件", "自有", false));
        dim.add(new ProductDim(1002, "P-100", "手机壳 A（改名）", "配件", "自有", true));
        dim.add(new ProductDim(1003, "P-200", "蓝牙耳机", "音频", "第三方", true));
        dim.forEach(d -> System.out.printf("  key=%-5d id=%-6s name=%-12s current=%b%n",
                d.productKey(), d.productId(), d.name(), d.isCurrent()));
        System.out.println("  同一商品 P-100 有两个代理键 -> 历史事实仍指向旧版本（可追溯）");
        System.out.println();
    }

    static void degenerate() {
        System.out.println("【退化维度：无独立维度表的维度】");
        List<OrderItemFact> facts = List.of(
                new OrderItemFact(9001, 20261118, 501, 1002, 2, 13800),
                new OrderItemFact(9002, 20261118, 502, 1003, 1, 29900));
        facts.forEach(f -> System.out.printf("  orderId=%-5d date=%-9d user=%-4d product=%-5d 数量=%d 金额=%d分%n",
                f.orderId(), f.dateKey(), f.userKey(), f.productKey(), f.quantity(), f.amountCents()));
        System.out.println("  订单号直接放事实表（无描述属性、只用于定位单据）");
        System.out.println();
    }

    static void defaultMember() {
        System.out.println("【默认成员：处理 NULL 外键】");
        Map<Integer, String> dim = new LinkedHashMap<>();
        dim.put(-1, "未知（来源缺失）");
        dim.put(0, "占位（不适用）");
        dim.put(1002, "手机壳 A（改名）");
        long nullFkRows = 3;
        long totalRows = 100;
        System.out.printf("  维度表含默认成员：%s%n", dim);
        System.out.printf("  外键为 NULL 的事实行 %d/%d -> 关联到 key=-1，统计不丢行%n",
                nullFkRows, totalRows);
        System.out.println("  反模式：内连接维度表会丢掉 NULL 外键的行（汇总对不上）");
    }

    public static void main(String[] args) {
        surrogateKey();
        degenerate();
        defaultMember();
    }
}
```

**课后练习**
1. 实现维度表设计与事实表关联：构造"商品改名"场景，验证历史事实指向旧代理键、新事实指向新代理键。
2. 对比"内连接维度表"与"含默认成员的外连接"在 100 行事实（含 3 行 NULL 外键）上的汇总差异。
3. 思考题：为什么代理键"永不复用"？从"历史事实的可追溯性"与"事实表外键稳定性"分析。

---

### 63-04 缓慢变化维 SCD

**知识点**
- **SCD（Slowly Changing Dimension）解决"维度属性会变"的问题**：用户等级变了、商品改类目了、门店换区域了——**三种处理策略的语义完全不同**。
- **SCD1（覆盖）**：直接改属性值，**不保留历史**——简单、省空间，但**历史事实会"追溯性变化"**（去年的订单现在按新类目统计）——适合"纠错型变更"（属性填错了）。
- **SCD2（拉链，新增版本行）**：每次变更插入新版本（新代理键 + `start_date`/`end_date`/`is_current`），**历史事实仍指向旧版本**——最常用，**支持"按当时的维度属性分析"**（63-05 拉链表）。
- **SCD3（新增列）**：加一列"上一版本值"（`old_category`/`current_category`）——**只保留一个历史版本**，适合"只关心上一次变化"的场景（很少用）。
- **选择判据**：**分析是否需要"历史当时的属性"**——需要（如"当时的商品类目"）用 SCD2；不需要（如"纠正错别字"）用 SCD1；**混用要标注字段级策略**。
- 易错点：全用 SCD1（历史分析全错）；SCD2 忘记关旧版本（同一维度有多条 `is_current=true`）；SCD2 变更时间用 ETL 时间而非业务生效时间（分析错位）；SCD 没有变更检测（每次全量插新版本，维度爆炸）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScdDemo {
    record DimRow(int key, String naturalKey, String attribute, long startDate,
                  long endDate, boolean current) {
        String display() {
            return String.format("key=%d %s=%s [%d~%s]%s", key, naturalKey, attribute,
                    startDate, endDate == Long.MAX_VALUE ? "now" : String.valueOf(endDate),
                    current ? " (current)" : "");
        }
    }

    /** SCD1：直接覆盖 */
    static void scd1(List<DimRow> dim, String naturalKey, String newValue, long now) {
        for (int i = 0; i < dim.size(); i++) {
            DimRow r = dim.get(i);
            if (r.naturalKey().equals(naturalKey)) {
                dim.set(i, new DimRow(r.key(), r.naturalKey(), newValue,
                        r.startDate(), r.endDate(), r.current()));
            }
        }
        System.out.println("  SCD1：覆盖 -> 历史丢失");
    }

    /** SCD2：关闭旧版本 + 插入新版本 */
    static int nextKey = 2000;

    static void scd2(List<DimRow> dim, String naturalKey, String newValue,
                     long effectiveDate) {
        for (int i = 0; i < dim.size(); i++) {
            DimRow r = dim.get(i);
            if (r.naturalKey().equals(naturalKey) && r.current()) {
                dim.set(i, new DimRow(r.key(), r.naturalKey(), r.attribute(),
                        r.startDate(), effectiveDate, false));
            }
        }
        dim.add(new DimRow(nextKey++, naturalKey, newValue, effectiveDate,
                Long.MAX_VALUE, true));
        System.out.println("  SCD2：旧版本关闭 + 新版本插入");
    }

    public static void main(String[] args) {
        List<DimRow> dim = new ArrayList<>();
        dim.add(new DimRow(1001, "P-100", "类目=配件", 0, Long.MAX_VALUE, true));

        System.out.println("【初始维度】");
        dim.forEach(r -> System.out.println("  " + r.display()));

        System.out.println();
        System.out.println("【应用 SCD2：商品从配件改到数码（生效日 1000）】");
        scd2(dim, "P-100", "类目=数码", 1000);
        dim.forEach(r -> System.out.println("  " + r.display()));

        System.out.println();
        System.out.println("【历史事实的两种统计口径】");
        Map<Long, Integer> factsByKey = new LinkedHashMap<>();
        factsByKey.put(1001L, 5);                       // 变更前的 5 笔
        factsByKey.put(1002L, 2);                       // 变更后的 2 笔（key 2000）
        int oldCategory = factsByKey.getOrDefault(1001L, 0);
        int newKey = dim.stream().filter(r -> r.naturalKey().equals("P-100") && r.current())
                .findFirst().map(DimRow::key).orElse(-1);
        int newCategory = factsByKey.getOrDefault(newKey, 0);
        System.out.printf("  按「当时属性」统计：配件=%d 笔，数码=%d 笔（正确）%n",
                oldCategory, newCategory);
        System.out.printf("  若用 SCD1 覆盖：全部 %d 笔都会被算作「数码」（历史错位）%n",
                oldCategory + newCategory);
        System.out.println();
        System.out.println("【变更检测】对比新旧属性，不同才产生新版本（否则维度爆炸）");
    }
}
```

**课后练习**
1. 实现三种 SCD 策略（覆盖/拉链/加列），对"商品改类目"场景分别输出维度表变化与历史统计口径差异。
2. 实现"变更检测"：比较新旧属性，只有变化才新增 SCD2 版本，用 10 次（其中 7 次无变化）的更新验证版本数。
3. 思考题：为什么 SCD2 的有效时间要用"业务生效时间"而不是"ETL 时间"？构造一个跨日的类目调整用例说明差异。

---

### 63-05 拉链表与历史追溯

**知识点**
- **拉链表是 SCD2 在数仓中的典型落地形式**：每行带 `start_date`/`end_date`（`end_date` 用极大值表示"至今有效"），**同一业务主键的多行时间区间不重叠、连续覆盖**——查询任意一天的状态就是"`start_date <= 某天 < end_date`"。
- **拉链表的更新是"关旧开新"两步**：① 把"有变化的旧记录"的 `end_date` 改为变更日；② 插入新记录（`start_date` = 变更日，`end_date` = 极大值）——**两步在同一批次完成，保证区间连续无空洞**。
- **拉链表的查询模式**：**查最新**（`end_date = 极大值`）、**查历史某天**（区间包含）、**查变化区间**（按 `start_date` 排序看版本链）——**分析场景"按当时状态统计"直接 join 区间条件**。
- **拉链表与全量快照的取舍**：**全量快照**（每天一份全表）简单但存储爆炸（100 天 = 100 倍）；**拉链表**只存变化，存储小但要处理区间逻辑——**维度用拉链、事实用快照**是常见组合。
- **拉链表的坑在"边界"**：变更日当天的归属（`start_date` 当天生效还是次日）、同日多次变更（合并为一次）、区间重叠检测（数据质量规则 46-08）。
- 易错点：`end_date` 用 `NULL`（区间比较失效，应用极大值）；关旧开新不在同批（中间出现空洞/重叠）；同日多次变更产生多条（区间重叠）；拉链表的变更没有生效时间（历史错位）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ZipperTable {
    static final long OPEN = Long.MAX_VALUE;

    record Row(String userId, String level, long startDate, long endDate) {
        boolean validAt(long day) {
            return startDate <= day && day < endDate;
        }

        boolean isOpen() {
            return endDate == OPEN;
        }
    }

    /** 关旧开新：同一批次完成 */
    static void upsert(List<Row> table, String userId, String newLevel, long changeDay) {
        for (int i = 0; i < table.size(); i++) {
            Row r = table.get(i);
            if (r.userId().equals(userId) && r.isOpen()) {
                if (r.level().equals(newLevel)) {
                    System.out.printf("  %s 等级未变（%s）-> 不产生新版本%n", userId, newLevel);
                    return;
                }
                table.set(i, new Row(r.userId(), r.level(), r.startDate(), changeDay));
            }
        }
        table.add(new Row(userId, newLevel, changeDay, OPEN));
        System.out.printf("  %s 变更为 %s（生效日 %d）：旧版本关闭 + 新版本开启%n",
                userId, newLevel, changeDay);
    }

    static Row stateAt(List<Row> table, String userId, long day) {
        for (Row r : table) {
            if (r.userId().equals(userId) && r.validAt(day)) {
                return r;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        List<Row> table = new ArrayList<>();
        table.add(new Row("u-1", "NORMAL", 0, OPEN));

        System.out.println("【拉链表变更轨迹】");
        upsert(table, "u-1", "VIP", 100);
        upsert(table, "u-1", "VIP", 150);          // 无变化 -> 无新版本
        upsert(table, "u-1", "SVIP", 200);

        table.sort(Comparator.comparingLong(Row::startDate));
        System.out.println();
        System.out.println("【拉链表内容】");
        for (Row r : table) {
            System.out.printf("  用户=%s 等级=%-7s [%d, %s)%n", r.userId(), r.level(),
                    r.startDate(), r.isOpen() ? "now" : String.valueOf(r.endDate()));
        }

        System.out.println();
        System.out.println("【按天查询历史状态】");
        for (long day : new long[]{50, 120, 250}) {
            Row r = stateAt(table, "u-1", day);
            System.out.printf("  第 %3d 天 -> %s%n", day, r == null ? "无记录" : r.level());
        }

        System.out.println();
        System.out.println("【区间完整性校验：不重叠、无空洞】");
        boolean ok = true;
        for (int i = 0; i < table.size() - 1; i++) {
            if (table.get(i).endDate() != table.get(i + 1).startDate()) {
                ok = false;
            }
        }
        System.out.println("  校验结果：" + (ok ? "连续覆盖" : "存在空洞或重叠（数据质量问题）"));
    }
}
```

**课后练习**
1. 实现拉链表完整更新：支持"同日多次变更合并"与"变更检测"，输出区间连续性校验结果。
2. 实现查询：给定任意日期，按"当时的用户等级"统计订单量（事实表 join 拉链维度 + 区间条件）。
3. 思考题：为什么维度用拉链表、事实用快照？从"维度变化慢、事实不可变"的数据特征分析存储与查询的取舍。

---

### 63-06 事实表三种类型

**知识点**
- **事务事实表**：**每次业务事件一行**（一次下单/一次支付/一次退款）——粒度最细、最灵活，**不可变**（事件是事实，不更新）；**分析任何维度都能下钻**，是事实表的主力。
- **周期快照事实表**：**固定周期一行**（每天/每月的账户余额、库存量）——用于**状态趋势分析**；**半可加度量**（余额可跨维度加、不能跨时间加，63-02）。
- **累积快照事实表**：**一个业务对象一行，随流程推进更新多个时间戳**（下单时间/支付时间/发货时间/完成时间）——**用于流程效率分析**（各环节耗时、漏斗转化）；**是唯一"会被更新"的事实表**。
- **三者的选择**：**"发生了什么"用事务表**（订单、支付、退款）；**"现在的状态"用周期快照**（库存、余额、用户数）；**"流程走了多久"用累积快照**（订单全生命周期）。
- **与 43/59 章的关系**：事务事实表常由 CDC 或流处理写入（43-09）；周期快照由批处理生成（59-11）；累积快照需要"状态合并"（按业务主键 upsert，59-10 的 MERGE）。
- 易错点：用事务表算"当前库存"（要全量重算，慢）；用周期快照算"交易明细"（粒度不够）；累积快照的时间戳不更新（漏斗分析失效）；三种表混在一个 Topic/表里。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FactTableTypes {
    record TxnFact(long eventId, String orderId, String type, long amountCents, long atMs) {
    }

    record SnapshotFact(String date, int sku, int stock, long stockValueCents) {
    }

    record AccumulatingFact(String orderId, Long placedAt, Long paidAt, Long shippedAt,
                            Long finishedAt, String status) {
    }

    static void transactionFact() {
        System.out.println("【事务事实表：每次事件一行】");
        List<TxnFact> facts = List.of(
                new TxnFact(1, "9001", "CREATED", 6900, 1000),
                new TxnFact(2, "9001", "PAID", 6900, 1100),
                new TxnFact(3, "9002", "CREATED", 3900, 1200),
                new TxnFact(4, "9002", "REFUNDED", -3900, 1300));
        facts.forEach(f -> System.out.printf("  #%d %s %-9s %6d分 @%d%n",
                f.eventId(), f.orderId(), f.type(), f.amountCents(), f.atMs()));
        long gmv = facts.stream().filter(f -> f.type().equals("PAID"))
                .mapToLong(TxnFact::amountCents).sum();
        System.out.println("  GMV（支付金额）= " + gmv + " 分（可加、可任意维度下钻）");
        System.out.println();
    }

    static void periodicSnapshot() {
        System.out.println("【周期快照：每天一行，记录状态】");
        List<SnapshotFact> snaps = List.of(
                new SnapshotFact("2026-11-16", 1, 100, 690_000),
                new SnapshotFact("2026-11-17", 1, 80, 552_000),
                new SnapshotFact("2026-11-18", 1, 60, 414_000));
        snaps.forEach(s -> System.out.printf("  %s sku=%d 库存=%-4d 库存金额=%d分%n",
                s.date(), s.sku(), s.stock(), s.stockValueCents()));
        long sumAcrossTime = snaps.stream().mapToLong(SnapshotFact::stockValueCents).sum();
        System.out.println("  跨天求和 = " + sumAcrossTime + "（错误！余额类半可加，不能跨时间累加）");
        System.out.println("  正确用法：看趋势/取某天快照（最新库存 60）");
        System.out.println();
    }

    static void accumulatingSnapshot() {
        System.out.println("【累积快照：一单一行，随流程更新时间戳】");
        Map<String, AccumulatingFact> table = new LinkedHashMap<>();
        table.put("9001", new AccumulatingFact("9001", 1000L, null, null, null, "CREATED"));
        System.out.println("  初始：" + table.get("9001"));

        table.put("9001", new AccumulatingFact("9001", 1000L, 1100L, null, null, "PAID"));
        table.put("9001", new AccumulatingFact("9001", 1000L, 1100L, 1500L, null, "SHIPPED"));
        table.put("9001", new AccumulatingFact("9001", 1000L, 1100L, 1500L, 3000L, "FINISHED"));
        AccumulatingFact f = table.get("9001");
        System.out.println("  最终：" + f);
        System.out.printf("  漏斗耗时：下单->支付 %dms，支付->发货 %dms，发货->完成 %dms%n",
                f.paidAt() - f.placedAt(), f.shippedAt() - f.paidAt(),
                f.finishedAt() - f.shippedAt());
        System.out.println();
        System.out.println("  唯一会被更新的事实表 -> 用于流程效率与漏斗分析");
    }

    public static void main(String[] args) {
        transactionFact();
        periodicSnapshot();
        accumulatingSnapshot();
    }
}
```

**课后练习**
1. 为同一业务（订单）建三种事实表，分别回答"昨天 GMV""当前库存""下单到完成平均耗时"，说明为什么必须用三种表。
2. 实现累积快照的更新逻辑：按 orderId upsert 四个时间戳 + 状态，构造"跳步（未发货直接完成）"异常数据并检出。
3. 思考题：为什么周期快照的度量是"半可加"？用库存跨天求和的错误结果说明。

---

### 63-07 星型与雪花的权衡

**知识点**
- **星型模型**：维度表**不规范化**（把"商品类目"直接放商品维度里，即使有冗余）——**join 次数少、查询快、易理解**；**代价是维度表冗余**（类目名重复存储）。
- **雪花模型**：维度表**规范化拆分**（商品维度 -> 类目维度 -> 一级类目维度）——**省空间、维度一致性好**；**代价是 join 层数多、查询慢、理解成本高**。
- **选择的判据是"分析场景的 join 成本"**：数仓分析查询动辄扫 TB 级，**每多一层 join 都是巨大的 IO 与 CPU**——**维度建模的默认答案是星型**；雪花只在"维度层级极深且需要严格一致"时使用。
- **折衷方案**：**维度表适度冗余**（把常用层级属性拍平：`category_l1/category_l2` 都放商品维度）+ **一致性维度**（63-08 保证同一维度在不同事实表里定义一致）——**用"受控冗余"换查询性能**。
- **与 46 章列存的配合**：星型的宽维度表 + 列存（46-02）的列裁剪，**join 时只读需要的列**——这是"星型 + 列存"成为数据仓库标准形态的原因。
- 易错点：全盘雪花化（join 七八层，查询慢十倍）；星型里冗余不一致（同一类目在不同维度表名字不同）；维度表拍平后没有更新机制（冗余数据过期）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StarVsSnowflake {
    record SnowProduct(int productKey, String name, int categoryKey) {
    }

    record SnowCategory(int categoryKey, String categoryName, int l1Key) {
    }

    record SnowL1(int l1Key, String l1Name) {
    }

    record StarProduct(int productKey, String name, String categoryName, String l1Name) {
    }

    static void snowflakeQuery() {
        System.out.println("【雪花：三层 join】");
        Map<Integer, SnowProduct> products = Map.of(
                1, new SnowProduct(1, "手机壳 A", 10),
                2, new SnowProduct(2, "蓝牙耳机", 20));
        Map<Integer, SnowCategory> categories = Map.of(
                10, new SnowCategory(10, "手机配件", 100),
                20, new SnowCategory(20, "音频设备", 200));
        Map<Integer, SnowL1> l1 = Map.of(
                100, new SnowL1(100, "数码"),
                200, new SnowL1(200, "数码"));
        int joins = 0;
        for (SnowProduct p : products.values()) {
            SnowCategory c = categories.get(p.categoryKey());
            joins++;
            SnowL1 top = l1.get(c.l1Key());
            joins++;
            System.out.printf("  %-10s -> %-8s -> %-4s（已 join %d 次）%n",
                    p.name(), c.categoryName(), top.l1Name(), joins);
        }
        System.out.println();
    }

    static void starQuery() {
        System.out.println("【星型：维度拍平，零 join 到类目】");
        List<StarProduct> dim = List.of(
                new StarProduct(1, "手机壳 A", "手机配件", "数码"),
                new StarProduct(2, "蓝牙耳机", "音频设备", "数码"));
        dim.forEach(p -> System.out.printf("  %-10s %-8s %-4s（已 join 0 次）%n",
                p.name(), p.categoryName(), p.l1Name()));
        System.out.println();
    }

    static void tradeoff() {
        System.out.println("【权衡表】");
        String[][] rows = {
                {"join 次数", "多（层级深度）", "少（维度拍平）"},
                {"存储", "省（无冗余）", "冗余（受控）"},
                {"查询性能", "慢（海量数据放大）", "快（推荐）"},
                {"维度一致性", "天然好（一份类目）", "需一致性维度保"},
                {"适用", "维度层级极深+强一致", "绝大多数分析场景"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s 雪花=%-18s 星型=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  折衷：维度适度拍平（category_l1/l2 放一张表）+ 一致性维度（63-08）");
    }

    public static void main(String[] args) {
        snowflakeQuery();
        starQuery();
        tradeoff();
    }
}
```

**课后练习**
1. 为同一份商品数据分别设计雪花（3 层）与星型（拍平）模型，对比"查某一级类目下商品数"的 join 次数与可读性。
2. 设计"适度拍平"方案：哪些属性拍平、哪些保留引用，给出判据（查询频率 × 层级深度）。
3. 思考题：为什么"星型 + 列存"是数据仓库的标准形态？从 join 成本与列裁剪的协同效应分析。

---

### 63-08 一致性维度与总线矩阵

**知识点**
- **一致性维度（Conformed Dimension）**：**同一维度在多个事实表中定义完全一致**（同样的代理键、同样的属性、同样的取值）——**没有一致性维度，跨过程分析就对不上**（订单表的"杭州"与支付表的"杭州市"）。
- **总线矩阵（Bus Matrix）**：**行=业务过程（事实表）、列=维度**，交叉点标记"该过程用该维度"——**它是数仓的全局地图**，保证"每个维度被多个事实表共享、每个过程覆盖核心维度"。
- **一致性维度的落地**：**维度表集中建设**（一份 dim_product 被订单/退款/库存/推荐事实表引用）；**维度变更统一走 SCD**（63-04）；**新增属性的影响面评估**（46-08 血缘）。
- **总线矩阵的价值**：**规划阶段**避免"每个报表建一套维度"（数据孤岛）；**评审阶段**检查"是否有过程缺关键维度"或"是否有维度只被一个过程用"（后者可能是设计冗余）。
- **与 46-10 指标平台的关系**：一致性维度是"切片方向的统一"，指标平台是"度量口径的统一"——**两者共同构成"数字对得上"的基础**。
- 易错点：每个主题各建一套用户维度（跨主题分析失败）；维度属性命名不统一（city/city_name/cityName）；总线矩阵画完不维护（新过程不登记）；一致性维度被单点修改（影响所有下游）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConformedDimension {
    /** 总线矩阵：行=过程，列=维度 */
    static final List<String> DIMENSIONS =
            List.of("日期", "用户", "商品", "渠道", "门店");

    static final Map<String, List<String>> BUS_MATRIX = new LinkedHashMap<>();

    static void build() {
        BUS_MATRIX.put("下单", List.of("日期", "用户", "商品", "渠道"));
        BUS_MATRIX.put("支付", List.of("日期", "用户", "渠道"));
        BUS_MATRIX.put("退款", List.of("日期", "用户", "商品", "渠道"));
        BUS_MATRIX.put("库存", List.of("日期", "商品", "门店"));
        BUS_MATRIX.put("到店", List.of("日期", "用户", "门店"));
    }

    static void printMatrix() {
        System.out.println("【总线矩阵】");
        StringBuilder header = new StringBuilder("  过程\\维度  ");
        DIMENSIONS.forEach(d -> header.append(String.format("%-6s", d)));
        System.out.println(header);
        BUS_MATRIX.forEach((process, dims) -> {
            StringBuilder line = new StringBuilder(String.format("  %-8s  ", process));
            for (String d : DIMENSIONS) {
                line.append(String.format("%-6s", dims.contains(d) ? "✓" : "-"));
            }
            System.out.println(line);
        });
        System.out.println();
    }

    static void consistencyCheck() {
        System.out.println("【一致性检查】");
        Map<String, Integer> usage = new LinkedHashMap<>();
        BUS_MATRIX.values().forEach(dims -> dims.forEach(d -> usage.merge(d, 1, Integer::sum)));
        usage.forEach((dim, count) -> {
            String verdict = count >= 2 ? "被 " + count + " 个过程共享（一致性维度）"
                    : "仅 1 个过程使用（检查是否应共享）";
            System.out.printf("  %-4s %s%n", dim, verdict);
        });
        System.out.println();

        List<String> orphan = new ArrayList<>();
        BUS_MATRIX.forEach((process, dims) -> {
            if (!dims.contains("日期")) {
                orphan.add(process);
            }
        });
        System.out.println("  缺日期维度的过程：" + (orphan.isEmpty() ? "无" : orphan)
                + "（任何事实表都应能按时间切）");
    }

    static void namingConflict() {
        System.out.println("【命名不一致的代价】");
        Map<String, String> cityByTable = new LinkedHashMap<>();
        cityByTable.put("fact_order.dim_user_city", "杭州");
        cityByTable.put("fact_pay.dim_user_city", "杭州市");
        cityByTable.put("fact_refund.dim_user_city", "杭州");
        System.out.println("  " + cityByTable);
        System.out.println("  按城市 join 三个事实表 -> 支付数据对不上（值域不一致）");
        System.out.println("  正解：一份 dim_user + 统一值域 + 变更走 SCD");
    }

    public static void main(String[] args) {
        build();
        printMatrix();
        consistencyCheck();
        namingConflict();
    }
}
```

**课后练习**
1. 为你的业务画出总线矩阵（≥4 个过程 × ≥5 个维度），标出一致性维度与"只被一个过程使用"的维度。
2. 实现一致性检查器：自动找出"缺关键维度（日期/用户）"的过程与"命名冲突"的维度属性。
3. 思考题：为什么"一致性维度"是跨主题分析的前提？构造两个主题各自建用户维度导致城市口径不一致的场景。

---

### 63-09 指标口径与原子指标

**知识点**
- **指标分层（46-10 的建模落地）**：**原子指标**（不可再拆的最细度量：支付金额、订单数）+ **派生指标**（原子指标 × 修饰词 × 时间周期：近 7 天支付金额）+ **复合指标**（多个派生指标计算：客单价 = 支付金额 ÷ 订单数）。
- **修饰词是"筛选条件"的标准化**：`近 7 天`、`新用户`、`App 渠道`——**修饰词要注册**（避免"近 7 天"有的含当天有的不含）；**时间周期与统计口径必须唯一**。
- **比率类指标的正确实现**：**存分子分母，查询时计算**（63-02 的不可加性）——**平均值的平均是错的**（辛普森悖论 54-08 的同类问题）；**去重类指标**（UV）不能跨天简单相加（要 union 去重，或用近似算法 46-07）。
- **口径文档是资产**：每个指标记录"业务含义/计算公式/数据源/过滤条件/负责人/更新频率"——**口径变更要评审 + 版本化 + 通知下游**（46-08 契约的指标版）。
- **与 42-05 SLO 的类比**：指标口径就是"数据的 SLI 定义"——**定义不清的指标无法用于决策**（52-01 非功能量化的思路）。
- 易错点：比率存成事实（汇总错误）；UV 跨天相加（重复计数）；"近 7 天"含当天与否不统一（数字对不上）；口径改了不通知下游（报表突变）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetricDefinition {
    record AtomicMetric(String code, String name, String formula, String owner) {
    }

    record DerivedMetric(String code, String name, String atomicCode, List<String> modifiers,
                         String period) {
    }

    static final Map<String, AtomicMetric> ATOMIC = new LinkedHashMap<>();
    static final List<DerivedMetric> DERIVED = new ArrayList<>();

    static void registry() {
        ATOMIC.put("pay_amount", new AtomicMetric("pay_amount", "支付金额",
                "SUM(fact_pay.amount) WHERE status=PAID", "数据组-小李"));
        ATOMIC.put("order_cnt", new AtomicMetric("order_cnt", "订单数",
                "COUNT(DISTINCT fact_order.order_id)", "数据组-小李"));
        DERIVED.add(new DerivedMetric("d7_pay_amount", "近7天支付金额", "pay_amount",
                List.of(), "近7天（不含当天）"));
        DERIVED.add(new DerivedMetric("new_user_order_cnt", "新用户订单数", "order_cnt",
                List.of("新用户", "App渠道"), "自然日"));
        System.out.println("【原子指标】");
        ATOMIC.values().forEach(m -> System.out.printf("  %-12s %-8s 公式=%s%n",
                m.code(), m.name(), m.formula()));
        System.out.println("【派生指标】");
        DERIVED.forEach(d -> System.out.printf("  %-18s = %s × %s × %s%n",
                d.name(), d.atomicCode(), d.modifiers(), d.period()));
        System.out.println();
    }

    /** 比率类：存分子分母，查询时算 */
    static void ratioMetric() {
        System.out.println("【客单价：分子分母 vs 平均的平均】");
        record Group(String name, long amount, long orders) {
        }
        List<Group> groups = List.of(
                new Group("App", 100_000, 100),
                new Group("Web", 10_000, 100));
        long totalAmount = groups.stream().mapToLong(Group::amount).sum();
        long totalOrders = groups.stream().mapToLong(Group::orders).sum();
        double correct = (double) totalAmount / totalOrders;
        double wrong = groups.stream().mapToDouble(g -> (double) g.amount() / g.orders())
                .average().orElse(0);
        System.out.printf("  正确：总金额/总订单 = %d/%d = %.2f%n", totalAmount, totalOrders, correct);
        System.out.printf("  错误：各分组客单价再平均 = %.2f（被单量少的分组拉偏）%n", wrong);
        System.out.println("  结论：比率指标必须存分子分母（或存明细），不能存比率值");
        System.out.println();
    }

    /** UV 类：跨天不能简单相加 */
    static void distinctMetric() {
        System.out.println("【UV：跨天不能相加】");
        Map<String, List<String>> uvByDay = new LinkedHashMap<>();
        uvByDay.put("11-16", List.of("u1", "u2", "u3"));
        uvByDay.put("11-17", List.of("u2", "u3", "u4"));
        long naive = uvByDay.values().stream().mapToLong(List::size).sum();
        long real = uvByDay.values().stream().flatMap(List::stream).distinct().count();
        System.out.printf("  逐日 UV 相加 = %d（错误，u2/u3 重复计数）%n", naive);
        System.out.printf("  去重后 UV = %d（正确）%n", real);
        System.out.println("  跨天/跨维度去重指标：用明细去重或 HyperLogLog 近似（46-07）");
        System.out.println();
        System.out.println("  口径纪律：公式/过滤/时间边界/负责人 全部登记，变更走评审与版本化");
    }

    public static void main(String[] args) {
        registry();
        ratioMetric();
        distinctMetric();
    }
}
```

**课后练习**
1. 注册 5 个原子指标与 5 个派生指标（含修饰词与时间周期），写出每个的口径文档字段。
2. 用分组数据验证"平均值的平均"与"总金额/总订单"的差异，说明比率指标的正确实现。
3. 思考题：为什么"去重类指标"跨天不能相加？设计一个"近 30 天活跃用户"的正确计算方案。

---

### 63-10 建模评审与反模式

**知识点**
- **建模评审的五个检查项**：① **业务过程是否清晰**（一张事实表对应一个可描述的业务事件）；② **粒度是否单一且已声明**；③ **维度是否覆盖核心分析方向**（日期/用户/商品/渠道）；④ **事实是否度量且可加性已标注**；⑤ **命名与数据类型是否规范**（统一后缀 `_key/_id/_amt/_cnt`）。
- **七大反模式**：**事实表混粒度**（汇总翻倍）、**维度里放度量**（无法聚合）、**比率存事实**（错误汇总）、**NULL 外键无默认成员**（丢行）、**每报表一套维度**（口径分裂）、**拉链表区间重叠**（历史查错）、**全量快照替代拉链**（存储爆炸）。
- **评审的可自动化部分**：用数据质量规则做"结构校验"（区间连续性/外键完整性/粒度唯一性 46-08）；**人工评审聚焦"业务语义"**（这个粒度对吗/维度够吗）——**机器查一致性，人查合理性**。
- **建模的演进成本**：**改粒度 = 重建事实表**（最贵）；**加维度 = 加外键 + 回刷**（中等）；**加度量 = 加列**（最便宜）——**评审时要为未来留出"加维度"的空间**（63-02）。
- **与 49-11 的关系**：49-11 是"领域模型评审"（聚合/不变量），本章是"分析模型评审"（粒度/维度/度量）——**两套评审清单都在回答"模型是否经得起追问"**。
- 易错点：评审只看字段类型不看业务语义；反模式"先上线再改"（重建成本高）；评审记录不留档（下次同样的问题）；没有 owner（维度表无人维护）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModelingReview {
    record Check(String item, String question, String failExample) {
    }

    static void checklist() {
        System.out.println("【建模评审五问】");
        List<Check> checks = List.of(
                new Check("业务过程", "这张事实表对应哪个业务事件？", "一张表混了订单与支付"),
                new Check("粒度", "一行代表什么？是否唯一？", "既有订单行又有商品行"),
                new Check("维度", "核心分析方向都覆盖了吗？", "少了日期维度无法看趋势"),
                new Check("事实", "都是可度量值且可加性明确？", "把客单价当事实"),
                new Check("命名", "后缀规范一致吗？", "amount/cnt/price 混用"));
        for (Check c : checks) {
            System.out.printf("  %-6s %-24s 反例：%s%n", c.item(), c.question(), c.failExample());
        }
        System.out.println();
    }

    static void antiPatterns() {
        System.out.println("【七大反模式与修复】");
        Map<String, String> patterns = new LinkedHashMap<>();
        patterns.put("事实表混粒度", "拆成两张事实表（订单级/商品级）");
        patterns.put("维度里放度量", "度量移入事实表");
        patterns.put("比率存事实", "存分子分母，查询时计算");
        patterns.put("NULL 外键", "维度加默认成员 -1");
        patterns.put("每报表一套维度", "建一致性维度（63-08）");
        patterns.put("拉链区间重叠", "同批关旧开新 + 区间校验");
        patterns.put("全量快照", "改拉链表或周期快照");
        patterns.forEach((k, v) -> System.out.printf("  %-14s -> %s%n", k, v));
        System.out.println();
    }

    /** 可自动化的结构校验 */
    static void autoChecks() {
        System.out.println("【可自动化的结构校验】");
        record FactRow(String orderId, String productKey, String dateKey, long amount) {
        }
        List<FactRow> facts = List.of(
                new FactRow("9001", "P-1", "2026-11-18", 6900),
                new FactRow("9001", "P-2", "2026-11-18", 3900),
                new FactRow("9001", "P-1", "2026-11-18", 6900));       // 重复行
        Map<String, Integer> grainCheck = new LinkedHashMap<>();
        for (FactRow f : facts) {
            grainCheck.merge(f.orderId() + "|" + f.productKey() + "|" + f.dateKey(), 1, Integer::sum);
        }
        List<String> dup = new ArrayList<>();
        grainCheck.forEach((k, v) -> {
            if (v > 1) {
                dup.add(k + " x" + v);
            }
        });
        System.out.println("  粒度唯一性校验（订单+商品+日期 应唯一）：重复=" + dup);
        long nullFk = facts.stream().filter(f -> f.productKey() == null || f.productKey().isBlank()).count();
        System.out.println("  外键完整性：空外键行=" + nullFk);
        System.out.println("  金额合理性：非负行=" + facts.stream().filter(f -> f.amount() >= 0).count()
                + "/" + facts.size());
        System.out.println();
        System.out.println("  分工：机器查一致性（唯一性/完整性/区间），人查合理性（粒度/维度/语义）");
    }

    public static void main(String[] args) {
        checklist();
        antiPatterns();
        autoChecks();
    }
}
```

**课后练习**
1. 用五问清单评审你项目里的一张事实表（或虚构），输出问题清单与修复优先级。
2. 实现结构校验器：粒度唯一性、外键完整性、金额非负、拉链区间连续，用 20 行脏数据验证全部检出。
3. 思考题：为什么"改粒度"是成本最高的演进？从事实表重建与下游改写的连锁影响分析。

---

### 63-11 综合案例：电商订单主题建模

**知识点**
- **把本章收口成一套完整建模**：确定业务过程（下单/支付/退款）-> 各自粒度（订单商品级/支付尝试级/退款商品级）-> 共享一致性维度（日期/用户/商品/渠道/门店）-> 三类事实表（事务 + 周期快照 + 累积快照）-> SCD2 商品/用户维度 -> 指标注册（原子/派生/复合）-> 评审与结构校验。
- **设计决策的显式记录**：**为什么订单用商品粒度**（可汇总出订单级）；**为什么退款独立事实表**（金额方向与口径不同）；**为什么库存用周期快照**（状态类半可加）；**为什么商品维度 SCD2**（类目变更要可追溯）——**每条都是 ADR 素材**（52-02）。
- **一致性验收**：跨过程 join 一致性维度能对上（63-08）；指标口径唯一（63-09）；结构校验全绿（63-10）；拉链区间连续（63-05）。
- **与 46 章链路的对接**：ODS 原样 -> DWD 维度建模（本章）-> DWS 按主题聚合 -> ADS 报表/接口；**Spark 作业负责层间搬运**（59-11），质量规则负责层间断言（46-08）。
- 易错点：三个过程各建一套维度（口径分裂）；退款金额混进支付事实表（GMV 计算错误）；指标口径散落在 SQL 里（无注册）；没有结构校验（脏数据无人拦截）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderModelingCase {
    record Dim(String name, String strategy, String note) {
    }

    record Fact(String name, String grain, List<String> dims, List<String> measures) {
    }

    static void model() {
        System.out.println("【一致性维度（共享）】");
        List<Dim> dims = List.of(
                new Dim("dim_date", "静态生成", "年/季/月/周/节假日"),
                new Dim("dim_user", "SCD2", "等级变更可追溯"),
                new Dim("dim_product", "SCD2", "类目调整可追溯"),
                new Dim("dim_channel", "SCD1", "纠错型，无需历史"),
                new Dim("dim_store", "SCD2", "门店区域调整"));
        dims.forEach(d -> System.out.printf("  %-12s %-8s %s%n", d.name(), d.strategy(), d.note()));

        System.out.println();
        System.out.println("【三个过程的事实表】");
        List<Fact> facts = List.of(
                new Fact("fact_order_item", "订单+商品", List.of("日期", "用户", "商品", "渠道"),
                        List.of("数量", "金额", "优惠", "成本")),
                new Fact("fact_pay_attempt", "支付尝试", List.of("日期", "用户", "渠道"),
                        List.of("支付金额", "手续费")),
                new Fact("fact_refund_item", "退款+商品", List.of("日期", "用户", "商品", "渠道"),
                        List.of("退款金额", "退款数量")));
        facts.forEach(f -> System.out.printf("  %-18s 粒度=%-10s 维度=%-28s 度量=%s%n",
                f.name(), f.grain(), f.dims(), f.measures()));
        System.out.println();
    }

    /** 跨过程一致性：同一用户在同一时间范围的各过程金额 */
    static void crossProcessCheck() {
        System.out.println("【跨过程一致性校验（同一 dim_user 代理键）】");
        Map<String, Long> orderByUser = new LinkedHashMap<>();
        orderByUser.put("u-key-501", 108_00L);
        Map<String, Long> payByUser = new LinkedHashMap<>();
        payByUser.put("u-key-501", 108_00L);
        Map<String, Long> refundByUser = new LinkedHashMap<>();
        refundByUser.put("u-key-501", 39_00L);
        for (String key : orderByUser.keySet()) {
            long order = orderByUser.get(key);
            long pay = payByUser.getOrDefault(key, 0L);
            long refund = refundByUser.getOrDefault(key, 0L);
            long netPay = pay - refund;
            System.out.printf("  用户 %s：下单=%d 支付=%d 退款=%d 净支付=%d -> %s%n",
                    key, order, pay, refund, netPay,
                    netPay == order ? "对账一致" : "差异需排查（39-09 对账口径）");
        }
        System.out.println("  前提：三个过程共享同一 dim_user（否则 join 不上）");
        System.out.println();
    }

    static void designDecisions() {
        System.out.println("【设计决策记录（ADR 素材）】");
        Map<String, String> adr = new LinkedHashMap<>();
        adr.put("订单用商品粒度", "可汇总出订单级；订单粒度无法下钻到商品");
        adr.put("退款独立事实表", "金额方向与口径不同，混表会让 GMV 计算歧义");
        adr.put("库存用周期快照", "状态类度量半可加，按天快照支持趋势");
        adr.put("商品维度 SCD2", "类目调整后需按「当时类目」统计");
        adr.put("支付用尝试粒度", "可分析失败重试，但金额需按订单去重");
        adr.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
    }

    public static void main(String[] args) {
        model();
        crossProcessCheck();
        designDecisions();
    }
}
```

**课后练习**
1. 为你的业务完成一套完整建模（≥3 个过程 + 5 个一致性维度 + 指标注册），输出评审文档。
2. 实现跨过程一致性校验：订单/支付/退款三表按用户+日期 join，输出"净支付 vs 下单金额"的差异明细。
3. 思考题：为什么"退款独立事实表"能避免 GMV 歧义？用"支付金额减去退款金额"与"混表后 SUM 金额"两种口径对比说明。
