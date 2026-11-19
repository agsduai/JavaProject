/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-02
 * 课名：Spring IOC 容器：BeanDefinition 与实例化
 * ------------------------------------------------------------
 * 知识点：
 *   - **IOC 容器的心智模型只有三步**：**读配置 -> 建 BeanDefinition（元数据）-> 按需实例化**。`BeanDefinition` 记录"这个 Bean 的类、作用域、是否懒加载、构造参数、属性依赖、初始化方法"——**它不是 Bean 实例，是"造 Bean 的图纸"**。
 *   - **容器启动的主流程（`refresh` 方法）**：加载配置（XML/注解/JavaConfig）-> 注册 BeanDefinition -> 执行 `BeanFactoryPostProcessor`（可在实例化前改图纸）-> 注册 `BeanPostProcessor`（实例化前后回调）-> 实例化非懒加载单例 -> 发布容器就绪事件。
 *   - **`BeanFactory` vs `ApplicationContext`**：前者是"最小容器"（按需 `getBean` 才实例化）；后者是"完整容器"（继承 `BeanFactory` + 事件/国际化/资源加载 + **预实例化单例**）——**日常用的是 `ApplicationContext`，它的启动就把单例建好了**。
 *   - **BeanDefinition 的可编程修改**：`BeanFactoryPostProcessor` 在"图纸阶段"动手（改属性/换类/加依赖），`BeanPostProcessor` 在"实例阶段"动手（代理包装/属性填充）——**AOP 正是 BeanPostProcessor 实现的**（60-04）。
 *   - **作用域（Scope）**：`singleton`（容器内单例，默认）、`prototype`（每次 `getBean` 新建）、`request`/`session`（Web 环境）——**prototype Bean 的依赖注入到单例里会"注入一次不再更新"**（经典坑）。
 *   - 易错点：以为 `getBean` 每次都创建（单例只建一次）；混淆 BeanFactoryPostProcessor 与 BeanPostProcessor 的执行时机；prototype 注入单例的"失效"问题；同名 Bean 覆盖（默认允许，容易掩盖配置错误）。
 * ============================================================
 */

package com.heima.ch60;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class IocContainerDemo {
    /** BeanDefinition：图纸 */
    record BeanDefinition(String name, Class<?> type, String scope, boolean lazy) {
    }

    /** 极简容器：注册图纸 -> 按需实例化 -> 单例缓存 */
    static class MiniContainer {
        final Map<String, BeanDefinition> definitions = new LinkedHashMap<>();
        final Map<String, Object> singletons = new LinkedHashMap<>();

        void register(BeanDefinition def) {
            definitions.put(def.name(), def);
            System.out.printf("  [注册图纸] %s (%s, scope=%s)%n",
                    def.name(), def.type().getSimpleName(), def.scope());
        }

        /** 预实例化单例（对应 ApplicationContext 刷新末段） */
        void preInstantiate() throws Exception {
            for (BeanDefinition def : definitions.values()) {
                if (!def.lazy() && def.scope().equals("singleton")) {
                    getBean(def.name());
                }
            }
        }

        Object getBean(String name) throws Exception {
            BeanDefinition def = definitions.get(name);
            if (def == null) {
                throw new IllegalArgumentException("No bean named " + name);
            }
            if (def.scope().equals("singleton")) {
                Object cached = singletons.get(name);
                if (cached != null) {
                    return cached;
                }
                Object created = instantiate(def);
                singletons.put(name, created);
                System.out.printf("  [实例化] %s -> %s（单例，已缓存）%n",
                        name, created.getClass().getSimpleName());
                return created;
            }
            Object created = instantiate(def);
            System.out.printf("  [实例化] %s -> 新实例（prototype，不缓存）%n", name);
            return created;
        }

        Object instantiate(BeanDefinition def) throws Exception {
            Constructor<?> ctor = def.type().getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        }
    }

    static class OrderService {
        public OrderService() {
        }

        public String describe() {
            return "OrderService";
        }
    }

    static class RequestContext {
        public RequestContext() {
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("【容器启动：注册图纸 -> 预实例化单例】");
        var container = new MiniContainer();
        container.register(new BeanDefinition("orderService", OrderService.class, "singleton", false));
        container.register(new BeanDefinition("requestCtx", RequestContext.class, "prototype", true));
        container.register(new BeanDefinition("lazyBean", OrderService.class, "singleton", true));
        container.preInstantiate();

        System.out.println();
        System.out.println("【按需获取】");
        Object a = container.getBean("orderService");
        Object b = container.getBean("orderService");
        System.out.println("  两次 getBean 是同一实例：" + (a == b));
        Object c = container.getBean("requestCtx");
        Object d = container.getBean("requestCtx");
        System.out.println("  prototype 两次不同：" + (c != d));
        System.out.println("  懒加载 Bean 首次获取才创建：" + container.getBean("lazyBean").getClass().getSimpleName());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展 MiniContainer：支持构造器注入（按类型查找依赖）、懒加载、`BeanFactoryPostProcessor` 风格的"图纸后处理"。
 * 2. 用断点在 `AbstractApplicationContext.refresh()` 里走一遍，记录 12 个关键方法的顺序，与本章主流程对照。
 * 3. 思考题：为什么"prototype Bean 注入到 singleton"会失效？从"注入发生在单例创建时、只发生一次"的时机分析。
 * ============================================================
 */
