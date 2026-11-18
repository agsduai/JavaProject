/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：7-06
 * 课名：大乐透
 * ------------------------------------------------------------
 * 知识点：
 *   - 题意：模拟超级大乐透机选号码：前区从 1~35 随机 5 个不重复号码，后区从 1~12 随机 2 个不重复号码
 *   - 核心思路：随机生成一个号码后，先检查它在前面是否已经出现过，重复就重新生成（do-while 循环直到取满）
 *   - 判重逻辑封装成独立方法，传入"数组 + 已生成个数 + 新号码"，返回 boolean
 *   - 号码全部生成后用 `Arrays.sort` 排序，按"前区 + 后区"的格式输出
 *   - 进阶思路：把 1~35 放入集合后 `Collections.shuffle` 洗牌，取前 5 个同样能保证不重复
 *   - 复杂度感受：O(n × k)（k 为号码个数），数据量极小可忽略；易错点是 `nextInt(35)` 生成的是 0~34，必须 +1 才是 1~35
 * ============================================================
 */

package com.heima.ch07;

import java.util.Arrays;
import java.util.Random;

// ==================== 示例代码 ====================

public class Lottery {
    public static void main(String[] args) {
        int[] front = new int[5]; // 前区：35 选 5
        int[] back = new int[2];  // 后区：12 选 2
        Random r = new Random();

        // 前区随机取 5 个不重复号码（1~35）
        for (int i = 0; i < front.length; i++) {
            int num;
            do {
                num = r.nextInt(35) + 1; // nextInt(35) 是 0~34，+1 才是 1~35
            } while (isExist(front, i, num)); // 与已生成号码重复就重取
            front[i] = num;
        }
        // 后区随机取 2 个不重复号码（1~12）
        for (int i = 0; i < back.length; i++) {
            int num;
            do {
                num = r.nextInt(12) + 1;
            } while (isExist(back, i, num));
            back[i] = num;
        }

        Arrays.sort(front); // 号码从小到大输出
        Arrays.sort(back);
        System.out.println("前区号码：" + Arrays.toString(front));
        System.out.println("后区号码：" + Arrays.toString(back));
        // 输出示例（每次运行随机）：
        // 前区号码：[3, 11, 17, 25, 34]
        // 后区号码：[5, 9]
    }

    // 判断 num 是否已出现在 arr 的前 count 个元素中
    public static boolean isExist(int[] arr, int count, int num) {
        for (int i = 0; i < count; i++) {
            if (arr[i] == num) {
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
 * 1. 把取号逻辑改写成方法 `pickNumbers(int max, int count)`：返回一个长度为 count 的数组，内容是从 1~max 中随机取出的不重复号码，用 `pickNumbers(35, 5)` 和 `pickNumbers(12, 2)` 完成大乐透机选。
 * 2. 编写双色球机选程序：红球 33 选 6 个不重复号码、蓝球 16 选 1 个号码，按"红球：… 蓝球：…"格式输出。
 * 3. 进阶：让用户自选一注号码（或硬编码一注），与机选号码比对，输出中了几个前区号码、几个后区号码，并按对照表给出中奖等级；再模拟机选 100 万次，统计"前区 5 个全中"出现的次数，体会一等奖概率有多低。
 * ============================================================
 */
