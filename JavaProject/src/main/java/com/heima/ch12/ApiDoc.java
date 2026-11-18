/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：12-01
 * 课名：API和API帮助文档
 * ------------------------------------------------------------
 * 知识点：
 *   - API（Application Programming Interface，应用程序编程接口）：Java 已经写好的类和方法，我们拿来直接调用，不必关心内部实现。
 *   - 最常用的类大多在 `java.lang` 包（如 String、System、Math），使用时不需要 import；其他包的类（如 java.util 包的 Scanner、Random）必须先 import 再用。
 *   - API 帮助文档（官方在线文档或离线 chm 文件）就是"类的说明书"，遇到陌生的类和方法，第一步就是查文档。
 *   - 查文档的步骤：搜索类名 → 看包名和类的说明 → 看构造方法摘要（怎么创建对象）→ 看方法摘要（方法名、参数、返回值）。
 *   - 看方法签名的方法：参数列表决定"要传什么"，返回值类型决定"要不要接收结果"，void 表示没有返回值。
 *   - 易错点：很多方法不会修改原数据（如字符串方法），必须用变量接收返回值，否则调用等于白调。
 * ============================================================
 */

package com.heima.ch12;

import java.util.Random;
import java.util.Scanner;

// ==================== 示例代码 ====================

public class ApiDoc {
    public static void main(String[] args) {
        // Scanner 在 java.util 包下，使用前必须先 import
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入一个整数：");
        int num = sc.nextInt();

        // Math 在 java.lang 包下，不需要 import，类名直接用
        System.out.println("它的绝对值是：" + Math.abs(num));
        System.out.println("它的平方根是：" + Math.sqrt(Math.abs(num)));

        // Random 也在 java.util 包下
        Random r = new Random();
        System.out.println("送给您一个 1~100 的幸运数字：" + (r.nextInt(100) + 1));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 打开 JDK 的 API 帮助文档，找到 String 类的 length 方法，说明它的功能、返回值类型以及是否有参数。
 * 2. 在文档中查找 Random 类的 nextInt(int bound) 方法，说明参数 bound 的含义和返回值的取值范围。
 * 3. 键盘录入一个浮点数，通过查阅 Math 类文档，输出它的绝对值、四舍五入后的整数以及向上取整的结果。
 * ============================================================
 */
