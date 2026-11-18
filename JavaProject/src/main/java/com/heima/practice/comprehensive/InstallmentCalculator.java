/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 14-01
 * 课名：金融分期计算器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】输入贷款本金（元）、年利率（%）、期数：① 用 BigDecimal 计算等额本息每月还款额（月供 = 本金 × 月利率 × (1 + 月利率)^n ÷ ((1 + 月利率)^n − 1)），保留 2 位四舍五入；② 输出总利息；③ 计算若提前 3 期还清可节省的利息；④ 用 `System.currentTimeMillis()` 统计计算耗时并输出。
 *   - 【考核点】BigDecimal 精度、divide 必须指定精度与 RoundingMode、Math.pow、包装类、System 计时、金额格式化。
 *   - 【易错点】BigDecimal 除法不指定精度会抛 ArithmeticException；不要用 `new BigDecimal(0.1)`（用字符串构造）。
 *   - 【提示】`Math.pow` 返回 double，转 BigDecimal 用 `BigDecimal.valueOf(...)`；月利率 = 年利率 ÷ 12 ÷ 100。
 * ============================================================
 */

package com.heima.practice.comprehensive;

import java.math.BigDecimal;
import java.math.RoundingMode;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：BigDecimal 加减与带精度除法
public class InstallmentCalculator {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("0.1");
        BigDecimal b = new BigDecimal("0.2");
        System.out.println(a.add(b));
        System.out.println(new BigDecimal("10").divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 BigInteger 计算 100! 的结果并输出其位数（不转 double）。
 * 2. 实现“等额本金”月供计算，与等额本息对比总利息差额。
 * 3. 处理年利率为 0 的边界情况（直接本金 ÷ 期数），并校验期数必须为正整数。
 * 
 * 
 * > 完成本册后进入《毕业综合实操卷.md》，按 4 小时限时完成整套系统。
 * ============================================================
 */
