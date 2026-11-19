/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-01
 * 课名：为什么读源码：方法与路线
 * ------------------------------------------------------------
 * 知识点：
 *   - **读源码的目的不是"背实现"，是获得三种能力**：① **排障能力**（框架报错时知道去哪个类、哪个方法、哪个配置项）；② **正确使用能力**（知道框架的边界与代价，避免误用）；③ **设计借鉴能力**（把框架的结构搬到自己的代码里——49/31 章的模式在真实项目中的样本）。
 *   - **读源码的正确姿势：从"问题"出发，不从"第一行"出发**——带着具体问题（"@Transactional 为什么没生效"）沿调用链定位，比从 `main` 往下读高效十倍。**通读框架源码是少数人的选择，按需精读是所有人的能力**。
 *   - **三条切入路径**：① **入口法**（找框架的启动入口：`SpringApplication.run`、`ServerBootstrap.bind`，顺主流程走）；② **断点法**（IDE 里在关键 API 下断点，看调用栈）；③ **文档法**（先读官方"生命周期/扩展点"文档建立地图，再看代码填充）。
 *   - **精读的产出必须落地**：一张调用链图、一段验证代码、一条团队文档（"这个坑以后注意"）——**只读不产出等于没读**（52 章输出倒逼输入）。
 *   - **本章选读标准**：Spring（IOC/AOP/事务）、Netty（线程模型/ByteBuf）、Tomcat（连接器/容器）、MyBatis（SqlSession/执行器）、SLF4J（桥接/异步）——**覆盖"业务开发最高频依赖"的核心机制**。
 *   - 易错点：一开始就通读（迷路放弃）；只读博客不读源码（二手信息过期）；读但不验证（理解错了不自知）；读完不沉淀（下次重新读）。
 * ============================================================
 */

package com.heima.ch60;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ReadSourceMethod {
    record Approach(String name, String how, String fit) {
    }

    static void approaches() {
        System.out.println("【三条切入路径】");
        List<Approach> list = List.of(
                new Approach("入口法", "从启动入口顺主流程：SpringApplication.run -> refresh", "建立全局地图"),
                new Approach("断点法", "关键 API 下断点，观察调用栈与参数", "定位具体行为"),
                new Approach("文档法", "先读官方生命周期/扩展点文档，再读代码", "避免迷路"));
        for (Approach a : list) {
            System.out.printf("  %-6s %-46s %s%n", a.name(), a.how(), a.fit());
        }
        System.out.println();
    }

    static void questionDriven() {
        System.out.println("【问题驱动的精读示例】");
        Map<String, String> questions = new LinkedHashMap<>();
        questions.put("@Transactional 不生效", "查代理创建时机：同类自调用不走代理（60-05）");
        questions.put("Bean 初始化顺序不对", "查 Bean 生命周期：构造 -> 属性注入 -> 初始化回调（60-03）");
        questions.put("Netty 内存泄漏", "查 ByteBuf 引用计数与释放路径（60-07）");
        questions.put("日志打不出来", "查 SLF4J 绑定与多实现冲突（60-10）");
        questions.forEach((q, path) -> System.out.printf("  问题：%-22s -> %s%n", q, path));
        System.out.println();
        System.out.println("  每个问题都对应一条具体的类/方法 —— 这才是可执行的读源码计划");
    }

    public static void main(String[] args) {
        approaches();
        questionDriven();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 列出你项目里遇到过的 5 个"框架行为不符合预期"的问题，为每个写出"该读哪个类/方法"的具体清单。
 * 2. 选一个你天天用的框架，用入口法画出它的启动主流程（10 个步骤以内），标注你能解释的与解释不了的部分。
 * 3. 思考题：为什么"问题驱动"比"通读"更适合绝大多数工程师？从学习效率与知识留存率两个角度分析。
 * ============================================================
 */
