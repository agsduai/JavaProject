/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-16
 * 课名：接口和抽象类的综合案例
 * ------------------------------------------------------------
 * 知识点：
 *   - 题面：训练基地有乒乓球运动员、篮球运动员、乒乓球教练、篮球教练。运动员都要“学习（learn）”，教练都要“教学（teach）”；其中乒乓球相关人员还要“会说英语”，篮球运动员还要“会打篮球”。要求用抽象类 + 接口完成设计
 *   - 设计第一步（抽共性）：顶层父类 Person 封装共有属性姓名、年龄
 *   - 设计第二步（定规则）：抽象类 Sporter（含抽象方法 learn）与抽象类 Coach（含抽象方法 teach）分别继承 Person，把“运动员必须会学习、教练必须会教学”定成规则
 *   - 设计第三步（附加能力做成接口）：接口 SpeakEnglish（会说英语）、PlayBasketball（会打篮球）；哪个类需要哪种能力，就实现哪个接口
 *   - 设计原则：本质身份和共性属性方法放抽象类（is-a 关系），扩展能力放接口（can-do 关系）；一个类可以同时实现多个接口
 *   - 考点覆盖：抽象类继承与抽象方法重写、接口多实现、super 全参构造、多态引用
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

// 顶层父类：抽取所有人员的共性属性
class InterfaceAbstractCasePerson {
    private String name; // 姓名
    private int age;     // 年龄

    public InterfaceAbstractCasePerson() {
    }

    public InterfaceAbstractCasePerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}

// 抽象类：运动员，制定“必须会学习”的规则
abstract class InterfaceAbstractCaseSporter extends InterfaceAbstractCasePerson {
    public InterfaceAbstractCaseSporter() {
    }

    public InterfaceAbstractCaseSporter(String name, int age) {
        super(name, age);
    }

    // 抽象方法：所有运动员都要学习
    public abstract void learn();
}

// 抽象类：教练，制定“必须会教学”的规则
abstract class InterfaceAbstractCaseCoach extends InterfaceAbstractCasePerson {
    public InterfaceAbstractCaseCoach() {
    }

    public InterfaceAbstractCaseCoach(String name, int age) {
        super(name, age);
    }

    // 抽象方法：所有教练都要教学
    public abstract void teach();
}

// 接口：会说英语（附加能力）
interface InterfaceAbstractCaseSpeakEnglish {
    void speakEnglish(); // public abstract 可省略
}

// 接口：会打篮球（附加能力）
interface InterfaceAbstractCasePlayBasketball {
    void playBasketball();
}

// 乒乓球运动员：继承抽象类 + 同时实现两个接口（多实现）
class InterfaceAbstractCasePingPangSporter extends InterfaceAbstractCaseSporter implements InterfaceAbstractCaseSpeakEnglish, InterfaceAbstractCasePlayBasketball {
    public InterfaceAbstractCasePingPangSporter() {
    }

    public InterfaceAbstractCasePingPangSporter(String name, int age) {
        super(name, age);
    }

    @Override
    public void learn() {
        System.out.println(getName() + "在学习发球和接球");
    }

    @Override
    public void speakEnglish() {
        System.out.println(getName() + "会说英语，能和外国选手交流");
    }

    @Override
    public void playBasketball() {
        System.out.println(getName() + "闲暇时会打篮球锻炼身体");
    }
}

// 篮球运动员：只继承抽象类，不实现接口
class InterfaceAbstractCaseBasketballSporter extends InterfaceAbstractCaseSporter {
    public InterfaceAbstractCaseBasketballSporter() {
    }

    public InterfaceAbstractCaseBasketballSporter(String name, int age) {
        super(name, age);
    }

    @Override
    public void learn() {
        System.out.println(getName() + "在学习运球和投篮");
    }
}

// 乒乓球教练：继承抽象类 + 实现说英语接口
class InterfaceAbstractCasePingPangCoach extends InterfaceAbstractCaseCoach implements InterfaceAbstractCaseSpeakEnglish {
    public InterfaceAbstractCasePingPangCoach() {
    }

    public InterfaceAbstractCasePingPangCoach(String name, int age) {
        super(name, age);
    }

    @Override
    public void teach() {
        System.out.println(getName() + "在教乒乓球战术");
    }

    @Override
    public void speakEnglish() {
        System.out.println(getName() + "会说英语，能带外教课程");
    }
}

// 篮球教练：只继承抽象类
class InterfaceAbstractCaseBasketballCoach extends InterfaceAbstractCaseCoach {
    public InterfaceAbstractCaseBasketballCoach() {
    }

    public InterfaceAbstractCaseBasketballCoach(String name, int age) {
        super(name, age);
    }

    @Override
    public void teach() {
        System.out.println(getName() + "在教篮球战术");
    }
}

public class InterfaceAbstractCase {
    public static void main(String[] args) {
        // 多态：抽象类引用指向子类对象
        InterfaceAbstractCaseSporter s1 = new InterfaceAbstractCasePingPangSporter("马龙", 35);
        InterfaceAbstractCaseSporter s2 = new InterfaceAbstractCaseBasketballSporter("小姚", 22);
        InterfaceAbstractCaseCoach c1 = new InterfaceAbstractCasePingPangCoach("刘指导", 50);
        InterfaceAbstractCaseCoach c2 = new InterfaceAbstractCaseBasketballCoach("杜指导", 55);

        s1.learn();
        s2.learn();
        c1.teach();
        c2.teach();

        // 接口引用：指向具备该能力的对象
        InterfaceAbstractCaseSpeakEnglish se = new InterfaceAbstractCasePingPangSporter("小马", 21);
        se.speakEnglish();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在案例基础上新增接口 PlayVolleyball（会打排球），让篮球运动员额外实现该接口并输出对应内容，其余类保持不变。
 * 2. 新增一类“助教 Assistant”：继承 Coach 的同时实现 SpeakEnglish 接口，写出它的完整类定义，并补充测试代码验证其全部行为。
 * 3. 挑战题：给所有人员增加方法 printInfo() 输出“姓名 + 年龄 + 身份”，思考该方法放在哪一层（Person、抽象类还是接口）最合适，说明理由并实现。
 * ============================================================
 */
