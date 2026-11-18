/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-04
 * 课名：索引与映射设计
 * ------------------------------------------------------------
 * 知识点：
 *   - **mapping 是字段的"类型 + 分析方式"声明**：每个字段要回答三个问题——什么类型（text/keyword/long/date/boolean）、要不要分词（analyzer）、要不要进倒排（index）与聚合排序（doc_values）。设计错了轻则查询失效，重则必须重建索引。
 *   - **text 与 keyword 的分工**：标题、描述 → `text`（分词、全文检索、评分）；状态、类目、手机号 → `keyword`（不分词，精确过滤、排序、聚合）。两个字段都要时用 multi-field：`title` 是 text，`title.keyword` 是 keyword，一个字段两套能力。
 *   - **动态映射（dynamic mapping）是便利也是陷阱**：写入未声明的新字段时 ES 自动猜类型——首个值像数字就建成 long、像日期就建成 date、其他建成带 `.keyword` 子字段的 text。**猜错类型后不可修改，只能重建索引**。生产规范：`dynamic: false`（新字段只存 _source 不建索引）或干脆关闭自动添加。
 *   - **mapping 不可变的例外**：可以"追加"新字段，不能改已有字段的类型/analyzer。改 analyzer 的标准动作是：建新索引（带别名切换）→ reindex → 原子切换别名。别名（alias）是零停机重建索引的基石。
 *   - **倒排索引之外的二级结构**：`doc_values` 是正排的列式存储，支持排序/聚合/脚本（keyword/数值默认开启）；`_source` 存原始 JSON，用于返回结果与 reindex。`index: false` 只存不搜，`fielddata` 是 text 字段做聚合的遗老遗少（内存杀手，禁用）。
 *   - **索引模板（template）与分片规划**：日志类按天建索引用模板统一 mapping；业务索引分片数 = 预期单分片 10~40GB；**主分片数建好后不可改**（文档路由依赖它），副本数可以随时调。
 *   - **null_value 与 copy_to**：keyword 字段默认 null 不索引，用 `null_value: ""` 补一个可检索占位；`copy_to: all` 把多个字段拼进一个搜索字段，实现"一次 match 查多列"。
 *   - 易错点：状态字段被动态映射猜成 text+keyword 子字段，聚合出来的桶按分词词组（"已支付"变成"已"+"支付"）；date 类型猜错导致时间字符串拒写；一个索引几十个分片（每个分片都是一个 Lucene 实例，小分片是纯开销）；reindex 期间忘了别名切换，前端连的还是旧索引。
 * ============================================================
 */

package com.heima.ch38;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class IndexMapping {
    record FieldDef(String name, String type, String analyzer, boolean indexed, String useCase) {
    }

    static void productMapping() {
        System.out.println("【商品索引 mapping 设计】");
        List<FieldDef> fields = List.of(
                new FieldDef("title", "text", "ik_max_word（+ik_smart 查询侧）", true,
                        "全文搜索与评分"),
                new FieldDef("title.keyword", "keyword", "不分词", true, "精确匹配与排序"),
                new FieldDef("category", "keyword", "-", true, "过滤 + terms 聚合"),
                new FieldDef("price", "scaled_float(精度100)", "-", true, "范围过滤与排序"),
                new FieldDef("status", "keyword", "-", true, "状态过滤，绝不用 text"),
                new FieldDef("description", "text", "ik_max_word", true, "低权重全文字段"),
                new FieldDef("innerRemark", "text", "-", false, "index:false 只存不搜"),
                new FieldDef("created", "date", "-", true, "范围过滤与排序"));
        for (FieldDef f : fields) {
            System.out.printf("  %-22s %-20s %-28s %s%n",
                    f.name(), f.type(), f.analyzer(), f.useCase());
        }
        System.out.println();
    }

    static void dynamicMappingTrap() {
        System.out.println("【动态映射的猜类型事故】");
        Map<String, String> guessed = new LinkedHashMap<>();
        guessed.put("orderId", "首值 1001 -> long（看起来没问题）");
        guessed.put("userNo", "首值 \"1001\" -> text + .keyword（后续数字串也能进，但聚合错位）");
        guessed.put("created", "首值 \"2026-01-01\" -> date（换成时间戳格式后拒写）");
        for (Map.Entry<String, String> e : guessed.entrySet()) {
            System.out.printf("  %-9s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  约束：已有字段类型不可改 -> 只能新索引 + reindex + 别名切换");
        System.out.println("  预防：显式 mapping；日志类索引 dynamic:false 或 strict");
        System.out.println();
    }

    static void reindexFlow() {
        System.out.println("【零停机改 mapping：别名切换四步】");
        String[] steps = {
                "1) 创建 product_v2（新 mapping，含新 analyzer）",
                "2) reindex：POST _reindex {source: product_v1, dest: product_v2}（后台异步）",
                "3) 切别名（原子操作）：移除 product_v1，添加 product_v2，都叫 product",
                "4) 应用侧永远只连别名 product，切换瞬间完成，客户端无感"};
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：应用直连 product_v1 实体名 -> 切换后前端仍写旧索引");
        System.out.println();
    }

    static void shardPlanning() {
        System.out.println("【分片与副本规划】");
        System.out.println("  主分片：单分片 10~40GB 为佳；3 个节点数据量 100GB -> 5 主分片");
        System.out.println("  副本：生产至少 1 副本（节点宕机不丢数据 + 读扩展）");
        System.out.println("  主分片数创建后不可改：路由公式 shard = hash(routing) % primary_count");
        System.out.println("  太多小分片：每个分片一个 Lucene 实例 + 一份固定内存开销，纯浪费");
        System.out.println("  日志场景：按天滚动索引 + 模板 + ILM 自动删除，分片数在模板里定");
    }

    public static void main(String[] args) {
        productMapping();
        dynamicMappingTrap();
        reindexFlow();
        shardPlanning();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你项目的核心业务对象写出完整 mapping（含 multi-field、copy_to），标注每个字段的选择理由，并检查动态映射是否已关闭。
 * 2. 复现一次"动态映射猜错"：让一个字段先写入 `"1001"` 再写入 `2002`，观察结果，然后给出两种修复路径（reindex / 兼容写法）。
 * 3. 思考题：为什么主分片数不可修改而副本数可以？结合路由公式推导"扩主分片必须 reindex"的原因。
 * ============================================================
 */
