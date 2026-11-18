/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-02
 * 课名：单例模式：五种写法与两种破坏
 * ------------------------------------------------------------
 * 知识点：
 *   - 单例要保证：**构造私有**、**自行创建唯一实例**、**对外提供统一访问点**。缺任何一条都不是单例。
 *   - **饿汉式**：类加载即创建，天生线程安全，但可能创建了却从没用过（有初始化开销时浪费）。
 *   - **懒汉式（无同步）**：`if (instance == null) instance = new X()` 在多线程下会创建多个实例，是典型的"看起来对、并发下错"。
 *   - **双重检查锁 DCL**：`instance` 必须 `volatile`。因为 `new` 不是原子操作，分三步（分配内存、初始化、赋值引用），重排序会让另一个线程看到"引用非空但字段还是默认值"的半成品对象。
 *   - **静态内部类**：利用类加载机制保证线程安全与懒加载，是"无锁懒加载"的最简写法；`Holder` 类只在第一次调用 `getInstance()` 时被初始化。
 *   - **枚举**：`enum` 的构造由 JVM 保证只执行一次，且**反射无法创建枚举实例**、**反序列化不会产生新对象**，是《Effective Java》推荐写法。
 *   - 两种破坏手段：**反射**（对私有构造 `setAccessible(true)` 后 `newInstance`）、**反序列化**（`readObject` 会新建对象，需写 `readResolve`）。
 *   - 防御手段：枚举最彻底；普通类可在构造器里加"已创建则抛异常"的判断；反序列化场景补 `readResolve()` 返回单例。
 *   - 易错：以为 `synchronized` 加在方法上就万事大吉（性能差且没解决反序列化）；DCL 漏写 `volatile`；把单例当"全局变量容器"存请求级数据，直接引发线程安全问题。
 * ============================================================
 */

package com.heima.ch31;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// ==================== 示例代码 ====================

public class SingletonPattern {
    /** 写法一：双重检查锁，instance 必须 volatile */
    static class DclSingleton {
        private static volatile DclSingleton instance;
        private final String tag;

        private DclSingleton() {
            tag = "DCL#" + System.identityHashCode(this);
        }

        static DclSingleton getInstance() {
            if (instance == null) {
                synchronized (DclSingleton.class) {
                    if (instance == null) {
                        instance = new DclSingleton();
                    }
                }
            }
            return instance;
        }

        String tag() {
            return tag;
        }
    }

    /** 写法二：静态内部类，无锁懒加载 */
    static class HolderSingleton {
        private HolderSingleton() {
        }

        private static class Holder {
            private static final HolderSingleton INSTANCE = new HolderSingleton();
        }

        static HolderSingleton getInstance() {
            return Holder.INSTANCE;
        }
    }

    /** 写法三：枚举，天然防反射、防反序列化 */
    enum EnumSingleton {
        INSTANCE;

        String tag() {
            return "ENUM#" + System.identityHashCode(this);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 200 线程并发取 DCL 实例 ===");
        Set<Integer> ids = Collections.newSetFromMap(new ConcurrentHashMap<>());
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(200);
        for (int i = 0; i < 200; i++) {
            pool.submit(() -> {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ids.add(System.identityHashCode(DclSingleton.getInstance()));
                done.countDown();
            });
        }
        gate.countDown();
        done.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        System.out.println("  不同实例个数 = " + ids.size() + "（正确结果应为 1）");
        System.out.println("  实例标识 = " + DclSingleton.getInstance().tag());

        System.out.println();
        System.out.println("=== 2. 静态内部类懒加载 ===");
        System.out.println("  调用前 Holder 未初始化，首次 getInstance 才加载 Holder 类");
        System.out.println("  a == b : " + (HolderSingleton.getInstance() == HolderSingleton.getInstance()));

        System.out.println();
        System.out.println("=== 3. 反射破坏 DCL 单例 ===");
        Constructor<DclSingleton> ctor = DclSingleton.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        DclSingleton hacked = ctor.newInstance();
        System.out.println("  反射创建成功，是否同一对象: " + (hacked == DclSingleton.getInstance()));
        System.out.println("  第二个实例 = " + hacked.tag());

        System.out.println();
        System.out.println("=== 4. 枚举单例防反射 ===");
        try {
            Constructor<EnumSingleton> ec = EnumSingleton.class.getDeclaredConstructor(String.class, int.class);
            ec.setAccessible(true);
            ec.newInstance("HACK", 1);
            System.out.println("  意外：枚举被反射破坏");
        } catch (Exception e) {
            System.out.println("  防御生效：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println("  枚举实例 = " + EnumSingleton.INSTANCE.tag());

        System.out.println();
        System.out.println("=== 结论 ===");
        System.out.println("  普通类单例：反射/反序列化都能破坏，需额外防御");
        System.out.println("  枚举单例：JVM 层保证唯一，无需任何防御代码");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `DclSingleton` 的 `volatile` 去掉，说明在什么重排序下会读到"半成品对象"，画出三步指令顺序。
 * 2. 给 `DclSingleton` 实现 `readResolve()`，验证反序列化后仍是同一实例。
 * 3. 用"构造器内判断已创建则抛异常"的方式给 DCL 单例加反射防御，并说明它为什么挡不住反序列化。
 * ============================================================
 */
