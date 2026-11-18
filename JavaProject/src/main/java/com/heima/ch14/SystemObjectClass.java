/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：14-02
 * 课名：System类和Object类
 * ------------------------------------------------------------
 * 知识点：
 *   - `System.currentTimeMillis()` 返回当前时刻距 1970 年 1 月 1 日 00:00:00（UTC）的毫秒值，两次调用相减即可统计一段代码的运行耗时。
 *   - `System.exit(status)` 终止 JVM：参数 0 表示正常退出，非 0 表示异常退出。
 *   - `System.arraycopy(src, srcPos, dest, destPos, length)` 底层数组拷贝，效率高；引用类型元素只拷贝地址（浅拷贝）。
 *   - Object 是所有类的直接或间接父类，任何类都可以重写它的方法。
 *   - toString 默认返回"类名@十六进制地址"；打印对象时 println 会自动调用它，重写后输出内容更可读。
 *   - equals 默认等同 `==`，比较的是地址值；重写后改为比较属性内容。String 类已重写 equals，所以字符串能比较内容。
 * ============================================================
 */

package com.heima.ch14;

// ==================== 示例代码 ====================

// 注意：一个 .java 文件中只能有一个 public 类
class SystemObjectClassStudent {
    private String name;
    private int age;

    public SystemObjectClassStudent(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // 重写 toString：打印对象内容，而不是"类名@地址"
    @Override
    public String toString() {
        return "SystemObjectClassStudent{name=" + name + ", age=" + age + "}";
    }

    // 重写 equals：比较属性内容，而不是地址值
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true; // 自己和自己比
        if (obj == null || getClass() != obj.getClass()) return false;
        SystemObjectClassStudent s = (SystemObjectClassStudent) obj;
        return age == s.age && name.equals(s.name);
    }
}

public class SystemObjectClass {
    public static void main(String[] args) {
        // 用毫秒时间戳统计代码运行耗时
        long start = System.currentTimeMillis();
        int sum = 0;
        for (int i = 1; i <= 100000; i++) {
            sum += i;
        }
        long end = System.currentTimeMillis();
        System.out.println("求和 100000 次，耗时：" + (end - start) + " 毫秒");

        // 数组拷贝：从 src 索引 1 起拷 3 个元素到 dest 索引 0 处
        int[] src = {1, 2, 3, 4, 5};
        int[] dest = new int[5];
        System.arraycopy(src, 1, dest, 0, 3);
        System.out.println(java.util.Arrays.toString(dest)); // [2, 3, 4, 0, 0]

        // equals 重写后比较内容；println 默认调用 toString
        SystemObjectClassStudent s1 = new SystemObjectClassStudent("阿玮", 18);
        SystemObjectClassStudent s2 = new SystemObjectClassStudent("阿玮", 18);
        System.out.println(s1.equals(s2)); // true
        System.out.println(s1);            // SystemObjectClassStudent{name=阿玮, age=18}
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写程序，循环 10000 次把数字拼接到一个 String 对象上，用 System.currentTimeMillis 统计并输出这段拼接代码的耗时毫秒数。
 * 2. 定义 Point 类（成员变量 int x、int y），重写 toString 和 equals，使得两个点的 x、y 都相同时 equals 返回 true；在 main 中创建三个点对象（内容相同、内容不同、与自身比较）逐一验证。
 * 3. 有数组 `{1, 2, 3, 4, 5, 6, 7, 8}`，只允许使用 System.arraycopy（可借助一个临时数组），实现把数组整体向左循环移动 2 位，得到 `{3, 4, 5, 6, 7, 8, 1, 2}`。
 * ============================================================
 */
