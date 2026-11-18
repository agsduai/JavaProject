/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：12-06
 * 课名：字符串的四个算法小练习
 * ------------------------------------------------------------
 * 知识点：
 *   - 题目 1（对称字符串）：键盘录入一个字符串判断是否对称，如 "abcba" 对称、"abcabc" 不对称。思路：双指针，一个从头、一个从尾逐个比较。
 *   - 题目 2（首字母大写）：把字符串变成首字母大写、其余小写，如 "HELLO" 变成 "Hello"。思路：截取首字母转大写，截取剩余部分转小写，再拼接。
 *   - 题目 3（统计字符类型）：统计字符串中大写字母、小写字母、数字的个数。思路：遍历后做字符范围判断，如 `ch >= 'a' && ch <= 'z'`。
 *   - 题目 4（旋转字符串）：判断字符串 b 能否由字符串 a 旋转若干次得到，如 "abcde" 旋转后可以得到 "cdeab"。思路：每次把首字符挪到末尾算一次旋转，在旋转 a 的长度次以内能匹配即成立。
 *   - 通用套路：先写清楚输入输出 → 想明白循环与判断条件 → 再动手敲代码，写完用特殊数据测试。
 *   - 本节示例完整实现题目 1 和题目 4，题目 2、3 留作课后练习。
 * ============================================================
 */

package com.heima.ch12;

// ==================== 示例代码 ====================

public class StringAlgorithmPractice {
    public static void main(String[] args) {
        // ===== 题目1：判断对称字符串 =====
        String s1 = "abcba";
        String s2 = "abcabc";
        System.out.println(s1 + " 是否对称：" + isSymmetric(s1)); // true
        System.out.println(s2 + " 是否对称：" + isSymmetric(s2)); // false

        // ===== 题目4：判断旋转字符串 =====
        String a = "abcde";
        String b = "cdeab";
        System.out.println(b + " 是否由 " + a + " 旋转得到：" + checkRotation(a, b)); // true
    }

    // 双指针判断对称：i 从前往后，j 从后往前，逐个字符比较
    public static boolean isSymmetric(String str) {
        for (int i = 0, j = str.length() - 1; i < j; i++, j--) {
            if (str.charAt(i) != str.charAt(j)) {
                return false;
            }
        }
        return true;
    }

    // 旋转判断：每次把首字符挪到末尾算旋转一次，最多转 length 次
    public static boolean checkRotation(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            // 旋转 i 次的结果 = 后半段 + 前半段
            String rotated = a.substring(i) + a.substring(0, i);
            if (rotated.equals(b)) {
                return true;
            }
        }
        return false;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 键盘录入一个字符串，把首字母大写、其余字母小写后输出。如输入 HELLO，输出 Hello。
 * 2. 键盘录入一个字符串，统计其中大写字母、小写字母、数字分别出现的个数。如输入 Ab12，输出 大写1、小写1、数字2。
 * 3. 键盘录入一个字符串 s 和一个整数 k，输出 s 向右旋转 k 位后的结果。如输入 abcde 和 2，输出 deabc。
 * ============================================================
 */
