# 第12章 常见API

本章进入常用 API 的学习：先学会查阅 API 帮助文档，再重点掌握 String、StringBuilder 的常用操作与典型字符串算法题，最后引入 ArrayList 集合，为下一章阶段项目实战打好基础。

### 12-01 API和API帮助文档

**知识点**
- API（Application Programming Interface，应用程序编程接口）：Java 已经写好的类和方法，我们拿来直接调用，不必关心内部实现。
- 最常用的类大多在 `java.lang` 包（如 String、System、Math），使用时不需要 import；其他包的类（如 java.util 包的 Scanner、Random）必须先 import 再用。
- API 帮助文档（官方在线文档或离线 chm 文件）就是"类的说明书"，遇到陌生的类和方法，第一步就是查文档。
- 查文档的步骤：搜索类名 → 看包名和类的说明 → 看构造方法摘要（怎么创建对象）→ 看方法摘要（方法名、参数、返回值）。
- 看方法签名的方法：参数列表决定"要传什么"，返回值类型决定"要不要接收结果"，void 表示没有返回值。
- 易错点：很多方法不会修改原数据（如字符串方法），必须用变量接收返回值，否则调用等于白调。

**示例代码**
```java
import java.util.Random;
import java.util.Scanner;

public class ApiDemo {
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
```

**课后练习**
1. 打开 JDK 的 API 帮助文档，找到 String 类的 length 方法，说明它的功能、返回值类型以及是否有参数。
2. 在文档中查找 Random 类的 nextInt(int bound) 方法，说明参数 bound 的含义和返回值的取值范围。
3. 键盘录入一个浮点数，通过查阅 Math 类文档，输出它的绝对值、四舍五入后的整数以及向上取整的结果。

### 12-02 字符串的概述

**知识点**
- String 表示字符串，在 java.lang 包下；程序中所有双引号字面量（如 "abc"）都是 String 类的对象。
- String 是不可变的（immutable）：对象内容一旦创建就不能改变，看似"修改"字符串，其实都是产生了新的字符串对象。
- 直接赋值创建的字符串会存入串池（字符串常量池，JDK7 及以后位于堆中），相同内容的字面量只保存一份、共享地址。
- `new String("abc")` 每次都会在堆内存开辟新空间，地址与串池中的不同，所以内容相同地址也可能不同。
- 常见构造方法：`String()` 创建空串、`String(char[] arr)`、`String(byte[] arr)`；反向转换用 toCharArray()、getBytes()。
- 易错点：`s + "world"` 这类拼接每次都会产生新对象，频繁拼接效率低，这正是后面 StringBuilder 要解决的问题。

**示例代码**
```java
public class StringDemo {
    public static void main(String[] args) {
        // 方式一：直接赋值，字符串存放在串池中，相同内容会共享
        String s1 = "abc";
        String s2 = "abc";
        System.out.println(s1 == s2); // true，两个引用指向串池中同一个对象

        // 方式二：用 char 数组构造字符串（new 出来的对象在堆内存中）
        char[] chars = {'a', 'b', 'c'};
        String s3 = new String(chars);
        System.out.println(s3); // abc

        // 用 byte 数组构造字符串（传的是字符对应的数字）
        byte[] bytes = {97, 98, 99};
        String s4 = new String(bytes);
        System.out.println(s4); // abc

        // String 是不可变的：下面的"修改"其实是创建了新对象
        String s5 = "hello";
        s5 = s5 + " world";
        System.out.println(s5); // hello world，原来的 "hello" 并没有被改变
    }
}
```

**课后练习**
1. 分别用直接赋值和 `new String("abc")` 创建两个内容相同的字符串，用 == 比较两者的地址，运行后解释结果差异的原因。
2. 把字符串 "hello" 转成 char 数组并逐个打印，再把 char 数组重新转回字符串输出。
3. 思考题：执行 `String s = "a"; s += "b"; s += "c";` 之后，内存中一共产生过多少个字符串对象？

### 12-03 字符串的比较、遍历和两个小练习

**知识点**
- `==` 比较的是地址值；比较字符串内容必须用 equals（区分大小写），忽略大小写用 equalsIgnoreCase。
- 键盘录入的字符串是 new 出来的，即使内容和字面量相同，用 == 比较也是 false，所以字符串比较一律用 equals。
- 遍历方式一：`for (int i = 0; i < s.length(); i++)` 配合 `s.charAt(i)` 按索引取字符；方式二：toCharArray() 转成数组后遍历。
- 小练习 1（统计字符出现次数）：遍历每个字符与目标字符比较，相等就把计数器加 1。
- 小练习 2（字符串反转）：可以倒序遍历逐个拼接，也可以转成 char 数组后首尾交换，再转回字符串。

**示例代码**
```java
public class StringTraverseDemo {
    public static void main(String[] args) {
        // ===== 练习1：统计某字符出现次数 =====
        String str = "abcabcdabcde";
        char target = 'a';
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            // charAt(i) 获取索引 i 上的字符
            if (str.charAt(i) == target) {
                count++;
            }
        }
        System.out.println(str + " 中 '" + target + "' 出现了 " + count + " 次"); // 3 次

        // ===== 练习2：字符串反转 =====
        String s = "abcdef";
        // 方式一：倒序遍历，逐个拼接到新字符串中
        String result = "";
        for (int i = s.length() - 1; i >= 0; i--) {
            result += s.charAt(i);
        }
        System.out.println(s + " 反转后：" + result); // fedcba

        // 方式二：转成 char 数组，首尾交换后转回字符串
        char[] arr = s.toCharArray();
        for (int i = 0, j = arr.length - 1; i < j; i++, j--) {
            char temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }
        System.out.println(s + " 反转后：" + new String(arr)); // fedcba
    }
}
```

**课后练习**
1. 键盘录入一个字符串，统计其中数字字符出现的个数。如输入 a1b22c3，输出 4。
2. 键盘录入一个字符串和一个目标字符，输出目标字符在字符串中每一次出现的位置（索引），一个也没有时输出 -1。
3. 键盘录入一个字符串，在每个字符之间插入一个空格后输出。如输入 abc，输出 a b c。

### 12-04 字符串的截取、替换和其他常见方法

**知识点**
- substring(int begin, int end) 截取 [begin, end) 范围，包头不包尾；substring(int begin) 从 begin 一路截取到末尾。
- replace(oldStr, newStr) 把所有旧内容替换成新内容；split("规则") 按规则切割成 String 数组，参数是正则表达式，遇到 `.`、`|` 等特殊符号要写成 `\\.`、`\\|`。
- contains 判断是否包含、indexOf 查找第一次出现的索引（找不到返回 -1）、startsWith / endsWith 判断开头和结尾。
- toCharArray / getBytes 转数组，String.valueOf 把数字等转成字符串，trim 去掉首尾空格。
- toUpperCase / toLowerCase 大小写转换，isEmpty 判断长度是否为 0。
- 易错点：这些方法都不改动原字符串，而是返回一个新字符串，必须接收返回值才有意义。

**示例代码**
```java
import java.util.Arrays;

public class StringMethodDemo {
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
```

**课后练习**
1. 给定字符串 "Hello-World-Java"，截取其中的 World 和 Java，拼接成 WorldJava 输出。
2. 键盘录入一句英文（包含空格），按空格切割成单词，统计单词个数并逐行输出每个单词及其长度。
3. 键盘录入一个邮箱字符串，判断是否包含 @：包含则输出 @ 之前和之后的两段内容，不包含则输出"格式有误"。

### 12-05 StringBuilder

**知识点**
- StringBuilder 是可变的字符串容器，所有拼接、修改都在同一个对象上进行，比 String 用 + 拼接效率高很多。
- 常用方法：append 追加内容（返回自身，支持链式编程）、reverse 反转、length 获取长度、toString 转回 String。
- 构造方法：`new StringBuilder()` 创建空容器，`new StringBuilder("内容")` 创建带初始内容的容器。
- String 与 StringBuilder 互相转换：String 用构造方法转进去，StringBuilder 用 toString() 转回来。
- 使用场景：循环拼接、频繁修改内容用 StringBuilder；内容固定不变的字符串用 String。
- 底层维护一个数组，容量不够时自动扩容，使用时"只管往里装"。

**示例代码**
```java
public class StringBuilderDemo {
    public static void main(String[] args) {
        // 创建一个空的 StringBuilder 容器
        StringBuilder sb = new StringBuilder();

        // append 追加内容，返回自身，可以链式调用
        sb.append("黑马").append("程序员").append(2026).append(true);
        System.out.println(sb); // 黑马程序员2026true

        // reverse 反转容器中的内容
        sb.reverse();
        System.out.println(sb); // eurt6202员序程马黑

        // length 获取字符个数
        System.out.println(sb.length()); // 13

        // toString 转回 String 类型
        String s = sb.toString();
        System.out.println(s.toUpperCase()); // EURT6202员序程马黑

        // 验证效率：循环拼接 10 万次，StringBuilder 非常快
        long start = System.currentTimeMillis();
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            sb2.append(i);
        }
        long end = System.currentTimeMillis();
        System.out.println("StringBuilder 拼接耗时：" + (end - start) + " 毫秒");
    }
}
```

**课后练习**
1. 键盘录入一个字符串，用 StringBuilder 反转后输出。
2. 把 int 数组 {1, 2, 3} 拼接成 "[1, 2, 3]" 格式的字符串输出，要求用 StringBuilder 完成。
3. 分别用 String 的 + 和 StringBuilder 循环拼接 1~100000 的数字，用 System.currentTimeMillis() 统计两种方式的耗时并对比。

### 12-06 字符串的四个算法小练习

**知识点**
- 题目 1（对称字符串）：键盘录入一个字符串判断是否对称，如 "abcba" 对称、"abcabc" 不对称。思路：双指针，一个从头、一个从尾逐个比较。
- 题目 2（首字母大写）：把字符串变成首字母大写、其余小写，如 "HELLO" 变成 "Hello"。思路：截取首字母转大写，截取剩余部分转小写，再拼接。
- 题目 3（统计字符类型）：统计字符串中大写字母、小写字母、数字的个数。思路：遍历后做字符范围判断，如 `ch >= 'a' && ch <= 'z'`。
- 题目 4（旋转字符串）：判断字符串 b 能否由字符串 a 旋转若干次得到，如 "abcde" 旋转后可以得到 "cdeab"。思路：每次把首字符挪到末尾算一次旋转，在旋转 a 的长度次以内能匹配即成立。
- 通用套路：先写清楚输入输出 → 想明白循环与判断条件 → 再动手敲代码，写完用特殊数据测试。
- 本节示例完整实现题目 1 和题目 4，题目 2、3 留作课后练习。

**示例代码**
```java
public class StringAlgorithmDemo {
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
```

**课后练习**
1. 键盘录入一个字符串，把首字母大写、其余字母小写后输出。如输入 HELLO，输出 Hello。
2. 键盘录入一个字符串，统计其中大写字母、小写字母、数字分别出现的个数。如输入 Ab12，输出 大写1、小写1、数字2。
3. 键盘录入一个字符串 s 和一个整数 k，输出 s 向右旋转 k 位后的结果。如输入 abcde 和 2，输出 deabc。

### 12-07 算法练习-字符串计算（难）

**知识点**
- 题意（力扣第 13 题）：罗马数字由 I V X L C D M 七种字符组成，对应 1、5、10、50、100、500、1000，给定罗马数字字符串，求出它表示的整数，如 MCMXCIV = 1994。
- 减法规则：小值写在大值左边表示减法，共六种情况：IV=4、IX=9、XL=40、XC=90、CD=400、CM=900。
- 核心思路：一次遍历，比较当前字符与右边字符的值——当前值小于右边值就做减法，否则做加法。
- 技巧：用 switch 或查表法把字符换成数值；访问右边字符前先判断 `i + 1 < length`，防止索引越界。
- 复杂度：时间 O(n)（只遍历一遍），空间 O(1)。

**示例代码**
```java
public class RomanToInteger {
    public static void main(String[] args) {
        // 多组数据验证：III=3, IV=4, LVIII=58, MCMXCIV=1994
        System.out.println(romanToInt("III"));     // 3
        System.out.println(romanToInt("IV"));      // 4
        System.out.println(romanToInt("LVIII"));   // 58
        System.out.println(romanToInt("MCMXCIV")); // 1994
    }

    // 罗马数字转整数：小值在大值左边时做减法，否则做加法
    public static int romanToInt(String s) {
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            int cur = getValue(s.charAt(i));
            // 当前字符的值小于右边字符的值时，当前值做减法（如 IV 中的 I）
            if (i + 1 < s.length() && cur < getValue(s.charAt(i + 1))) {
                sum -= cur;
            } else {
                sum += cur;
            }
        }
        return sum;
    }

    // 查表：把罗马字符换成对应的数值
    public static int getValue(char c) {
        switch (c) {
            case 'I': return 1;
            case 'V': return 5;
            case 'X': return 10;
            case 'L': return 50;
            case 'C': return 100;
            case 'D': return 500;
            case 'M': return 1000;
            default:  return 0;
        }
    }
}
```

**课后练习**
1. 键盘录入一个合法的罗马数字字符串（只包含 I、V、X 三种字符），输出对应的整数。如输入 XIV，输出 14。
2. 压缩字符串：键盘录入一个只含小写字母的字符串，把连续出现的相同字符压缩成"字符+次数"的形式。如输入 aaabbc，输出 a3b2c1。
3. 整数转罗马数字（力扣第 12 题）：编写方法把整数转成罗马数字。如输入 1994，输出 MCMXCIV；输入 58，输出 LVIII。

### 12-08 字符串的作业

**知识点**
- 作业 1（统计每个字符出现次数）：键盘录入一个字符串，输出每个字符及其出现的次数。如输入 aabbc，输出 a=2、b=2、c=1。
- 作业 2（判断纯数字）：键盘录入一个字符串，判断它是否全部由数字字符组成，输出 true 或 false；空字符串视为 false。
- 作业 3（手机号打码）：键盘录入 11 位手机号，输出打码后的结果，如 13812345678 输出 138****5678；长度不是 11 位时输出"录入有误"。
- 作业 4（拆分排序）：把字符串 "c:18,b:20,a:22" 按逗号拆分成若干项，再按字母升序输出每一项。
- 作业 5（最长公共前缀）：编写方法求两个字符串的最长公共前缀，如 flower 和 flow 返回 flow，没有公共前缀时返回空字符串。
- 统一要求：全部封装成方法，方法内部不写 Scanner 和打印语句，由 main 方法传参调用并打印结果。

**示例代码**
```java
import java.util.Arrays;

public class StringSplitDemo {
    public static void main(String[] args) {
        // split 小工具演示：按指定规则把字符串切成数组
        String date = "2026-06-01";
        String[] parts = date.split("-");

        // 切割结果是一个字符串数组，遍历打印每一项
        for (int i = 0; i < parts.length; i++) {
            System.out.println("第 " + (i + 1) + " 项：" + parts[i]);
        }

        // split 的参数是正则表达式，遇到 . 需要转义成 \\.
        String version = "jdk17.0.9";
        System.out.println(Arrays.toString(version.split("\\."))); // [jdk17, 0, 9]
    }
}
```

**课后练习**
1. 统计每个字符出现次数：键盘录入一个字符串，输出每个字符及其出现次数。如输入 aabbc，输出 a=2、b=2、c=1。
2. 判断纯数字：键盘录入一个字符串，判断它是否全部由数字字符组成，输出 true 或 false（空字符串视为 false）。
3. 手机号打码：键盘录入 11 位手机号，输出打码结果，如 13812345678 输出 138****5678；长度不是 11 位时输出"录入有误"。
4. 拆分排序：把字符串 "c:18,b:20,a:22" 按逗号拆分，并按字母升序输出每一项。
5. 最长公共前缀：编写方法求两个字符串的最长公共前缀，如 flower 和 flow 返回 flow，没有公共前缀时返回空字符串。

### 12-09 ArrayList集合

**知识点**
- 数组长度固定、增删要手动搬元素；ArrayList 集合长度可变、自动扩容，适合存"数量不确定"的一批数据。
- `ArrayList<E>` 在 java.util 包下，E 是泛型：限定集合只能存这一种类型，如 ArrayList<String>；尖括号里只能写引用类型。
- 基本类型要存进集合需要用包装类：ArrayList<Integer>、ArrayList<Double>（JDK5 之后 int 与 Integer 自动装箱、拆箱）。
- 五个常用方法：add(e) 添加、get(i) 获取、remove 删除、set(i, e) 修改、size() 获取长度；集合可以直接打印查看内容。
- remove(整数) 默认按索引删除；想按内容删除要传对象，如 list.remove(Integer.valueOf(10))，这是初学者常见的歧义点。
- 遍历删除陷阱：for-each 或普通 for 正序删除会漏删元素，for-each 中直接 remove 还会报 ConcurrentModificationException；解决办法是倒序遍历删除，或使用迭代器的 remove 方法。

**示例代码**
```java
import java.util.ArrayList;

public class ArrayListDemo {
    public static void main(String[] args) {
        // 创建集合，泛型限定只能存 String
        ArrayList<String> list = new ArrayList<>();

        // add 添加元素
        list.add("Java");
        list.add("Python");
        list.add("C++");
        System.out.println(list); // [Java, Python, C++]，集合可以直接打印

        // get 根据索引获取，size 获取长度
        System.out.println(list.get(0));            // Java
        System.out.println("集合长度：" + list.size()); // 3

        // set 修改指定索引的元素
        list.set(1, "Go");
        System.out.println(list); // [Java, Go, C++]

        // remove 按索引删除
        list.remove(2);
        System.out.println(list); // [Java, Go]

        // ===== 遍历删除陷阱：删除所有包含 a 的元素 =====
        ArrayList<String> words = new ArrayList<>();
        words.add("abc");
        words.add("aaa");
        words.add("bbb");
        words.add("acc");

        // 注意：正序遍历删除会漏删元素，for-each 中删除会报异常
        // 正确做法：倒序遍历删除
        for (int i = words.size() - 1; i >= 0; i--) {
            if (words.get(i).contains("a")) {
                words.remove(i);
            }
        }
        System.out.println(words); // [bbb]
    }
}
```

**课后练习**
1. 创建 ArrayList<Integer>，存入 10 个 1~100 的随机整数，遍历求出最大值、最小值与总和。
2. 创建 ArrayList<String> 存 5 个电影名，键盘录入一个电影名，存在则删除并提示删除成功，不存在则提示未找到。
3. 创建 ArrayList<Integer> 存入 8 个含重复值的整数，删除其中所有偶数后输出剩余元素。

### 12-10 ArrayList练习

**知识点**
- 自定义类型存集合四步走：定义 JavaBean → 创建对象 → add 存入集合 → get 取出使用。
- 集合里存的是对象的地址值；直接打印对象名得到的是"类名@地址值"，需要自己拼接属性来展示（后续会学重写 toString）。
- 查找元素的套路：遍历逐个比较，找到返回索引，约定俗成找不到返回 -1。
- 按条件删除的套路：倒序遍历 + 条件判断 + remove，避免漏删。
- 做集合题先想清楚"存什么（类）、怎么存（集合）、怎么处理（遍历逻辑）"，再动手写代码。

**示例代码**
```java
// 文件名: Student.java
public class Student {
    private String name;
    private int score;

    public Student() {
    }

    public Student(String name, int score) {
        this.name = name;
        this.score = score;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }
}
```

```java
// 文件名: ArrayListStudentDemo.java
import java.util.ArrayList;

public class ArrayListStudentDemo {
    public static void main(String[] args) {
        // 练习1：存学生对象并遍历
        ArrayList<Student> list = new ArrayList<>();
        list.add(new Student("张三", 85));
        list.add(new Student("李四", 59));
        list.add(new Student("王五", 92));
        list.add(new Student("赵六", 47));

        for (int i = 0; i < list.size(); i++) {
            Student stu = list.get(i);
            System.out.println(stu.getName() + " 的成绩是 " + stu.getScore());
        }

        // 练习2：查找集合中的元素，按姓名查找并返回索引
        System.out.println("王五的索引：" + findByName(list, "王五")); // 2

        // 练习3：删除所有不及格（分数低于 60）的学生，倒序删除避免漏删
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).getScore() < 60) {
                list.remove(i);
            }
        }
        System.out.println("删除不及格学生后：");
        for (Student stu : list) {
            System.out.println(stu.getName() + " " + stu.getScore());
        }
    }

    // 按姓名查找，找到返回索引，找不到返回 -1
    public static int findByName(ArrayList<Student> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
```

**课后练习**
1. 定义 Student 类（姓名、年龄），存 3 个学生对象到集合，键盘录入一个年龄，删除集合中所有该年龄的学生并输出剩余学生。
2. 求集合中分数最高的学生对象并输出其全部信息；集合为空时给出友好提示。
3. 定义 Book 类（书名、价格），创建集合存 5 本书，把价格高于 50 元的书筛选到新集合并输出。

## 本章综合练习

### 综合题：通讯录与文本清洗

**场景**

做一个控制台通讯录：用 ArrayList 存联系人，支持增删改查与按姓名排序；再做一轮文本处理，统计敏感词出现次数并把手机号中间四位打码。String 与 ArrayList 的常用 API 要全部覆盖。

**任务**
1. 定义 Contact 类（name、phone、group），用 ArrayList<Contact> 实现增删改查，删除时按下标与按对象两种方式各写一遍。
2. 实现按姓名排序（String 的 compareTo），并说明为什么不直接用 == 比较字符串。
3. 实现敏感词统计：给定一段文本与词表，用 indexOf 循环计数，输出每个词的出现次数。
4. 实现手机号打码：11 位手机号保留前 3 后 4，中间用 **** 替换，用 StringBuilder 拼接；长度非 11 位时原样返回。

**验收清单**
- [ ] 增删改查四个操作各有输出验证，删除不存在的元素有提示且不报错。
- [ ] 排序后姓名按字典序升序，中文姓名也能稳定排序。
- [ ] 敏感词计数与人工核对一致，重叠出现的情况有说明。
- [ ] 打码方法对空串、短串、超长串都不抛异常。
