/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：10-06
 * 课名：枚举
 * ------------------------------------------------------------
 * 知识点：
 *   - 枚举是一种特殊的类，用于把取值范围有限的、需要一一列举的数据定义出来，如星期、季节、性别、方向。
 *   - 定义格式：`public enum 枚举名 { 枚举项1, 枚举项2, 枚举项3; }`，每个枚举项就是枚举类的一个实例对象。
 *   - 枚举项名字习惯全部大写（遵循常量命名规范），如 SPRING、MONDAY。
 *   - 枚举可以有成员变量、成员方法，构造方法默认（且必须）私有，外界不能 new 枚举对象。
 *   - 枚举项需要携带参数时写成"枚举项(参数)"，并定义对应的构造方法完成初始化。
 *   - 枚举与 switch 天然配合：case 后直接写枚举项，分支逻辑清晰且不容易写错。
 * ============================================================
 */

package com.heima.ch10;

// ==================== 示例代码 ====================

// 枚举：列举一年四季
enum EnumDemoSeason {
    // 枚举项必须写在最前面，带参数时会调用对应的构造方法
    SPRING("春天", "万物复苏"),
    SUMMER("夏天", "烈日炎炎"),
    AUTUMN("秋天", "硕果累累"),
    WINTER("冬天", "白雪皑皑");

    private String name; // 季节名
    private String desc; // 季节描述

    // 枚举的构造方法默认私有，外界不能调用
    EnumDemoSeason(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }
}

public class EnumDemo {
    public static void main(String[] args) {
        // 直接使用枚举项（本质是枚举类中现成的实例对象）
        EnumDemoSeason s = EnumDemoSeason.SPRING;
        System.out.println(s.getName() + "：" + s.getDesc());

        // 枚举配合 switch
        EnumDemoSeason cur = EnumDemoSeason.SUMMER;
        switch (cur) {
            case SPRING -> System.out.println("去踏青");
            case SUMMER -> System.out.println("去游泳");
            case AUTUMN -> System.out.println("去登山");
            case WINTER -> System.out.println("去滑雪");
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义 Direction 枚举（UP、DOWN、LEFT、RIGHT），携带中文描述属性和 getter，在 main 中打印四个方向的描述。
 * 2. 定义 Gender 枚举（MALE、FEMALE），配合 switch 根据传入的枚举值打印不同的问候语。
 * 3. 对比题：枚举项"不带参数"和"带参数"两种写法分别需要什么样的构造方法？先把 Season 改造成只描述名字的版本编译通过，再改回带描述的版本，体会两种构造方法的差异。
 * ============================================================
 */
