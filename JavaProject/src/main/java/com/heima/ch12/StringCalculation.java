/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：12-07
 * 课名：算法练习-字符串计算（难）
 * ------------------------------------------------------------
 * 知识点：
 *   - 题意（力扣第 13 题）：罗马数字由 I V X L C D M 七种字符组成，对应 1、5、10、50、100、500、1000，给定罗马数字字符串，求出它表示的整数，如 MCMXCIV = 1994。
 *   - 减法规则：小值写在大值左边表示减法，共六种情况：IV=4、IX=9、XL=40、XC=90、CD=400、CM=900。
 *   - 核心思路：一次遍历，比较当前字符与右边字符的值——当前值小于右边值就做减法，否则做加法。
 *   - 技巧：用 switch 或查表法把字符换成数值；访问右边字符前先判断 `i + 1 < length`，防止索引越界。
 *   - 复杂度：时间 O(n)（只遍历一遍），空间 O(1)。
 * ============================================================
 */

package com.heima.ch12;

// ==================== 示例代码 ====================

public class StringCalculation {
    public static void main(String[] args) {
        // 多组数据验证：III=3, IV=4, LVIII=58, MCMXCIV=1994
        System.out.println(romanToInt("III"));     // 3
        System.out.println(romanToInt("IV"));      // 4
        System.out.println(romanToInt("LVIII"));   // 58
        System.out.println(romanToInt("MCMXCIV")); // 1994
    }

    // 罗马数字转整数：小值在大值左边时做减法，否则做加法
    public static int romanToInt(String s) {
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            int cur = getValue(s.charAt(i));
            // 当前字符的值小于右边字符的值时，当前值做减法（如 IV 中的 I）
            if (i + 1 < s.length() && cur < getValue(s.charAt(i + 1))) {
                sum -= cur;
            } else {
                sum += cur;
            }
        }
        return sum;
    }

    // 查表：把罗马字符换成对应的数值
    public static int getValue(char c) {
        switch (c) {
            case 'I': return 1;
            case 'V': return 5;
            case 'X': return 10;
            case 'L': return 50;
            case 'C': return 100;
            case 'D': return 500;
            case 'M': return 1000;
            default:  return 0;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 键盘录入一个合法的罗马数字字符串（只包含 I、V、X 三种字符），输出对应的整数。如输入 XIV，输出 14。
 * 2. 压缩字符串：键盘录入一个只含小写字母的字符串，把连续出现的相同字符压缩成"字符+次数"的形式。如输入 aaabbc，输出 a3b2c1。
 * 3. 整数转罗马数字（力扣第 12 题）：编写方法把整数转成罗马数字。如输入 1994，输出 MCMXCIV；输入 58，输出 LVIII。
 * ============================================================
 */
