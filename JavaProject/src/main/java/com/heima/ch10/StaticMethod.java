/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：10-03
 * 课名：static修饰成员方法
 * ------------------------------------------------------------
 * 知识点：
 *   - static 修饰的成员方法叫静态方法，属于类，推荐用"类名.方法名()"调用，也可以用对象名调用但不推荐。
 *   - 静态方法最典型的应用是工具类：把一些常用的、与具体对象无关的功能封装成静态方法，方便直接拿来用。
 *   - 工具类书写规范：构造方法私有（外界无法创建对象）+ 所有方法都是静态方法。
 *   - 私有构造的意义：工具类里的方法用类名就能调用，创建对象毫无意义，私有化还能防止别人乱 new。
 *   - JDK 中的 Math、Arrays 就是典型的工具类，本课自己动手封装一个 ArrayUtil。
 *   - 工具类方法写好后，任何项目中都可以直接"类名.方法"复用，这就是封装带来的复用性。
 * ============================================================
 */

package com.heima.ch10;

// ==================== 示例代码 ====================

// 数组工具类：私有构造方法 + 静态方法
class StaticMethodArrayUtil {
    // 私有构造：工具类不需要对象，防止外界 new
    private StaticMethodArrayUtil() {
    }

    // 打印数组内容，格式如 [1, 2, 3]
    public static void printArray(int[] arr) {
        System.out.print("[");
        for (int i = 0; i < arr.length; i++) {
            if (i == arr.length - 1) {
                System.out.print(arr[i]);
            } else {
                System.out.print(arr[i] + ", ");
            }
        }
        System.out.println("]");
    }

    // 求数组最大值
    public static int getMax(int[] arr) {
        int max = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > max) {
                max = arr[i];
            }
        }
        return max;
    }

    // 求数组总和
    public static int getSum(int[] arr) {
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        return sum;
    }
}

public class StaticMethod {
    public static void main(String[] args) {
        int[] arr = {3, 7, 1, 9, 4};

        // 工具类的静态方法直接用类名调用
        StaticMethodArrayUtil.printArray(arr);
        System.out.println("最大值：" + StaticMethodArrayUtil.getMax(arr));
        System.out.println("总和：" + StaticMethodArrayUtil.getSum(arr));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 ArrayUtil 增加静态方法 getMin(int[] arr) 求最小值、getAverage(int[] arr) 求平均值，并在测试类中用类名调用验证。
 * 2. 写一个 StringUtil 工具类：私有构造 + 静态方法 upper(String s) 返回全大写形式、reverse(String s) 返回反转后的字符串。
 * 3. 思考题：如果不私有化构造方法，外界 new 一个工具类对象也能照样调用静态方法，那么开发中为什么仍然约定工具类的构造方法必须私有？写下你的理解。
 * ============================================================
 */
