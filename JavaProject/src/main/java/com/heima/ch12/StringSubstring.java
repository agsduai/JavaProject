/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：12-04
 * 课名：字符串的截取、替换和其他常见方法
 * ------------------------------------------------------------
 * 知识点：
 *   - substring(int begin, int end) 截取 [begin, end) 范围，包头不包尾；substring(int begin) 从 begin 一路截取到末尾。
 *   - replace(oldStr, newStr) 把所有旧内容替换成新内容；split("规则") 按规则切割成 String 数组，参数是正则表达式，遇到 `.`、`|` 等特殊符号要写成 `\\.`、`\\|`。
 *   - contains 判断是否包含、indexOf 查找第一次出现的索引（找不到返回 -1）、startsWith / endsWith 判断开头和结尾。
 *   - toCharArray / getBytes 转数组，String.valueOf 把数字等转成字符串，trim 去掉首尾空格。
 *   - toUpperCase / toLowerCase 大小写转换，isEmpty 判断长度是否为 0。
 *   - 易错点：这些方法都不改动原字符串，而是返回一个新字符串，必须接收返回值才有意义。
 * ============================================================
 */

package com.heima.ch12;

import java.util.Arrays;

// ==================== 示例代码 ====================

public class StringSubstring {
    public static void main(String[] args) {
        String s = "I-LOVE-JAVA-2026";

        // 截取：包头不包尾，[2, 6) 得到 LOVE
        System.out.println(s.substring(2, 6)); // LOVE

        // 从索引 7 一路截取到末尾
        System.out.println(s.substring(7)); // JAVA-2026

        // 替换：把所有旧内容替换成新内容
        System.out.println("aabbbc".replace("b", "x")); // aaxxxc

        // 按规则切割成字符串数组
        String[] arr = s.split("-");
        System.out.println(Arrays.toString(arr)); // [I, LOVE, JAVA, 2026]

        // 包含判断与查找索引，找不到时 indexOf 返回 -1
        System.out.println(s.contains("JAVA")); // true
        System.out.println(s.indexOf("A"));     // 8

        // 去空格、转大写、转数组
        System.out.println(" abc ".trim().toUpperCase());        // ABC
        System.out.println(Arrays.toString("abc".toCharArray())); // [a, b, c]
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给定字符串 "Hello-World-Java"，截取其中的 World 和 Java，拼接成 WorldJava 输出。
 * 2. 键盘录入一句英文（包含空格），按空格切割成单词，统计单词个数并逐行输出每个单词及其长度。
 * 3. 键盘录入一个邮箱字符串，判断是否包含 @：包含则输出 @ 之前和之后的两段内容，不包含则输出"格式有误"。
 * ============================================================
 */
