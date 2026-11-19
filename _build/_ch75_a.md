### 75-01 为什么需要 GraphQL

**知识点**
- **REST 的两个痛点**：**过度获取**（列表页只要 id+name，接口却返回 30 个字段）与**获取不足**（一个页面要调 5 个接口再前端拼装）——**GraphQL 让客户端声明"我要哪些字段"，一次请求拿全**。
- **GraphQL 的核心三个概念**：**Schema**（服务端定义的类型系统，强类型）、**Query**（客户端声明要什么，形状即结果）、**Resolver**（每个字段怎么取数）——**"查询的形状 = 响应的形状"**是它最直观的价值。
- **与 REST 的关系不是替代**：**REST 适合"资源固定、缓存友好"的公开 API**（64 章）；**GraphQL 适合"多端需求差异大、字段组合多"的 BFF 层**（75-10）——**很多公司两者并存**：对外 REST，对内 GraphQL。
- **GraphQL 的代价**：**缓存难**（单端点 POST，HTTP 缓存失效，需要持久化查询/字段级缓存）、**N+1 风险**（75-05）、**复杂度攻击**（深层嵌套查询，75-08）——**它不是免费的午餐**。
- 与 64 章的关系：64 章讲"REST 开放平台怎么治理"，本章讲"另一种 API 形态的治理"——**治理问题同构**（鉴权/限流/版本/错误码）。
- 易错点：为了"技术先进"上 GraphQL（团队与场景不匹配）；不做复杂度限制（被一个查询打挂）；把所有业务塞进一个巨型 schema（变成分布式单体）；忽略 N+1（性能比 REST 还差）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhyGraphQL {
    record Product(long id, String name, double price, String brand, String desc,
                   int stock, String category, List<String> images) {
    }

    static void restProblem() {
        Product p = new Product(1, "手机壳", 69.0, "自有", "液态硅胶...", 500,
                "配件", List.of("a.jpg", "b.jpg"));
        System.out.println("【REST 的两个问题】");
        System.out.println("  列表页只要 id+name，REST 返回全部 8 个字段：");
        System.out.println("    过度获取：响应 " + estimateBytes(p) + " 字节，实际需要约 40 字节");
        System.out.println("  详情页要商品+品牌+库存+图片，REST 可能要调 3 个接口：");
        System.out.println("    GET /products/1 + GET /brands/自有 + GET /stocks/1");
        System.out.println("    获取不足：前端要拼装，且三次往返");
        System.out.println();
    }

    static int estimateBytes(Product p) {
        return (p.name() + p.brand() + p.desc() + p.category()
                + p.images().toString()).length() + 60;
    }

    static void graphqlIdea() {
        System.out.println("【GraphQL：客户端声明形状】");
        String query = """
                {
                  product(id: 1) {
                    name
                    price
                  }
                }""";
        System.out.println("  查询：");
        query.lines().forEach(l -> System.out.println("    " + l));
        System.out.println("  响应（形状与查询一致）：");
        System.out.println("    { \"data\": { \"product\": { \"name\": \"手机壳\", \"price\": 69.0 } } }");
        System.out.println("  只取两个字段 -> 一次请求、无过度获取");

        System.out.println();
        System.out.println("  详情页一次拿全：");
        String detail = "{ product(id: 1) { name price brand stock images } }";
        System.out.println("    " + detail);
        System.out.println("    -> 一个请求拿到四个字段（无需 3 次往返）");
        System.out.println();
    }

    static void tradeoffs() {
        System.out.println("【GraphQL 的代价】");
        String[][] rows = {
                {"HTTP 缓存", "URL 即资源，CDN 可缓存", "单端点 POST，HTTP 缓存失效"},
                {"N+1", "接口内自行控制", "字段级取数易 N+1（75-05）"},
                {"复杂度攻击", "接口固定，风险可控", "深嵌套查询可打挂（75-08）"},
                {"可观测", "按 URL 统计", "按 operation name 统计"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s REST=%-22s GraphQL=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  结论：对外 REST（缓存友好），对内 BFF 用 GraphQL（聚合灵活）");
    }

    public static void main(String[] args) {
        restProblem();
        graphqlIdea();
        tradeoffs();
    }
}
```

**课后练习**
1. 找一个你项目的详情页，列出"REST 需要几次请求 / GraphQL 一次能不能拿全"，量化往返次数。
2. 对比 REST 与 GraphQL 在"缓存/版本/可观测"三个方面的治理差异，写出选型建议。
3. 思考题：为什么"把所有业务塞进一个 GraphQL schema"会变成分布式单体？从"耦合与发布"分析。

---

### 75-02 Schema 与类型系统

**知识点**
- **Schema 是 GraphQL 的契约**：**对象类型**（`type Product { id: ID! name: String! }`）、**标量**（ID/String/Int/Float/Boolean，可自定义 Date 等）、**枚举**、**接口与联合**（多态）、**输入类型**（Mutation 参数）——**强类型是 GraphQL 的核心优势**（客户端可静态校验）。
- **`!` 表示非空**：`name: String!` 表示"一定返回字符串"，`String` 表示"可能为 null"——**非空字段的 resolver 不能返回 null**（否则整棵树报错 75-07）。
- **Schema 即文档**：内省查询（`__schema`）能导出完整类型定义（`75-09` 的安全注意）；**代码生成**（客户端从 schema 生成类型安全的请求代码）是 GraphQL 生态最大价值之一（64-07 的 SDK 生成思路）。
- **与 49 章建模的关系**：GraphQL schema 是"API 层的类型建模"（面向前端需求），领域模型是"业务层的建模"——**schema 可以组合多个领域模型**（BFF 的价值）。
- 易错点：全字段非空（一有 null 就整树报错）；把数据库表直接映射成 schema（暴露内部结构）；不定义输入类型（Mutation 参数散落）；schema 与实现不同步（无生成/校验）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchemaDesign {
    /** 最小 schema 模型：类型 -> 字段(名, 类型, 非空) */
    record FieldDef(String name, String type, boolean nonNull) {
        String toSdl() {
            return name + ": " + type + (nonNull ? "!" : "");
        }
    }

    record TypeDef(String name, String kind, List<FieldDef> fields) {
        String toSdl() {
            StringBuilder sb = new StringBuilder(kind + " " + name + " {\n");
            fields.forEach(f -> sb.append("  ").append(f.toSdl()).append("\n"));
            return sb.append("}").toString();
        }
    }

    static void schema() {
        List<TypeDef> types = List.of(
                new TypeDef("Product", "type", List.of(
                        new FieldDef("id", "ID", true),
                        new FieldDef("name", "String", true),
                        new FieldDef("price", "Float", true),
                        new FieldDef("brand", "Brand", false),
                        new FieldDef("stock", "Int", false),
                        new FieldDef("status", "ProductStatus", true),
                        new FieldDef("reviews", "[Review!]", true))),
                new TypeDef("Brand", "type", List.of(
                        new FieldDef("id", "ID", true),
                        new FieldDef("name", "String", true))),
                new TypeDef("Review", "type", List.of(
                        new FieldDef("id", "ID", true),
                        new FieldDef("score", "Int", true),
                        new FieldDef("content", "String", false))),
                new TypeDef("ProductInput", "input", List.of(
                        new FieldDef("name", "String", true),
                        new FieldDef("price", "Float", true))));
        types.forEach(t -> {
            System.out.println("  " + t.toSdl().replace("\n", "\n  "));
            System.out.println();
        });
        System.out.println("【枚举】");
        System.out.println("  enum ProductStatus { ONSALE OFFSALE SOLD_OUT }");
        System.out.println();
        System.out.println("【Query 与 Mutation 根类型】");
        System.out.println("  type Query { product(id: ID!): Product products(first: Int): [Product!]! }");
        System.out.println("  type Mutation { createProduct(input: ProductInput!): Product! }");
        System.out.println();
    }

    static void nullability() {
        System.out.println("【非空 vs 可空】");
        System.out.println("  name: String!  -> resolver 返回 null 会向上冒泡报错（整棵树 null）");
        System.out.println("  brand: Brand   -> 返回 null 合法（客户端要处理）");
        System.out.println("  原则：确实必有的用 !，其余留可空（否则一个 null 毁掉整个响应）");
        System.out.println();
        System.out.println("【反模式】把数据库表字段一一映射：暴露 created_by/version 等内部字段");
    }

    public static void main(String[] args) {
        schema();
        nullability();
    }
}
```

**课后练习**
1. 为"商品 + 品牌 + 评论"设计 GraphQL schema（含枚举、输入类型、Query/Mutation 根），用 SDL 写出。
2. 判断 10 个字段该不该加 `!`，并说明"错加非空"的后果（整树报错）。
3. 思考题：为什么"schema 直接映射数据库表"是反模式？从"暴露内部结构"与"前端需求变化"分析。

---

### 75-03 查询解析

**知识点**
- **GraphQL 查询是一棵树**：`{ product(id:1) { name price } }` 解析成 AST（SelectionSet：字段 + 参数 + 子选择集）——**解析器（parser）产出 AST，执行器（executor）按 AST 取数**。
- **解析的三类节点**：**Operation**（query/mutation/subscription + 变量声明）、**Field**（字段名 + 别名 + 参数 + 子选择集）、**Fragment**（可复用的字段集合，`...Frag`）——**Fragment 是客户端复用查询片段的关键**。
- **别名（alias）解决"同字段多次取不同参数"**：`a: product(id:1) { name } b: product(id:2) { name }`——**响应里用别名作键**，这是 GraphQL 灵活性的来源。
- **变量（variables）是参数化的关键**：查询里写 `product(id: $id)`，变量单独传——**避免字符串拼接（注入风险 75-09）**，也便于服务端做持久化查询。
- 与 64/70 章的关系：变量等同于 REST 的参数化（64-03），**查询字符串本身不应含用户数据**。
- 易错点：用字符串拼接把用户输入塞进 query（注入）；不用变量（每个请求生成新查询，缓存失效）；不处理 alias（同字段多参数写不出来）；不支持 Fragment（客户端查询冗长）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryParsing {
    sealed interface Node permits Field, FragmentSpread {
    }

    record Field(String alias, String name, Map<String, String> args, List<Node> children)
            implements Node {
        String key() {
            return alias != null ? alias : name;
        }
    }

    record FragmentSpread(String name) implements Node {
    }

    /** 极简解析：支持 { a: field(arg: "v") { sub } ...Frag } */
    static class Parser {
        final String src;
        int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        String readName() {
            skipWs();
            int start = pos;
            while (pos < src.length()
                    && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                pos++;
            }
            return src.substring(start, pos);
        }

        String peek() {
            skipWs();
            return pos < src.length() ? String.valueOf(src.charAt(pos)) : "";
        }

        void expect(String c) {
            skipWs();
            if (pos >= src.length() || !src.startsWith(c, pos)) {
                throw new IllegalArgumentException("期望 " + c + " 于 " + pos);
            }
            pos += c.length();
        }

        List<Node> parseSelectionSet() {
            expect("{");
            List<Node> nodes = new ArrayList<>();
            while (true) {
                skipWs();
                if (pos >= src.length() || src.charAt(pos) == '}') {
                    break;
                }
                if (src.startsWith("...", pos)) {
                    pos += 3;
                    nodes.add(new FragmentSpread(readName()));
                    continue;
                }
                String first = readName();
                skipWs();
                String alias = null;
                String name = first;
                if (pos < src.length() && src.charAt(pos) == ':') {
                    pos++;
                    alias = first;
                    name = readName();
                    skipWs();
                }
                Map<String, String> args = new LinkedHashMap<>();
                if (pos < src.length() && src.charAt(pos) == '(') {
                    pos++;
                    while (pos < src.length() && src.charAt(pos) != ')') {
                        String k = readName();
                        expect(":");
                        skipWs();
                        String v;
                        if (pos < src.length() && src.charAt(pos) == '"') {
                            pos++;
                            int end = src.indexOf('"', pos);
                            v = src.substring(pos, end);
                            pos = end + 1;
                        } else {
                            v = readName();
                        }
                        args.put(k, v);
                        skipWs();
                        if (pos < src.length() && src.charAt(pos) == ',') {
                            pos++;
                        }
                    }
                    expect(")");
                }
                skipWs();
                List<Node> children = new ArrayList<>();
                if (pos < src.length() && src.charAt(pos) == '{') {
                    children = parseSelectionSet();
                }
                nodes.add(new Field(alias, name, args, children));
            }
            expect("}");
            return nodes;
        }
    }

    static void printTree(List<Node> nodes, int depth) {
        for (Node n : nodes) {
            String pad = "  ".repeat(depth);
            if (n instanceof Field f) {
                System.out.printf("%s%s%s%s%n", pad,
                        f.alias() == null ? "" : f.alias() + ": ",
                        f.name(), f.args().isEmpty() ? "" : " " + f.args());
                printTree(f.children(), depth + 1);
            } else if (n instanceof FragmentSpread fs) {
                System.out.printf("%s...%s%n", pad, fs.name());
            }
        }
    }

    public static void main(String[] args) {
        String query = """
                {
                  a: product(id: "1") {
                    name
                    price
                    brand { name }
                  }
                  b: product(id: "2") { name }
                  ...Common
                }
                """;
        System.out.println("【原始查询】");
        query.lines().forEach(l -> System.out.println("  " + l));
        List<Node> ast = new Parser(query).parseSelectionSet();
        System.out.println("【AST】");
        printTree(ast, 1);

        System.out.println();
        System.out.println("【要点】");
        System.out.println("  1) 别名字段：a/b 作为响应键（同字段不同参数）");
        System.out.println("  2) 子选择集：brand { name } 只取品牌名字");
        System.out.println("  3) Fragment：...Common 复用字段集合");
        System.out.println("  4) 参数应通过变量传递（$id），而不是字符串拼接（75-09）");
    }
}
```

**课后练习**
1. 扩展解析器：支持变量声明（`query Q($id: ID!)`）与变量引用（`id: $id`），用别名查询验证。
2. 实现 Fragment 展开：把 `...Common` 替换为对应字段集，输出完全展开后的 AST。
3. 思考题：为什么"用户输入拼进查询字符串"是注入漏洞？从"查询语法即代码"分析。

---

### 75-04 执行引擎与 Resolver

**知识点**
- **执行引擎的工作**：按 AST 逐字段调用 **Resolver**（`resolve(parent, args, context)`），**返回值的形状必须匹配 schema**——对象字段递归调用其子字段 resolver，标量直接返回。
- **Resolver 的四类实现**：**内存取数**（已加载的对象字段）、**服务调用**（RPC/HTTP 调下游）、**数据加载**（DB 查询，N+1 风险点 75-05）、**计算派生**（字段由其他字段算出）。
- **Context 贯穿整个请求**：放认证信息（74 章）、请求 ID（42 章 trace）、数据加载器（75-05）——**resolver 不直接依赖全局状态**。
- **执行顺序**：**同一层的字段并行执行**（可并发取数），**父子串行**（父 resolve 出对象才能取子字段）——**这决定了 N+1 的形态**（父返回 N 个对象，每个对象的子字段各查一次）。
- 与 64-09 的关系：GraphQL 的错误是"字段级"的（部分成功 75-07），不是"整体失败"——**resolver 抛错只影响该字段**。
- 易错点：resolver 里做重活（没缓存/没批处理）；resolver 返回类型与 schema 不符（运行期报错）；context 无 traceId（排障困难）；把业务逻辑写进 resolver（应该调服务层）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExecutionEngine {
    record Product(long id, String name, double price, long brandId) {
    }

    record Brand(long id, String name) {
    }

    record Review(long productId, int score, String content) {
    }

    /** 模拟数据源 */
    static final Map<Long, Product> productDb = new LinkedHashMap<>();
    static final Map<Long, Brand> brandDb = new LinkedHashMap<>();
    static final List<Review> reviewDb = new ArrayList<>();

    static void seed() {
        productDb.put(1L, new Product(1, "手机壳", 69.0, 10));
        productDb.put(2L, new Product(2, "蓝牙耳机", 129.0, 10));
        brandDb.put(10L, new Brand(10, "自有品牌"));
        reviewDb.add(new Review(1, 5, "好用"));
        reviewDb.add(new Review(1, 4, "一般"));
        reviewDb.add(new Review(2, 5, "音质好"));
    }

    /** Resolver 注册表：字段路径 -> 取数函数 */
    interface Resolver {
        Object resolve(Object parent, Map<String, String> args, Map<String, Object> ctx);
    }

    static final Map<String, Resolver> resolvers = new LinkedHashMap<>();

    static void register() {
        resolvers.put("Query.product", (p, a, ctx) -> {
            long id = Long.parseLong(a.getOrDefault("id", "0"));
            Map<String, Object> stats = stats(ctx);
            stats.put("dbQuery", (Integer) stats.getOrDefault("dbQuery", 0) + 1);
            return productDb.get(id);
        });
        resolvers.put("Product.brand", (p, a, ctx) -> {
            Product prod = (Product) p;
            Map<String, Object> stats = stats(ctx);
            stats.put("dbQuery", (Integer) stats.getOrDefault("dbQuery", 0) + 1);                     // N+1 发生点
            return brandDb.get(prod.brandId());
        });
        resolvers.put("Product.reviews", (p, a, ctx) -> {
            Product prod = (Product) p;
            Map<String, Object> stats = stats(ctx);
            stats.put("dbQuery", (Integer) stats.getOrDefault("dbQuery", 0) + 1);                     // N+1 发生点
            return reviewDb.stream().filter(r -> r.productId() == prod.id()).toList();
        });
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> stats(Map<String, Object> ctx) {
        return (Map<String, Object>) ctx.computeIfAbsent("stats",
                k -> new LinkedHashMap<String, Object>());
    }

    /** 简化执行：product(id){name price brand{name} reviews{score}} */
    static Map<String, Object> execute(long productId, boolean wantBrand,
                                       boolean wantReviews, Map<String, Object> ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, String> args = Map.of("id", String.valueOf(productId));
        Object p = resolvers.get("Query.product").resolve(null, args, ctx);
        if (p == null) {
            out.put("product", null);
            return out;
        }
        Product prod = (Product) p;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", prod.name());
        fields.put("price", prod.price());
        if (wantBrand) {
            Object b = resolvers.get("Product.brand").resolve(prod, Map.of(), ctx);
            fields.put("brand", b == null ? null
                    : Map.of("name", ((Brand) b).name()));
        }
        if (wantReviews) {
            Object rs = resolvers.get("Product.reviews").resolve(prod, Map.of(), ctx);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object o : (List<?>) rs) {
                list.add(Map.of("score", ((Review) o).score()));
            }
            fields.put("reviews", list);
        }
        out.put("product", fields);
        return out;
    }

    public static void main(String[] args) {
        seed();
        register();

        Map<String, Object> ctx = new LinkedHashMap<>();
        System.out.println("【执行：product(1) { name price brand{name} reviews{score} }】");
        System.out.println("  结果：" + execute(1, true, true, ctx));
        System.out.println("  DB 查询次数：" + stats(ctx).get("dbQuery"));

        System.out.println();
        Map<String, Object> ctx2 = new LinkedHashMap<>();
        System.out.println("【执行：product(1) { name price }（不取关联）】");
        System.out.println("  结果：" + execute(1, false, false, ctx2));
        System.out.println("  DB 查询次数：" + stats(ctx2).get("dbQuery") + "（只查主表）");

        System.out.println();
        System.out.println("【执行顺序】同层字段可并行；父子串行（先有 product 才能取 brand）");
        System.out.println("  N+1 形态：父返回 N 个对象 -> 每个对象的 brand 各查一次（75-05 解决）");
    }
}
```

**课后练习**
1. 扩展执行引擎：支持"列表查询 products(first: 10) + 每项取 brand"，统计 DB 查询次数（观察 N+1）。
2. 把 resolver 的"数据访问"抽成服务层调用，说明"业务逻辑不进 resolver"的收益。
3. 思考题：为什么"同层字段可并行"？从"父对象已就绪"与"字段间无依赖"分析。

---

### 75-05 N+1 与 DataLoader

**知识点**
- **N+1 是 GraphQL 的头号性能杀手**：查 10 个商品（1 次）-> 每个商品取 brand（10 次）-> **共 11 次查询**；嵌套两层则 N×M——**REST 手写接口可以一次 join，GraphQL 的字段级取数天然分散**。
- **DataLoader 的解法：批量化 + 缓存**——把**同一事件循环内的多次单个请求合并成一次批量请求**（`load(id)` 收集 -> `loadMany(ids)` 一次查库），**请求级缓存**避免同请求内重复加载。
- **DataLoader 的两个保证**：**批量**（同一 tick 的请求合并）与**缓存**（同 key 只查一次）——**实现关键**：延迟到下一微任务统一执行（`queueMicrotask`/`CompletableFuture`）。
- **批量化要求"取数接口支持批量"**：`findByIds(List<Long>)` 而不是 `findById(Long)`——**下游服务要提供批量接口**（没有就自己 `IN` 查询）。
- 与 54/46 章的关系：与推荐的"批量特征加载"、大数据的"列裁剪"同源——**减少往返是通用优化**（39-02 的批量化思想）。
- 易错点：不用 DataLoader（N+1）；DataLoader 的缓存跨请求复用（数据陈旧）；批量接口无上限（一次 IN 10 万）；嵌套 DataLoader 未分页（75-08 的复杂度）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataLoaderDemo {
    record Product(long id, String name, long brandId) {
    }

    record Brand(long id, String name) {
    }

    static final List<Product> products = List.of(
            new Product(1, "手机壳", 10),
            new Product(2, "耳机", 10),
            new Product(3, "充电器", 20),
            new Product(4, "数据线", 20),
            new Product(5, "支架", 30));

    static final Map<Long, Brand> brands = new LinkedHashMap<>();

    static {
        brands.put(10L, new Brand(10, "自有品牌"));
        brands.put(20L, new Brand(20, "第三方"));
        brands.put(30L, new Brand(30, "配件厂"));
    }

    /** 模拟 DB：按 id 批量查（DataLoader 需要的下游能力） */
    static Map<Long, Brand> findBrandsByIds(Set<Long> ids) {
        System.out.printf("    [DB] 批量查询品牌 %s%n", ids);
        Map<Long, Brand> out = new LinkedHashMap<>();
        for (Long id : ids) {
            Brand b = brands.get(id);
            if (b != null) {
                out.put(id, b);
            }
        }
        return out;
    }

    /** DataLoader：收集 -> 批量 -> 分配 */
    static class BrandLoader {
        final Set<Long> queue = new LinkedHashSet<>();
        final Map<Long, Brand> cache = new LinkedHashMap<>();
        boolean scheduled = false;

        /** 单个加载：先入队，返回延迟结果（这里用同步 flush 简化演示合并效果） */
        Brand load(long brandId) {
            if (cache.containsKey(brandId)) {
                System.out.printf("    [cache] 命中品牌 %d%n", brandId);
                return cache.get(brandId);
            }
            queue.add(brandId);
            if (!scheduled) {
                scheduled = true;
            }
            return null;                     // 真实实现返回 CompletableFuture
        }

        /** 在事件循环末尾调用：一次批量取回所有入队的 key */
        void dispatch() {
            if (queue.isEmpty()) {
                return;
            }
            Map<Long, Brand> loaded = findBrandsByIds(queue);
            cache.putAll(loaded);
            queue.clear();
            scheduled = false;
        }
    }

    /** 不用 DataLoader：每个 product 单独查 */
    static void withoutLoader() {
        System.out.println("【不用 DataLoader（N+1）】");
        int queries = 1;                     // 查商品列表
        for (Product p : products) {
            System.out.printf("    [DB] 查品牌 %d（商品 %s）%n", p.brandId(), p.name());
            queries++;
        }
        System.out.printf("  查询次数：%d（1 + %d）%n", queries, products.size());
        System.out.println();
    }

    /** 用 DataLoader：先全部 load（入队），再 dispatch 一次 */
    static void withLoader() {
        System.out.println("【用 DataLoader（批量 + 缓存）】");
        BrandLoader loader = new BrandLoader();
        int queries = 1;                     // 查商品列表
        for (Product p : products) {
            loader.load(p.brandId());
        }
        System.out.println("  阶段一：收集完 5 个 brandId（尚未查库）");
        loader.dispatch();
        queries++;
        System.out.println("  阶段二：一次批量查回所有品牌");
        for (Product p : products) {
            Brand b = loader.load(p.brandId());       // 全部命中缓存
            System.out.printf("    商品 %-6s -> 品牌 %s%n", p.name(), b.name());
        }
        System.out.printf("  查询次数：%d（1 + 1）%n", queries);
        System.out.println();
    }

    public static void main(String[] args) {
        withoutLoader();
        withLoader();

        System.out.println("【要点】");
        System.out.println("  1) DataLoader = 批量化 + 请求级缓存");
        System.out.println("  2) 下游需提供批量接口（findByIds / IN 查询）");
        System.out.println("  3) 缓存只在单次请求内有效（请求结束销毁，避免脏数据）");
        System.out.println("  4) 批量要有上限（IN 10 万会打挂 DB，需分批）");
    }
}
```

**课后练习**
1. 实现 DataLoader：收集/批量/缓存，用"5 个商品取 brand"验证查询次数从 6 降到 2。
2. 扩展为两层嵌套（product -> brand -> parentBrand），观察 N×M 并说明 DataLoader 如何逐层批量化。
3. 思考题：为什么"DataLoader 缓存不能跨请求"？从"数据更新后读到旧值"分析。

---

### 75-06 Mutation 与订阅

**知识点**
- **Query 只读、Mutation 写**：**Mutation 的字段串行执行**（同一请求里多个 mutation 按顺序，避免竞争）——**与 Query 的并行相反**，这是规范要求。
- **Mutation 的输入用输入类型**（75-02）：`createProduct(input: ProductInput!)`——**参数集中、便于校验（24-02 同款）**、便于演进（加字段不破坏签名）。
- **Mutation 的返回值要"够用"**：返回创建后的对象（含 id）或受影响的数量——**客户端常需要"写后立即读"**（45-02 的 read-your-writes），返回对象可省一次查询。
- **订阅（Subscription）是长连接推送**：基于 WebSocket（65-02 的 gRPC/WS 之外的选择），适合"数据变化通知"；**生产上更常用 MQ/SSE 替代**（43/65 章）——**订阅是 GraphQL 里最少用的部分**。
- 与 64 章的关系：Mutation 等于 REST 的 POST/PUT/DELETE，**幂等键、版本兼容、错误码规范同样适用**（39/64-05）。
- 易错点：Mutation 幂等键缺失（重试导致重复创建）；多个 mutation 放一个请求（部分失败语义复杂）；用订阅做业务事件总线（不可靠，应走 MQ）；Mutation 返回全量对象（响应过大）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MutationDemo {
    record ProductInput(String name, double price, String idempotencyKey) {
    }

    record Product(long id, String name, double price) {
    }

    static final Map<String, Product> idemIndex = new LinkedHashMap<>();
    static final List<Product> products = new ArrayList<>();
    static long nextId = 100;

    /** Mutation：幂等 + 校验 + 返回创建对象 */
    static Map<String, Object> createProduct(ProductInput input) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 1) 幂等
        Product existing = idemIndex.get(input.idempotencyKey());
        if (existing != null) {
            result.put("product", existing);
            result.put("idempotent", true);
            return result;
        }
        // 2) 校验
        List<String> errors = new ArrayList<>();
        if (input.name() == null || input.name().isBlank()) {
            errors.add("name 必填");
        }
        if (input.price() <= 0) {
            errors.add("price 必须大于 0");
        }
        if (!errors.isEmpty()) {
            result.put("errors", errors);
            return result;
        }
        // 3) 写库
        Product p = new Product(++nextId, input.name(), input.price());
        products.add(p);
        idemIndex.put(input.idempotencyKey(), p);
        result.put("product", p);
        result.put("idempotent", false);
        return result;
    }

    /** 订阅：基于事件的推送（简化演示） */
    static class EventBus {
        final Map<String, List<Map<String, Object>>> mailbox = new LinkedHashMap<>();

        void subscribe(String clientId, String topic) {
            mailbox.computeIfAbsent(clientId, k -> new ArrayList<>());
            System.out.printf("  客户端 %s 订阅 %s%n", clientId, topic);
        }

        void publish(String topic, Object payload) {
            System.out.printf("  发布 %s：%s%n", topic, payload);
            mailbox.forEach((client, list) ->
                    list.add(Map.of("topic", topic, "payload", payload)));
        }

        void drain(String clientId) {
            System.out.printf("  客户端 %s 收到：%s%n", clientId,
                    mailbox.getOrDefault(clientId, List.of()));
            mailbox.getOrDefault(clientId, List.of()).clear();
        }
    }

    public static void main(String[] args) {
        System.out.println("【Mutation：创建商品】");
        Map<String, Object> r1 = createProduct(new ProductInput("手机壳", 69.0, "idem-1"));
        System.out.println("  " + r1);
        System.out.println("  重复提交同一 idempotencyKey：");
        Map<String, Object> r2 = createProduct(new ProductInput("手机壳", 69.0, "idem-1"));
        System.out.println("  " + r2 + "（幂等命中，未新建）");

        System.out.println();
        System.out.println("【校验失败】");
        System.out.println("  " + createProduct(new ProductInput("", -1, "idem-2")));

        System.out.println();
        System.out.println("【多个 Mutation 串行执行】");
        List<ProductInput> batch = List.of(
                new ProductInput("耳机", 129.0, "idem-3"),
                new ProductInput("充电器", 39.0, "idem-4"));
        for (ProductInput in : batch) {
            createProduct(in);
            System.out.println("  执行下一个（串行，非并行）");
        }
        System.out.printf("  当前商品数：%d%n", products.size());

        System.out.println();
        System.out.println("【订阅（长连接推送）】");
        EventBus bus = new EventBus();
        bus.subscribe("client-1", "product.created");
        bus.publish("product.created", "手机壳");
        bus.drain("client-1");

        System.out.println();
        System.out.println("  规范：Query 并行 / Mutation 串行；生产推送更常用 MQ 或 SSE（43/65 章）");
    }
}
```

**课后练习**
1. 实现 Mutation：幂等键 + 校验 + 返回对象，用"重复提交/校验失败/正常"三场景验证。
2. 实现订阅的简化版：客户端订阅 + 事件发布 + 消息投递，说明为何生产上更常用 MQ。
3. 思考题：为什么规范要求"Mutation 串行执行"？从"写操作竞争"与"客户端预期"分析。

---

### 75-07 错误处理与部分成功

**知识点**
- **GraphQL 的错误是"字段级"的**：响应包含 `data`（部分成功的数据）+ `errors`（失败字段的错误列表）——**一个字段失败不影响其他字段**，这是与 REST"整体成功/失败"最大的差异。
- **错误对象的结构**：`message`（人读）、`path`（出错字段路径）、`locations`（查询位置）、`extensions.code`（机器码 64-09）——**客户端按 path 定位、按 code 分支**。
- **两种错误来源**：**字段解析失败**（resolver 抛错，字段置 null 并进 errors）与**请求级错误**（语法错误/校验失败，无 data 只有 errors）——**要区分处理**。
- **非空字段错误的传播**：`name: String!` 的 resolver 返回 null -> **错误向上冒泡，最近的"可空祖先"被置 null**——**这就是"一个 null 毁掉整棵树"的机制**（75-02 的非空纪律）。
- 与 64-09 的关系：错误码体系（`extensions.code`）与 REST 的 `code` 同构——**GraphQL 只是把错误放进了 `errors` 数组**。
- 易错点：所有错误都抛 500（丢失字段级信息）；不设 `extensions.code`（客户端无法程序化处理）；非空字段返回 null（整树变 null）；把内部堆栈塞进 message（36 章泄露）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ErrorHandling {
    record GraphQLError(String message, List<String> path, String code,
                        Map<String, Object> extensions) {
        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("message", message);
            m.put("path", path);
            m.put("extensions", Map.of("code", code));
            return m;
        }
    }

    record Response(Map<String, Object> data, List<GraphQLError> errors) {
        String describe() {
            return "data=" + data + " errors=" + (errors.isEmpty() ? "[]"
                    : errors.stream().map(GraphQLError::code).toList());
        }
    }

    static void partialSuccess() {
        System.out.println("【部分成功：一个字段失败，其余正常】");
        Map<String, Object> data = new LinkedHashMap<>();
        List<GraphQLError> errors = new ArrayList<>();
        // product.name 成功
        data.put("name", "手机壳");
        // product.price 成功
        data.put("price", 69.0);
        // product.brand 失败（下游超时）
        data.put("brand", null);
        errors.add(new GraphQLError("品牌服务超时", List.of("product", "brand"),
                "DOWNSTREAM_TIMEOUT", Map.of("service", "brand")));
        // product.reviews 成功
        data.put("reviews", List.of(Map.of("score", 5)));
        Response r = new Response(data, errors);
        System.out.println("  " + r.describe());
        System.out.println("  客户端：能展示的展示，brand 位置显示占位符");
        errors.forEach(e -> System.out.println("    错误 " + e.toJson()));
        System.out.println();
    }

    static void nonNullBubbling() {
        System.out.println("【非空字段错误的冒泡】");
        System.out.println("  schema: product { name: String!  brand: Brand }");
        System.out.println("  若 name（非空）的 resolver 返回 null：");
        System.out.println("    name 报错 -> 冒泡到最近的“可空祖先” product -> product 变 null");
        System.out.println("    -> 整棵树 data.product = null（其他顶层字段不受影响）");
        System.out.println();
        System.out.println("  若 brand（可空）返回 null：只断在该字段，product 仍完整");
        System.out.println("  教训：非空要克制（75-02）");
        System.out.println();
    }

    static void requestLevelError() {
        System.out.println("【请求级错误（无 data）】");
        Response r = new Response(null, List.of(
                new GraphQLError("字段 pricee 不存在", List.of(), "GRAPHQL_VALIDATION_FAILED",
                        Map.of("suggestion", "price"))));
        System.out.println("  " + r.describe());
        System.out.println("  这类错误在解析/校验阶段就发生，不执行任何 resolver");
        System.out.println();
    }

    static void codeTable() {
        System.out.println("【错误码约定（与 64-09 对齐）】");
        String[][] rows = {
                {"UNAUTHENTICATED", "401", "未登录/令牌无效"},
                {"FORBIDDEN", "403", "已登录但无权限（74 章）"},
                {"NOT_FOUND", "404", "资源不存在或越权（统一 404）"},
                {"VALIDATION_FAILED", "400", "参数校验失败"},
                {"DOWNSTREAM_TIMEOUT", "504", "下游超时（可重试）"},
                {"INTERNAL", "500", "未预期错误（不暴露堆栈）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-20s HTTP %-4s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  规范：extensions.code 稳定（客户端分支），message 给人看，不泄露内部信息");
    }

    public static void main(String[] args) {
        partialSuccess();
        nonNullBubbling();
        requestLevelError();
        codeTable();
    }
}
```

**课后练习**
1. 实现"部分成功"响应：3 个字段中 1 个失败，输出 data + errors，验证客户端可定位。
2. 演示非空字段冒泡：`name: String!` 返回 null 时 product 变 null，对比可空字段的行为。
3. 思考题：为什么 GraphQL 的"部分成功"对前端更友好但更难监控？从"错误分散"与"成功率统计"分析。

---

### 75-08 深度与复杂度限制

**知识点**
- **GraphQL 的独特攻击面：查询形状由客户端决定**——一个深层嵌套查询（`product { brand { products { brand ... } } }`）能让服务端**指数级放大**查询量，**比 REST 的"固定接口"危险得多**。
- **三种限制**：**深度限制**（maxDepth，如 10 层）、**复杂度限制**（每个字段权重 × 数量求和，如 1000 分）、**时间限制**（执行超时）——**三者组合**，缺一个都能被绕。
- **复杂度计算**：给字段配权重（标量 1、列表 10、关联 20），查询时乘上 `first` 参数的倍数——**`products(first: 1000) { reviews(first: 1000) }` = 100 万条**，必须按"参数 × 子字段"累计。
- **与 69-04 的关系**：广告预算是"钱"的限制，GraphQL 复杂度是"算力"的限制——**同一种"防止单请求拖垮系统"的思想**（44-08 限流的查询版）。
- **持久化查询（Persisted Query）是更彻底的方案**：客户端只发查询 ID（服务端存好查询），**无法构造任意查询**——适合自有客户端（App/Web），开放平台仍需复杂度限制。
- 易错点：只限深度不限复杂度（宽查询绕过）；复杂度不乘列表参数（`first: 100000`）；不做超时（慢查询拖垮）；生产开内省（75-09 泄露 schema）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryComplexity {
    /** 字段权重：标量 1、列表 10、关联 20 */
    static final Map<String, Integer> fieldWeight = new LinkedHashMap<>();

    static {
        fieldWeight.put("id", 1);
        fieldWeight.put("name", 1);
        fieldWeight.put("price", 1);
        fieldWeight.put("products", 10);
        fieldWeight.put("brand", 20);
        fieldWeight.put("reviews", 20);
    }

    record FieldNode(String name, Integer first, List<FieldNode> children) {
    }

    /** 计算复杂度：字段权重 × 列表倍数 的递归求和 */
    static int complexity(FieldNode node) {
        int base = fieldWeight.getOrDefault(node.name(), 5);
        int multiplier = node.first() == null ? 1 : Math.max(1, node.first());
        int self = base * multiplier;
        int children = 0;
        for (FieldNode c : node.children()) {
            children += complexity(c);
        }
        return self + children * multiplier;
    }

    static int depth(FieldNode node) {
        int max = 0;
        for (FieldNode c : node.children()) {
            max = Math.max(max, depth(c));
        }
        return 1 + max;
    }

    static final int MAX_DEPTH = 8;
    static final int MAX_COMPLEXITY = 5000;

    static String validate(FieldNode q) {
        int d = depth(q);
        int c = complexity(q);
        System.out.printf("  查询 %-40s 深度=%-2d 复杂度=%-6d -> ", q.name() + "…", d, c);
        if (d > MAX_DEPTH) {
            return "拒绝：深度超限（" + d + " > " + MAX_DEPTH + "）";
        }
        if (c > MAX_COMPLEXITY) {
            return "拒绝：复杂度超限（" + c + " > " + MAX_COMPLEXITY + "）";
        }
        return "通过";
    }

    public static void main(String[] args) {
        System.out.println("【正常查询】");
        FieldNode normal = new FieldNode("products", 10, List.of(
                new FieldNode("name", null, List.of()),
                new FieldNode("price", null, List.of()),
                new FieldNode("brand", null, List.of(
                        new FieldNode("name", null, List.of())))));
        System.out.println("  " + validate(normal));

        System.out.println();
        System.out.println("【宽查询：first 放大】");
        FieldNode wide = new FieldNode("products", 1000, List.of(
                new FieldNode("name", null, List.of()),
                new FieldNode("reviews", 1000, List.of(
                        new FieldNode("name", null, List.of())))));
        System.out.println("  " + validate(wide));

        System.out.println();
        System.out.println("【深查询：嵌套】");
        FieldNode deep = new FieldNode("products", 10, List.of(
                new FieldNode("brand", null, List.of(
                        new FieldNode("products", 10, List.of(
                                new FieldNode("brand", null, List.of(
                                        new FieldNode("products", 10, List.of(
                                                new FieldNode("brand", null,
                                                        List.of())))))))))));
        System.out.println("  " + validate(deep));

        System.out.println();
        System.out.println("【复杂度模型说明】");
        System.out.println("  complexity(node) = weight(node) × first(node)");
        System.out.println("                   + Σ complexity(child) × first(node)");
        System.out.println("  关键：列表参数会放大子字段的权重（嵌套列表是乘数）");

        System.out.println();
        System.out.println("【三道防线】");
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("深度限制", "maxDepth 8~10，防深层嵌套");
        lines.put("复杂度限制", "权重 × 参数 累计，超阈值拒绝（建议 1000~5000）");
        lines.put("执行超时", "单请求 ≤ 3s，超时终止");
        lines.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println();
        System.out.println("  更彻底：持久化查询（客户端只发查询 ID，75-09）");
    }
}
```

**课后练习**
1. 实现复杂度计算器（权重 × first 递归求和），用"正常/宽/深"三个查询验证拦截。
2. 加入"列表参数放大"测试：`products(first: 100000)` 单层是否被拒，说明为何只看深度不够。
3. 思考题：为什么"持久化查询"比"复杂度限制"更安全？从"无法构造任意查询"分析。

---

### 75-09 安全：内省、批量与注入

**知识点**
- **内省（Introspection）泄露 schema**：`__schema` 能导出全部类型与字段——**生产环境通常关闭**（或仅内部可用），但**关闭后客户端代码生成要离线做**（75-02）。
- **批量攻击（Batching）**：把 1000 个查询塞进一个请求（`[{query},{query}...]`）——**绕过单请求限流**；**对策**：限制批量数量（如 ≤10）+ 按 operation 数计复杂度。
- **注入风险**：**用户输入拼进查询字符串**（`product(name: "` + input + `")`）——与 SQL 注入同源（36-02）；**用变量（variables）传值**（75-03）。
- **敏感字段与权限**：GraphQL 的字段级权限要**在 resolver 里校验**（74-04 统一入口），不能只靠"客户端不查就行"——**攻击者可以构造任意查询**。
- **与 36 章的关系**：GraphQL 的注入/越权/DoS 与 REST 同源，**只是攻击面变成了"查询形状"**——安全原则不变（输入校验、最小权限、限流）。
- 易错点：生产开内省（schema 泄露，攻击者按图索骥）；不限制批量（限流被绕过）；字段无权限校验（构造查询读敏感字段）；错误信息暴露 schema 细节。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphQLSecurity {
    /** 内省开关 */
    static boolean introspectionEnabled = false;

    static String handleIntrospection(String query) {
        if (query.contains("__schema") || query.contains("__type")) {
            if (!introspectionEnabled) {
                return "拒绝：内省已关闭（生产环境）";
            }
            return "返回 schema 定义（仅开发环境）";
        }
        return "非内省查询";
    }

    /** 批量限制 */
    static final int MAX_BATCH = 10;

    static String handleBatch(List<String> queries) {
        if (queries.size() > MAX_BATCH) {
            return "拒绝：批量过大（" + queries.size() + " > " + MAX_BATCH + "）";
        }
        int totalComplexity = queries.size() * 100;          // 简化：每查询 100
        return "通过：批量 " + queries.size() + " 个，总复杂度 " + totalComplexity;
    }

    /** 注入防护：变量 vs 字符串拼接 */
    static String buildQuery(String userInput, boolean useVariable) {
        if (useVariable) {
            return "query($name: String) { products(name: $name) { id } }  variables={name:\""
                    + userInput + "\"}";
        }
        return "query { products(name: \"" + userInput + "\") { id } }";
    }

    static void injectionDemo() {
        System.out.println("【注入对比】");
        String evil = "\") { id } } query { secret { token } } query { x: products(name: \"";
        System.out.println("  恶意输入：" + evil);
        System.out.println("  拼字符串 -> " + buildQuery(evil, false));
        System.out.println("    查询被截断并注入新字段（数据泄露）");
        System.out.println("  用变量  -> " + buildQuery(evil, true));
        System.out.println("    恶意内容只是 name 的字符串值（安全）");
        System.out.println();
    }

    /** 字段级权限：resolver 内校验 */
    record UserAccount(String id, Set<String> perms) {
    }

    static String resolveField(UserAccount user, String field) {
        String required = switch (field) {
            case "price" -> "product:read:price";
            case "cost" -> "product:read:cost";
            default -> "product:read";
        };
        if (!user.perms().contains(required)) {
            return "字段 " + field + " -> 403（缺 " + required + "）";
        }
        return "字段 " + field + " -> 返回数据";
    }

    public static void main(String[] args) {
        System.out.println("【内省】");
        System.out.println("  开发环境：" + handleIntrospection("{__schema{types{name}}}"));
        introspectionEnabled = true;
        System.out.println("  开发环境（已开）：" + handleIntrospection("{__schema{types{name}}}"));
        introspectionEnabled = false;
        System.out.println("  生产环境：" + handleIntrospection("{__schema{types{name}}}"));

        System.out.println();
        System.out.println("【批量限制】");
        System.out.println("  " + handleBatch(List.of("q1", "q2", "q3")));
        System.out.println("  " + handleBatch(fill(20)));

        System.out.println();
        injectionDemo();

        System.out.println("【字段级权限】");
        UserAccount staff = new UserAccount("u-1", Set.of("product:read"));
        UserAccount manager = new UserAccount("u-2",
                Set.of("product:read", "product:read:price", "product:read:cost"));
        for (String f : new String[]{"name", "price", "cost"}) {
            System.out.println("  staff   " + resolveField(staff, f));
        }
        for (String f : new String[]{"price", "cost"}) {
            System.out.println("  manager " + resolveField(manager, f));
        }

        System.out.println();
        System.out.println("【安全清单】关闭内省 / 限制批量 / 用变量 / 字段级鉴权 / 错误不暴露结构");
    }

    static List<String> fill(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add("q" + i);
        }
        return out;
    }
}
```

**课后练习**
1. 实现三项防护：内省开关/批量限制/变量传值，用 4 个场景验证拦截效果。
2. 实现字段级权限校验（resolver 内），用"缺 price 权限的用户构造含 price 的查询"验证 403。
3. 思考题：为什么"客户端不查敏感字段"不能当安全边界？从"攻击者可以构造任意查询"分析。

---

### 75-10 BFF 与联邦架构

**知识点**
- **BFF（Backend For Frontend）是 GraphQL 最典型的落点**：**每个前端（App/Web/小程序）一个 BFF**，BFF 用 GraphQL 聚合后端多个微服务的 REST/RPC，**前端只面对一个"按需取数"的端点**。
- **BFF 的价值**：**聚合**（一次请求拿多个服务的数据）、**裁剪**（只返回前端要的字段）、**适配**（不同端不同结构）、**隔离**（后端服务不感知前端差异）——**与 50-11 的聚合网关同源，只是查询形状可声明**。
- **联邦（Federation）解决"多个团队各自维护子图"**：每个团队发布自己的子 schema（Product/Brand/Review），网关**合并成一个超级图**并按字段路由到对应子图——**避免"一个巨型 schema 由一个团队维护"**（75-01 的反模式）。
- **与 29/50 章的关系**：BFF 是"API 组合层"，联邦是"schema 组合层"——**两者都在解决"前端需要的数据散落在多个服务"**（50-11 聚合网关的 GraphQL 版）。
- 易错点：BFF 里写业务逻辑（变成第二个后端）；联邦的实体定义不一致（同一实体在不同子图字段冲突）；BFF 无缓存（每次都穿透下游）；一个 BFF 服务所有端（又变成巨型 schema）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BffFederation {
    /** 子图：团队 -> 它能提供的类型与字段 */
    record Subgraph(String team, String type, List<String> fields) {
    }

    static final List<Subgraph> subgraphs = new ArrayList<>();

    static void seed() {
        subgraphs.add(new Subgraph("商品团队", "Product", List.of("id", "name", "price")));
        subgraphs.add(new Subgraph("品牌团队", "Product", List.of("brand")));
        subgraphs.add(new Subgraph("评价团队", "Product", List.of("reviews")));
        subgraphs.add(new Subgraph("库存团队", "Product", List.of("stock")));
    }

    /** 联邦：合并超级图 + 字段路由表 */
    static Map<String, String> buildRouting() {
        Map<String, String> routing = new LinkedHashMap<>();
        for (Subgraph s : subgraphs) {
            for (String f : s.fields()) {
                String key = s.type() + "." + f;
                if (routing.containsKey(key)) {
                    System.out.printf("  冲突：%s 被 %s 与 %s 同时声明 -> 需联邦评审%n",
                            key, routing.get(key), s.team());
                } else {
                    routing.put(key, s.team());
                }
            }
        }
        return routing;
    }

    /** BFF 聚合：按查询字段分派到对应团队 */
    static Map<String, Object> query(Map<String, String> routing, List<String> fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        int calls = 0;
        for (String f : fields) {
            String team = routing.getOrDefault("Product." + f, "未知");
            if (team.equals("未知")) {
                out.put(f, null);
                System.out.printf("    %-8s -> 无子图提供（返回 null）%n", f);
                continue;
            }
            out.put(f, "[" + team + " 提供] " + f + " 的值");
            calls++;
            System.out.printf("    %-8s -> 路由到 %-8s 调用 %d%n", f, team, calls);
        }
        System.out.printf("  合计 %d 次下游调用（同团队字段可合并为 1 次）%n", calls);
        return out;
    }

    static void bffVsApiGateway() {
        System.out.println("【BFF vs API 网关】");
        String[][] rows = {
                {"职责", "路由/限流/鉴权", "聚合/裁剪/适配"},
                {"取数形状", "固定（按接口）", "客户端声明"},
                {"变更频率", "低", "随前端需求变化"},
                {"典型技术", "REST 网关（64-08）", "GraphQL BFF"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s 网关=%-18s BFF=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【子图清单】");
        subgraphs.forEach(s -> System.out.printf("  %-8s %-8s 字段=%s%n",
                s.team(), s.type(), s.fields()));
        System.out.println();

        System.out.println("【联邦合并 + 路由表】");
        Map<String, String> routing = buildRouting();
        routing.forEach((k, v) -> System.out.printf("  %-16s -> %s%n", k, v));
        System.out.println();

        System.out.println("【BFF 按查询字段分派】");
        System.out.println("  查询：product { name price brand reviews }");
        Map<String, Object> data = query(routing, List.of("name", "price", "brand", "reviews"));
        System.out.println("  结果：" + data.keySet());

        System.out.println();
        System.out.println("【不存在的字段】");
        query(routing, List.of("name", "supplier"));

        System.out.println();
        bffVsApiGateway();
        System.out.println("  原则：BFF 只做聚合与适配，不写业务逻辑（业务仍在后端服务）");
    }
}
```

**课后练习**
1. 实现联邦路由表：4 个子图合并，验证字段路由到正确团队、冲突能被检出。
2. 实现 BFF 聚合：一次查询跨 3 个团队，验证"同团队字段合并调用"的优化。
3. 思考题：为什么"BFF 里写业务逻辑"是反模式？从"职责重复"与"两端不一致"分析。

---

### 75-11 综合案例：商品 GraphQL 网关

**知识点**
- **把本章收口成一个可运行的商品网关**：**Schema**（75-02）-> **解析**（75-03）-> **执行 + Resolver**（75-04）-> **DataLoader 解 N+1**（75-05）-> **Mutation 幂等**（75-06）-> **部分成功**（75-07）-> **复杂度限制**（75-08）-> **安全**（75-09）-> **联邦路由**（75-10）。
- **一次完整请求的链路**：解析查询 -> 校验（深度/复杂度/字段存在）-> 鉴权（字段级 74-04）-> 执行（resolver + DataLoader 批量）-> 组装响应（data + errors）。
- **验收清单**：查询形状与 schema 一致、N+1 已消除、复杂度超限被拒、字段级鉴权生效、部分失败可定位、Mutation 幂等。
- 与全书的关系：GraphQL 网关把 54（数据）、74（权限）、42（可观测）、64（治理）在"API 形态"上组合——**它和 69/70/71 章一样，是"能力的组装"**。
- 易错点：无复杂度限制；字段不鉴权；N+1 未处理；错误无 code；Mutation 无幂等。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProductGraphqlCase {
    record Product(long id, String name, double price, double cost, long brandId) {
    }

    record Brand(long id, String name) {
    }

    static final Map<Long, Product> products = new LinkedHashMap<>();
    static final Map<Long, Brand> brands = new LinkedHashMap<>();
    static final Map<String, Object> stats = new LinkedHashMap<>();

    static void seed() {
        products.put(1L, new Product(1, "手机壳", 69.0, 20.0, 10));
        products.put(2L, new Product(2, "耳机", 129.0, 60.0, 10));
        products.put(3L, new Product(3, "充电器", 39.0, 15.0, 20));
        brands.put(10L, new Brand(10, "自有品牌"));
        brands.put(20L, new Brand(20, "第三方"));
    }

    /** 字段级权限（74-04/75-09） */
    static boolean canRead(Set<String> perms, String field) {
        return switch (field) {
            case "cost" -> perms.contains("product:read:cost");
            case "price" -> perms.contains("product:read:price") || perms.contains("product:read");
            default -> perms.contains("product:read");
        };
    }

    /** DataLoader：批量取品牌（75-05） */
    static class BrandBatch {
        final java.util.Set<Long> queue = new java.util.LinkedHashSet<>();
        final Map<Long, Brand> cache = new LinkedHashMap<>();

        void load(long id) {
            if (!cache.containsKey(id)) {
                queue.add(id);
            }
        }

        void dispatch() {
            if (queue.isEmpty()) {
                return;
            }
            stats.put("brandBatchQueries", (Integer) stats.getOrDefault("brandBatchQueries", 0) + 1);
            System.out.printf("    [DB] 批量查品牌 %s（1 次）%n", queue);
            for (Long id : queue) {
                Brand b = brands.get(id);
                if (b != null) {
                    cache.put(id, b);
                }
            }
            queue.clear();
        }

        Brand get(long id) {
            return cache.get(id);
        }
    }

    record FieldNode(String name, List<FieldNode> children) {
    }

    static int complexity(FieldNode n) {
        int self = n.name().equals("products") ? 10 : 1;
        int sum = self;
        for (FieldNode c : n.children()) {
            sum += complexity(c);
        }
        return sum;
    }

    static void execute(Set<String> perms, List<String> fields, int maxComplexity) {
        FieldNode root = new FieldNode("products", fields.stream()
                .map(f -> new FieldNode(f, List.of())).toList());
        int c = complexity(root);
        System.out.printf("  复杂度=%d（上限 %d）-> %s%n", c, maxComplexity,
                c > maxComplexity ? "拒绝" : "通过");
        if (c > maxComplexity) {
            return;
        }

        List<Map<String, Object>> data = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        BrandBatch batch = new BrandBatch();
        stats.put("productQueries", (Integer) stats.getOrDefault("productQueries", 0) + 1);
        System.out.println("    [DB] 查商品列表（1 次）");

        // 阶段一：收集所有 brandId（DataLoader）
        if (fields.contains("brand")) {
            for (Product p : products.values()) {
                batch.load(p.brandId());
            }
            batch.dispatch();
        }

        // 阶段二：组数据（字段级权限）
        for (Product p : products.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : fields) {
                if (!canRead(perms, f)) {
                    row.put(f, null);
                    errors.add(p.id() + "." + f + ": FORBIDDEN");
                    continue;
                }
                switch (f) {
                    case "name" -> row.put(f, p.name());
                    case "price" -> row.put(f, p.price());
                    case "cost" -> row.put(f, p.cost());
                    case "brand" -> {
                        Brand b = batch.get(p.brandId());
                        row.put(f, b == null ? null : b.name());
                    }
                    default -> {
                        row.put(f, null);
                        errors.add(p.id() + "." + f + ": UNKNOWN_FIELD");
                    }
                }
            }
            data.add(row);
        }

        System.out.println("  结果行数：" + data.size());
        System.out.println("  样本：" + data.get(0));
        System.out.println("  错误：" + (errors.isEmpty() ? "[]" : errors));
        System.out.printf("  统计：商品查询 %s 次，品牌批量查询 %s 次%n",
                stats.get("productQueries"), stats.get("brandBatchQueries"));
        System.out.println();
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【查询一：客服（无 cost 权限）取 name/price/brand】");
        execute(Set.of("product:read"), List.of("name", "price", "brand"), 100);

        System.out.println("【查询二：财务（有 cost 权限）取 cost】");
        execute(Set.of("product:read", "product:read:cost"), List.of("name", "cost"), 100);

        System.out.println("【查询三：复杂度超限】");
        execute(Set.of("product:read"), List.of("name", "price", "brand", "cost"), 10);

        System.out.println("【查询四：非法字段】");
        execute(Set.of("product:read"), List.of("name", "supplier"), 100);

        System.out.println("【验收】schema 一致 / N+1 消除（品牌 1 次批量）/ 复杂度限制 / 字段鉴权 / 错误可定位");
    }
}
```

**课后练习**
1. 端到端实现商品 GraphQL 网关（查询/权限/DataLoader/复杂度），用 4 个查询验证各项防护。
2. 加入 Mutation（创建商品 + 幂等键）与"部分成功"响应，验证写路径的规范。
3. 思考题：为什么"字段级鉴权必须在 resolver 内"？从"客户端可构造任意查询"分析（75-09 的呼应）。
