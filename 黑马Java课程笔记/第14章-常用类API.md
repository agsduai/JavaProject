# 第14章 常用类API

本章围绕 Java 常用类与算法思维展开：从 Math 工具类与两道经典算法题入手，接着弄清 System/Object 的底层行为，再攻克包装类的装箱陷阱，最后掌握 BigInteger 与 BigDecimal 对大数运算和浮点精度的处理。

### 14-01 Math类和两道算法题

**知识点**

- Math 位于 java.lang 包，使用时不需要 import；构造方法私有、无法创建对象，所有方法都是静态的，直接 `Math.xxx()` 调用。
- `abs` 求绝对值；`ceil` 向上取整、`floor` 向下取整（参数和返回值都是 double）；`round` 四舍五入，返回 long。
- `max`/`min` 取两个数的最大、最小值；`pow(a, b)` 求 a 的 b 次幂；`sqrt` 开平方根，返回 double。
- `random()` 生成 `[0.0, 1.0)` 之间的随机小数，乘上范围长度再平移起点，即可得到任意区间的随机数。
- 统计一个整数的位数有两种思路：循环除以 10 并计数；或借助 `Math.log10` 取对数后再取整。
- 易错：`Math.abs(Integer.MIN_VALUE)` 的结果仍是负数（int 溢出），需要先转成 long 再取绝对值。

**示例代码**

```java
public class MathDemo {
    public static void main(String[] args) {
        System.out.println(Math.abs(-8));      // 8 绝对值
        System.out.println(Math.ceil(3.1));    // 4.0 向上取整
        System.out.println(Math.floor(3.9));   // 3.0 向下取整
        System.out.println(Math.round(3.5));   // 4 四舍五入
        System.out.println(Math.max(10, 20));  // 20 最大值
        System.out.println(Math.pow(2, 10));   // 1024.0 幂运算
        System.out.println(Math.sqrt(16));     // 4.0 开平方
        System.out.println(Math.random());     // [0.0, 1.0) 的随机小数

        // 算法题：统计一个整数的位数
        System.out.println(countDigits(0));     // 1
        System.out.println(countDigits(4567));  // 4
        System.out.println(countDigits(-908));  // 3
    }

    // 循环除以 10 并计数，直到变成 0
    public static int countDigits(int num) {
        if (num == 0) {
            return 1; // 0 只有一位，特殊处理
        }
        long n = Math.abs((long) num); // 先转 long 再取绝对值，防止溢出
        int count = 0;
        while (n > 0) {
            n = n / 10;
            count++;
        }
        return count;
    }
}
```

**课后练习**

1. 判断一个非负整数是否是完全平方数：编写方法接收一个 int 参数，若存在整数 k 使得 k*k 恰好等于该数，输出"是完全平方数"，否则输出"不是完全平方数"。示例：16 → 是；17 → 不是；0 → 是。
2. 编写方法 `randomInRange(int min, int max)`，利用 Math.random 返回 [min, max] 范围内的随机整数，并循环生成 1000 个结果验证它们全部落在范围内。
3. 不使用循环，改用 `Math.log10` 实现统计整数位数的方法，并测试 0、负数、Integer.MAX_VALUE 三种特殊情况，观察结果是否正确。

### 14-02 System类和Object类

**知识点**

- `System.currentTimeMillis()` 返回当前时刻距 1970 年 1 月 1 日 00:00:00（UTC）的毫秒值，两次调用相减即可统计一段代码的运行耗时。
- `System.exit(status)` 终止 JVM：参数 0 表示正常退出，非 0 表示异常退出。
- `System.arraycopy(src, srcPos, dest, destPos, length)` 底层数组拷贝，效率高；引用类型元素只拷贝地址（浅拷贝）。
- Object 是所有类的直接或间接父类，任何类都可以重写它的方法。
- toString 默认返回"类名@十六进制地址"；打印对象时 println 会自动调用它，重写后输出内容更可读。
- equals 默认等同 `==`，比较的是地址值；重写后改为比较属性内容。String 类已重写 equals，所以字符串能比较内容。

**示例代码**

```java
// 注意：一个 .java 文件中只能有一个 public 类
class Student {
    private String name;
    private int age;

    public Student(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // 重写 toString：打印对象内容，而不是"类名@地址"
    @Override
    public String toString() {
        return "Student{name=" + name + ", age=" + age + "}";
    }

    // 重写 equals：比较属性内容，而不是地址值
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true; // 自己和自己比
        if (obj == null || getClass() != obj.getClass()) return false;
        Student s = (Student) obj;
        return age == s.age && name.equals(s.name);
    }
}

public class SystemObjectDemo {
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
        Student s1 = new Student("阿玮", 18);
        Student s2 = new Student("阿玮", 18);
        System.out.println(s1.equals(s2)); // true
        System.out.println(s1);            // Student{name=阿玮, age=18}
    }
}
```

**课后练习**

1. 编写程序，循环 10000 次把数字拼接到一个 String 对象上，用 System.currentTimeMillis 统计并输出这段拼接代码的耗时毫秒数。
2. 定义 Point 类（成员变量 int x、int y），重写 toString 和 equals，使得两个点的 x、y 都相同时 equals 返回 true；在 main 中创建三个点对象（内容相同、内容不同、与自身比较）逐一验证。
3. 有数组 `{1, 2, 3, 4, 5, 6, 7, 8}`，只允许使用 System.arraycopy（可借助一个临时数组），实现把数组整体向左循环移动 2 位，得到 `{3, 4, 5, 6, 7, 8, 1, 2}`。

### 14-03 包装类

**知识点**

- 8 种基本类型对应 8 种包装类：byte→Byte、short→Short、int→Integer、long→Long、float→Float、double→Double、char→Character、boolean→Boolean（int 和 char 需要特殊记忆）。
- 包装类让基本类型拥有对象的能力：泛型集合、工具方法等都要求包装类。
- 自动装箱：int → Integer，底层调用 `Integer.valueOf`；自动拆箱：Integer → int，底层调用 `intValue`，都由编译器自动完成。
- `Integer.parseInt("123")` 把字符串转成 int，是最常用的转换方法；格式非法会抛 NumberFormatException。
- 缓存池陷阱：IntegerCache 事先缓存了 -128 ~ 127 的 Integer 对象，该范围内 `==` 比较为 true，超出范围则为 false（新对象）。
- 结论：包装类比较内容一律用 equals；Integer 变量为 null 时自动拆箱会抛 NullPointerException。

**示例代码**

```java
public class WrapperDemo {
    public static void main(String[] args) {
        // 自动装箱：基本类型 -> 包装类（底层是 Integer.valueOf）
        Integer a = 100;
        // 自动拆箱：包装类 -> 基本类型（底层是 intValue）
        int b = a + 1;
        System.out.println("拆箱运算结果：" + b);

        // 字符串转整数：最常用的方式
        int num = Integer.parseInt("12345");
        System.out.println("转换后加 1：" + (num + 1));
        // Integer.parseInt("12a3"); // 运行时抛 NumberFormatException

        // 缓存池陷阱：-128 ~ 127 之间指向同一个对象
        Integer x = 127;
        Integer y = 127;
        System.out.println(x == y);      // true，命中缓存池
        Integer m = 128;
        Integer n = 128;
        System.out.println(m == n);      // false，超出范围是新对象
        System.out.println(m.equals(n)); // true，包装类比较内容必须用 equals

        // 易错：null 拆箱会抛空指针
        Integer empty = null;
        // int danger = empty; // NullPointerException
        System.out.println("安全写法：" + (empty == null ? 0 : empty));
    }
}
```

**课后练习**

1. 定义字符串 `"666"`，把它转成 int 后乘以 2 并输出结果；再尝试用 `Integer.parseInt` 转换字符串 `"66a6"`，运行后阅读控制台异常信息，记录异常类型和出错原因。
2. 编写方法 `parseIntSafely(String s)`：字符串合法时返回对应的整数，不合法时返回 -1 且程序不能崩溃；在 main 中分别用 `"1024"`、`"12x"`、空字符串三种输入测试。
3. 先预测下面每个输出的值再运行验证：`Integer a = 100, b = 100, c = 200, d = 200;`，分别输出 `a == b`、`c == d`、`a.equals(b)`、`c.equals(d)` 的结果；若有预测错误的，把所有 `==` 比较改写成 equals 并解释为什么 `==` 不可靠。

### 14-04 BigInteger和BigDecimal

**知识点**

- BigInteger 表示任意大小的整数，long 装不下时使用；推荐用字符串构造 `new BigInteger("...")`，或用 `BigInteger.valueOf(long)`。
- BigInteger 和 BigDecimal 都是不可变对象，add/subtract/multiply/divide 等运算不会改变原对象，而是返回新对象。
- BigDecimal 解决二进制浮点数的精度丢失问题（如 double 算出 0.1 + 0.2 ≠ 0.3），适合金额等精确计算场景。
- 创建建议：`new BigDecimal("0.1")` 字符串构造或 `BigDecimal.valueOf(0.1)`；避免 `new BigDecimal(0.1)`，它会把 double 本身的误差带入。
- divide 除不尽时（如 10÷3）必须指定小数位数和舍入模式 RoundingMode（HALF_UP 为四舍五入），否则抛 ArithmeticException。
- 比较数值大小用 compareTo；equals 会连同精度一起比较，`1.0` 和 `1.00` 用 equals 结果为 false。

**示例代码**

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class BigDecimalDemo {
    public static void main(String[] args) {
        // BigInteger：long 存不下的大整数运算
        BigInteger big1 = new BigInteger("99999999999999999999999999");
        BigInteger big2 = BigInteger.valueOf(100);
        System.out.println("大数加法：" + big1.add(big2));
        System.out.println("大数乘法：" + big1.multiply(big2));
        System.out.println("2 的 100 次方：" + BigInteger.valueOf(2).pow(100));

        // BigDecimal：解决浮点精度问题
        System.out.println(0.1 + 0.2); // double 直接算：0.30000000000000004
        BigDecimal d1 = new BigDecimal("0.1"); // 推荐用字符串构造
        BigDecimal d2 = BigDecimal.valueOf(0.2);
        System.out.println("精确加法：" + d1.add(d2)); // 0.3

        // 四则运算：add 加 / subtract 减 / multiply 乘 / divide 除
        BigDecimal price = new BigDecimal("19.99");
        BigDecimal count = BigDecimal.valueOf(3);
        System.out.println("商品总价：" + price.multiply(count)); // 59.97

        // 除不尽时必须指定精度和舍入模式，否则抛 ArithmeticException
        BigDecimal result = new BigDecimal("10")
                .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        System.out.println("10 ÷ 3 保留 2 位：" + result); // 3.33

        // 比较大小用 compareTo，equals 会连精度一起比
        System.out.println(new BigDecimal("1.0").equals(new BigDecimal("1.00")));    // false
        System.out.println(new BigDecimal("1.0").compareTo(new BigDecimal("1.00"))); // 0
    }
}
```

**课后练习**

1. 分别用 double 和 BigDecimal 计算 0.1 + 0.2 + 0.3，把两个结果都打印出来，对比它们是否一致。
2. 超市结算：单价 3.5 元的商品买了 7 件，用 BigDecimal 计算总价；再把 100 元平均分给 3 人，计算每人应付金额并保留 2 位小数（四舍五入）。
3. 用 BigInteger 计算 100 的阶乘（100!）并输出结果，然后统计这个结果一共有多少位数字。

## 本章综合练习

### 综合题：金融分期计算器

**场景**

做一个小型分期计算器：本金、期数、年利率由用户输入，输出每期应还、总利息、总还款；同时用 Math 做取整与幂运算、用包装类处理空值、用 BigInteger 演示阶乘溢出。金额一律用 BigDecimal。

**任务**
1. 用 BigDecimal 实现等额本息每期还款额计算，结果保留两位小数并指定 HALF_UP 舍入，说明为什么不能用 double。
2. 用 Math 实现四舍五入到分、向上取整到元、求最大最小值，对比 Math.round 与 BigDecimal.setScale 的差异。
3. 用 Integer 包装类演示自动装箱拆箱，并写出一个 null 拆箱导致 NullPointerException 的例子及修复方式。
4. 用 BigInteger 计算 50! 并与 long 版本对比，说明 long 在哪个阶乘开始溢出。

**验收清单**
- [ ] 分期结果与手工计算误差不超过 0.01 元，每期金额之和等于总还款额。
- [ ] 本金为 0、期数为 0、利率为 0 三种边界都有明确处理。
- [ ] null 拆箱的异常能被复现，并在修复后不再出现。
- [ ] 所有金额变量类型统一为 BigDecimal，代码中不出现 double 参与金额运算。
