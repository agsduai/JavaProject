/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-10
 * 课名：营销归因与增量 ROI
 * ------------------------------------------------------------
 * 知识点：
 *   - **表面 ROI = 活动期成交额 / 优惠成本，这个数几乎总是虚高的**：**因为下单的人里有一大部分本来就会买**，**券只是把钱送给了原本的买家**。
 *   - **增量 ROI 要减去「本来就会发生」的部分**：**增量 = 实验组成交 - 对照组成交（按同一口径、同一时间窗）**，**增量 ROI = 增量毛利 / 优惠成本**。
 *   - **对照组不能是「活动前一周」**：**大促前后本来就有自然波动（周末、发薪日、天气）**，**必须同时段随机分流做 A/B，才能剥离时间因素**。
 *   - **券的「核销率」高不代表有效**：**无门槛券核销率天然高，但增量可能接近 0**，**有门槛券核销率低，却可能撬动了真实的凑单**。
 *   - **归因窗口要固定**：**「领券后 7 天内下单算活动效果」与「30 天」会得出完全不同的 ROI**，**窗口越长，把自然成交算进来的越多**。
 *   - 易错点：用活动期总 GMV 对比活动前一周；把「用了券的订单」全算成增量；忽略退货（退货后的 GMV 不是真成交）；只算 GMV 不算毛利（高毛利品类被低毛利品类拉低）。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

public class MarketingAttribution {
    record Group(String name, long users, long orders, long gmvFen, long grossFen,
                 long couponCostFen, long refundFen) {
        long netGmv() {
            return gmvFen - refundFen;
        }

        double conversion() {
            return orders * 100.0 / users;
        }

        double aov() {
            return orders == 0 ? 0 : netGmv() / 100.0 / orders;
        }
    }

    static String yuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }

    static String pct(double v) {
        return String.format("%.2f%%", v);
    }

    public static void main(String[] args) {
        // 同期随机分流：实验组发券，对照组不发
        // 口径：GMV 为下单金额，refundFen 为退货金额，grossFen 为「已扣退货后的净毛利」
        Group exp = new Group("实验组（发券）", 500_000, 62_000,
                1_860_000_000L, 470_000_000L, 155_000_000L, 111_600_000L);
        Group ctrl = new Group("对照组（不发券）", 500_000, 54_000,
                1_620_000_000L, 397_000_000L, 0L, 64_800_000L);

        System.out.println("【1. 两组基础数据（同期随机分流，各 50 万人）】");
        System.out.printf("  %-16s %-10s %-10s %-14s %-14s %s%n",
                "组", "订单数", "转化率", "净GMV(元)", "净毛利(元)", "客单价(元)");
        for (Group g : new Group[]{exp, ctrl}) {
            System.out.printf("  %-16s %-10d %-10s %-14s %-14s %.2f%n",
                    g.name(), g.orders(), pct(g.conversion()), yuan(g.netGmv()),
                    yuan(g.grossFen()), g.aov());
        }

        System.out.println();
        System.out.println("【2. 表面 ROI：拿实验组自己的成交去除优惠成本】");
        double surfaceRoi = exp.grossFen() / (double) exp.couponCostFen();
        System.out.printf("  表面 ROI = 实验组毛利 %s / 优惠成本 %s = %.2f%n",
                yuan(exp.grossFen()), yuan(exp.couponCostFen()), surfaceRoi);
        System.out.println("  这个数把「本来就会买的人」的毛利也算成了活动功劳。");

        System.out.println();
        System.out.println("【3. 增量 ROI：减去对照组本来就会发生的部分】");
        long incOrders = exp.orders() - ctrl.orders();
        long incGmv = exp.netGmv() - ctrl.netGmv();
        long incGross = exp.grossFen() - ctrl.grossFen();
        double incRoi = incGross / (double) exp.couponCostFen();
        System.out.printf("  增量订单 %d 单，增量净GMV %s 元，增量净毛利 %s 元%n",
                incOrders, yuan(incGmv), yuan(incGross));
        System.out.println("  注意这里是「单」不是「人」：订单差与人数差不是一回事，口径写错会把结论放大好几倍。");
        System.out.printf("  增量 ROI = %s / %s = %.2f%n",
                yuan(incGross), yuan(exp.couponCostFen()), incRoi);
        System.out.printf("  表面 ROI %.2f vs 增量 ROI %.2f，虚高 %.1f 倍%n",
                surfaceRoi, incRoi, surfaceRoi / incRoi);

        System.out.println();
        System.out.println("【4. 券成本摊到增量订单上，每单花多少】");
        long subsidyPerOrder = exp.couponCostFen() / incOrders;
        long grossPerOrder = incGross / incOrders;
        System.out.printf("  优惠成本 %s 元 / 增量订单 %d 单 = 每单补贴 %s 元%n",
                yuan(exp.couponCostFen()), incOrders, yuan(subsidyPerOrder));
        System.out.printf("  增量订单客单价 %s 元，每单净毛利 %s 元%n",
                yuan(incGmv / incOrders), yuan(grossPerOrder));
        System.out.printf("  每单补贴 %s 元 > 每单毛利 %s 元，单看增量订单是「补贴高于毛利」%n",
                yuan(subsidyPerOrder), yuan(grossPerOrder));
        System.out.println("  这正是增量 ROI < 1 的含义：增量订单自己养不活自己的优惠成本。");

        System.out.println();
        System.out.println("【5. 退货对结论的影响】");
        System.out.printf("  实验组退货 %s 元，占 GMV %.2f%%%n",
                yuan(exp.refundFen()), exp.refundFen() * 100.0 / exp.gmvFen());
        System.out.printf("  对照组退货 %s 元，占 GMV %.2f%%%n",
                yuan(ctrl.refundFen()), ctrl.refundFen() * 100.0 / ctrl.gmvFen());
        long incGmvGross = exp.gmvFen() - ctrl.gmvFen();
        System.out.printf("  含退货的增量 GMV %s 元，扣退货后 %s 元，缩水 %.1f%%%n",
                yuan(incGmvGross), yuan(incGmv),
                (incGmvGross - incGmv) * 100.0 / incGmvGross);
        System.out.printf("  若拿含退货 GMV 去算 ROI 会得到 %.2f，用净毛利算只有 %.2f%n",
                incGmvGross / (double) exp.couponCostFen(), incRoi);
        System.out.printf("  券吸引来的用户退货率更高（%.0f%% vs %.0f%%）时，只看下单 GMV 会高估近两成。%n",
                exp.refundFen() * 100.0 / exp.gmvFen(), ctrl.refundFen() * 100.0 / ctrl.gmvFen());

        System.out.println();
        System.out.println("【6. 归因窗口：7 天 vs 30 天】");
        // 把增量净GMV 按日拆开：前 7 天占大头，第 8~30 天是长尾
        long[] daily = {900_000_00L, 450_000_00L, 260_000_00L, 150_000_00L,
                92_000_00L, 50_000_00L, 30_000_00L};
        long seven = 0;
        for (long v : daily) {
            seven += v;
        }
        long tail = 670_000_00L;          // 第 8~30 天的长尾归因 GMV
        long thirty = seven + tail;
        System.out.printf("  前 7 天逐日增量净GMV（元）：");
        for (long v : daily) {
            System.out.printf("%s ", yuan(v));
        }
        System.out.println();
        System.out.printf("  7 天窗口合计 %s 元，加上 8~30 天长尾 %s 元 = 30 天窗口 %s 元%n",
                yuan(seven), yuan(tail), yuan(thirty));
        double roi7 = incGross / (double) exp.couponCostFen();
        double roi30 = incGross * (thirty / (double) seven) / exp.couponCostFen();
        System.out.printf("  同一笔优惠成本 %s 元：7 天口径 ROI %.2f，30 天口径 ROI %.2f%n",
                yuan(exp.couponCostFen()), roi7, roi30);
        System.out.printf("  窗口从 7 天拉到 30 天，ROI 从 %.2f 涨到 %.2f，涨的 %.0f%% 全是自然成交%n",
                roi7, roi30, (roi30 / roi7 - 1) * 100);
        System.out.println("  窗口越长越好看，所以口径必须活动前定死并写进复盘模板，不能事后挑一个好看的。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把实验组转化率从 12.4% 调到 11.0%，重算增量 ROI，说明「表面 ROI 仍然好看但增量接近 0」是什么情形。
 * 2. 给对照组也加一个「平台通用券」成本，说明为什么对照必须「除被测变量外完全一致」。
 * 3. 思考题：如果活动期间同时上了「降价 + 发券」，怎么拆分各自的增量？说出至少两种可行方案。
 * ============================================================
 */

