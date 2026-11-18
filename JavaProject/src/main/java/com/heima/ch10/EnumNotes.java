/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：10-07
 * 课名：枚举的注意事项
 * ------------------------------------------------------------
 * 知识点：
 *   - 枚举的构造方法必须是私有的（连访问修饰符都不写时默认也是私有），外界不能创建枚举对象，只能使用现成的枚举项。
 *   - 枚举项必须写在枚举类的第一行，多个枚举项用逗号分隔，最后一个枚举项后面用分号结束，之后才能写成员变量和方法。
 *   - 枚举的本质是一个类，默认已经继承了 java.lang.Enum，由于 Java 单继承，枚举不能再继承其他类，但可以实现接口。
 *   - 枚举项之间比较可以直接用 ==，也可以用 equals，结果一样。
 *   - 常用方法：values() 返回所有枚举项组成的数组；valueOf(String) 把字符串转成对应的枚举项，字符串不存在时会抛出 IllegalArgumentException 异常。
 *   - 其他方法：name()/toString() 返回枚举项名字；ordinal() 返回枚举项在枚举中的序号，从 0 开始。
 * ============================================================
 */

package com.heima.ch10;

// ==================== 示例代码 ====================

public class EnumNotes {
    public static void main(String[] args) {
        // values()：获取所有枚举项
        EnumNotesWeek[] weeks = EnumNotesWeek.values();
        for (EnumNotesWeek w : weeks) {
            System.out.println(w + " 序号：" + w.ordinal());
        }

        // valueOf()：把字符串转成对应的枚举项
        EnumNotesWeek w1 = EnumNotesWeek.valueOf("MONDAY");
        System.out.println(w1);

        // 枚举项比较：== 或 equals 都可以
        System.out.println(w1 == EnumNotesWeek.MONDAY);       // true
        System.out.println(w1.equals(EnumNotesWeek.TUESDAY)); // false

        // 枚举的本质：默认继承 java.lang.Enum，打印父类名验证
        System.out.println(EnumNotesWeek.class.getSuperclass()); // class java.lang.Enum

        // EnumNotesWeek bad = EnumNotesWeek.valueOf("SOMEDAY");
        // 字符串在枚举中不存在，运行时会抛出 IllegalArgumentException
    }
}

// 枚举项必须在第一行，最后一个枚举项后面用分号结束，之后才能写其他成员
enum EnumNotesWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义 Color 枚举（RED、GREEN、BLUE），用 values() 遍历打印每个枚举项的名字及其 ordinal() 序号。
 * 2. 编写代码用 valueOf 分别转换 "GREEN" 和 "YELLOW" 两个字符串，观察第二次转换运行时的异常信息，把异常类名记录下来。
 * 3. 读代码预测输出题：Direction 枚举中枚举项顺序为 LEFT、UP、RIGHT、DOWN，打印 Direction.UP.ordinal() 和 Direction.DOWN.ordinal() 各是多少？再判断 Direction.UP == Direction.valueOf("UP") 的结果是什么，写出理由。
 * ============================================================
 */
