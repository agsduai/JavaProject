/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-03
 * 课名：Spring Bean 生命周期与循环依赖
 * ------------------------------------------------------------
 * 知识点：
 *   - **Bean 的生命周期七步**：实例化（构造器）-> 属性填充（依赖注入）-> `Aware` 回调（BeanName/BeanFactory）-> `BeanPostProcessor.postProcessBeforeInitialization` -> 初始化（`@PostConstruct` -> `InitializingBean.afterPropertiesSet` -> `init-method`）-> `BeanPostProcessor.postProcessAfterInitialization`（**AOP 代理在这里生成**）-> 使用 -> 销毁（`@PreDestroy` -> `DisposableBean.destroy`）。
 *   - **循环依赖的三级缓存**：`singletonObjects`（成品）、`earlySingletonObjects`（半成品提前暴露）、`singletonFactories`（ObjectFactory 占位）——**A 依赖 B、B 依赖 A 时，A 先暴露"半成品引用"，B 拿到它完成创建，A 再完成**。**这就是 setter/字段注入能解决循环依赖、构造器注入不能的原因**（构造器阶段对象还没创建）。
 *   - **三级缓存存的是 ObjectFactory 而非对象**：为了让**提前暴露的引用可能被 AOP 代理替换**（若 A 需要代理，B 拿到的是代理而不是原始对象）——**这是"为什么需要第三级"的核心答案**（面试高频，也是 60-04 的伏笔）。
 *   - **`@Lazy` 是循环依赖的另一种解法**：注入代理占位，真正用时才解析——**但它把问题推迟到运行期**（首次调用时才可能报错），不如重构依赖关系。
 *   - **循环依赖的正确解法是"重构"**：抽公共依赖、改事件驱动（49-03）、上移职责——**三级缓存是框架的兜底，不是设计的目标**。
 *   - 易错点：用构造器注入又出现循环依赖（Spring 无法解决，直接报错）；`@Async`/AOP 代理与循环依赖叠加（拿到代理还是原始对象的差异）；把三级缓存当"性能优化"（它是兼容性兜底）；单例 Bean 里持有 prototype 依赖（60-02 的坑）。
 * ============================================================
 */

package com.heima.ch60;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

// ==================== 示例代码 ====================

public class BeanLifecycle {
    /** 三级缓存模拟 */
    static class Container {
        final Map<String, Object> singletonObjects = new HashMap<>();      // 成品
        final Map<String, Object> earlySingletonObjects = new HashMap<>(); // 半成品
        final Map<String, Supplier<Object>> singletonFactories = new HashMap<>(); // 工厂

        final Map<String, Supplier<Object>> creators = new LinkedHashMap<>();

        void register(String name, Supplier<Object> creator) {
            creators.put(name, creator);
        }

        Object getBean(String name) {
            Object ready = singletonObjects.get(name);
            if (ready != null) {
                return ready;
            }
            Object early = earlySingletonObjects.get(name);
            if (early != null) {
                System.out.println("    命中二级缓存（半成品）：" + name);
                return early;
            }
            Supplier<Object> factory = singletonFactories.get(name);
            if (factory != null) {
                Object o = factory.get();
                earlySingletonObjects.put(name, o);
                System.out.println("    命中三级缓存（工厂生成半成品）：" + name);
                return o;
            }
            return create(name);
        }

        Object create(String name) {
            System.out.println("    创建 " + name + "：实例化 -> 暴露半成品工厂 -> 属性填充");
            // 1) 实例化
            Object instance = new Object() {
                @Override
                public String toString() {
                    return name;
                }
            };
            // 2) 提前暴露（三级缓存）
            singletonFactories.put(name, () -> instance);
            // 3) 属性填充（可能触发依赖的创建）
            wiring(name);
            // 4) 初始化完成后移入一级缓存
            singletonObjects.put(name, instance);
            earlySingletonObjects.remove(name);
            singletonFactories.remove(name);
            System.out.println("    完成 " + name + "：移入一级缓存（成品）");
            return instance;
        }

        void wiring(String name) {
            if (name.equals("serviceA")) {
                System.out.println("      serviceA 依赖 serviceB -> 触发 getBean(serviceB)");
                getBean("serviceB");
            } else if (name.equals("serviceB")) {
                System.out.println("      serviceB 依赖 serviceA -> 触发 getBean(serviceA)");
                getBean("serviceA");
            }
        }
    }

    public static void main(String[] args) {
        var c = new Container();
        c.register("serviceA", Object::new);
        c.register("serviceB", Object::new);
        System.out.println("【循环依赖解析：A <-> B】");
        Object a = c.getBean("serviceA");
        System.out.println("  A 获取成功：" + a);
        System.out.println("  一级缓存键：" + c.singletonObjects.keySet());
        System.out.println();
        System.out.println("  关键：B 拿到的 A 是\"提前暴露的半成品\"，A 完成初始化后回收缓存");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展容器模拟：支持"构造器注入"并在循环依赖时抛异常，对比 setter 注入的差异，解释原因。
 * 2. 在真实 Spring 应用里用一个 `BeanPostProcessor` 打印所有 Bean 的创建顺序与初始化耗时，输出"启动耗时 Top5 Bean"。
 * 3. 思考题：为什么三级缓存中存的是 `ObjectFactory` 而不是对象？从"AOP 代理可能替换引用"的角度推导。
 * ============================================================
 */
