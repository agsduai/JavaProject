/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：2-21
 * 课名：逻辑运算符
 * ------------------------------------------------------------
 * 知识点：
 *   - & 与：两边都为 true，结果才是 true。
 *   - | 或：两边只要有一个为 true，结果就是 true。
 *   - ! 非：取反，true 变 false、false 变 true。
 *   - ^ 异或：两边相同为 false，不同为 true。
 *   - 逻辑运算符两边要么是 boolean 值，要么是能产生 boolean 的表达式（常与关系运算符连用）。
 *   - & 和 | 无论左边结果如何，右边都会执行。
 * ============================================================
 */

package com.heima.ch02;

// ==================== 示例代码 ====================

public class LogicalOperator {
    public static void main(String[] args) {
        boolean hasTicket = true;     // 有票
        boolean hasIdCard = false;    // 有身份证
        // & 与：两边都true才true
        System.out.println(hasTicket & hasIdCard);   // false
        // | 或：一边true就true
        System.out.println(hasTicket | hasIdCard);   // true
        // ! 非：取反
        System.out.println(!hasTicket);   // false
        // ^ 异或：相同false，不同true
        System.out.println(hasTicket ^ hasIdCard);   // true
        // 与关系运算符连用
        int score = 85;
        System.out.println(score >= 80 & score < 90);   // true
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义两个 boolean 变量表示"已购票""已实名"，用 & 输出能否进场，用 | 输出"是否至少满足其中一项"。
 * 2. 定义三个整数 a = 10、b = 5、c = 8，输出 `a > b & b < c`、`a > b | c > b`、`!(a > b)`、`a > b ^ c > b` 四个表达式的结果。
 * 3. 挑战：用程序输出 p、q 取 true/false 全部四种组合时 `p & q`、`p | q`、`p ^ q` 的值，并把结果整理成表格写在注释里。
 * ============================================================
 */
