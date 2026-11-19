/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-04
 * 课名：缓慢变化维 SCD
 * ------------------------------------------------------------
 * 知识点：
 *   - **SCD（Slowly Changing Dimension）解决"维度属性会变"的问题**：用户等级变了、商品改类目了、门店换区域了——**三种处理策略的语义完全不同**。
 *   - **SCD1（覆盖）**：直接改属性值，**不保留历史**——简单、省空间，但**历史事实会"追溯性变化"**（去年的订单现在按新类目统计）——适合"纠错型变更"（属性填错了）。
 *   - **SCD2（拉链，新增版本行）**：每次变更插入新版本（新代理键 + `start_date`/`end_date`/`is_current`），**历史事实仍指向旧版本**——最常用，**支持"按当时的维度属性分析"**（63-05 拉链表）。
 *   - **SCD3（新增列）**：加一列"上一版本值"（`old_category`/`current_category`）——**只保留一个历史版本**，适合"只关心上一次变化"的场景（很少用）。
 *   - **选择判据**：**分析是否需要"历史当时的属性"**——需要（如"当时的商品类目"）用 SCD2；不需要（如"纠正错别字"）用 SCD1；**混用要标注字段级策略**。
 *   - 易错点：全用 SCD1（历史分析全错）；SCD2 忘记关旧版本（同一维度有多条 `is_current=true`）；SCD2 变更时间用 ETL 时间而非业务生效时间（分析错位）；SCD 没有变更检测（每次全量插新版本，维度爆炸）。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三种 SCD 策略（覆盖/拉链/加列），对"商品改类目"场景分别输出维度表变化与历史统计口径差异。
 * 2. 实现"变更检测"：比较新旧属性，只有变化才新增 SCD2 版本，用 10 次（其中 7 次无变化）的更新验证版本数。
 * 3. 思考题：为什么 SCD2 的有效时间要用"业务生效时间"而不是"ETL 时间"？构造一个跨日的类目调整用例说明差异。
 * ============================================================
 */
