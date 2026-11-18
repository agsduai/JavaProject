/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：22-07
 * 课名：聚合与继承（多模块）
 * ------------------------------------------------------------
 * 知识点：
 *   - 聚合（`<modules>`）：父工程一次命令构建所有子模块，父工程 `packaging` 必须是 `pom`。
 *   - 继承（`<parent>`）：子模块继承父工程的 `properties`、`dependencies`、`dependencyManagement`、插件配置。
 *   - `<dependencyManagement>` 只声明版本不引入依赖，子模块要用时仍需写 `<dependency>` 但可以省略 `version`。
 *   - `<properties>` 里集中定义版本号，用 `${xxx.version}` 引用，升级时只改一处。
 *   - 子模块的 `<relativePath>` 指向父 `pom.xml`，默认是 `../pom.xml`。
 *   - 易错：父工程 `packaging` 写成 `jar` 会导致 `modules` 不生效；`dependencyManagement` 里写了依赖就以为已引入，结果子模块编译报"找不到符号"。
 * ============================================================
 */

package com.heima.ch22;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MultiModule {
    public static void main(String[] args) {
        // 父工程统一管理版本
        Map<String, String> managed = new LinkedHashMap<>();
        managed.put("mysql-connector-j", "8.4.0");
        managed.put("mybatis", "3.5.16");
        managed.put("junit-jupiter", "5.10.2");

        Module parent = new Module("heima-parent", "pom", null);
        parent.modules.add("heima-common");
        parent.modules.add("heima-service");
        parent.modules.add("heima-web");

        Module common = new Module("heima-common", "jar", "heima-parent");
        common.uses.add("junit-jupiter");

        Module service = new Module("heima-service", "jar", "heima-parent");
        service.uses.add("mybatis");
        service.uses.add("mysql-connector-j");

        Module web = new Module("heima-web", "jar", "heima-parent");
        web.uses.add("mybatis");

        List<Module> all = new ArrayList<>(List.of(parent, common, service, web));

        System.out.println("工程结构：");
        System.out.println(parent.name + " (packaging=pom)");
        for (Module m : all.subList(1, all.size())) {
            System.out.println("  +- " + m.name + " (parent=" + m.parent + ")");
            for (String dep : m.uses) {
                System.out.println("       " + dep + " : " + managed.get(dep) + "   <- 版本来自父工程");
            }
        }

        System.out.println();
        System.out.println("执行 mvn clean install（在父工程目录）：");
        for (Module m : all) {
            System.out.println("  " + m.name + " -> " + ("pom".equals(m.packaging) ? "聚合构建，无产物" : "构建 jar"));
        }

        System.out.println();
        System.out.println("若把父工程 packaging 误写成 jar：modules 会被忽略，只有父工程自己被构建。");
    }

    static class Module {
        String name;
        String packaging;
        String parent;
        List<String> modules = new ArrayList<>();
        List<String> uses = new ArrayList<>();

        Module(String name, String packaging, String parent) {
            this.name = name;
            this.packaging = packaging;
            this.parent = parent;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 建一个 `parent + common + web` 三模块工程，在父工程里用 `dependencyManagement` 锁定版本，子模块省略 `version` 后验证能编译。
 * 2. 说明聚合和继承分别解决什么问题，为什么实际项目里经常同一个 `pom.xml` 同时承担两者。
 * 3. 父工程 `packaging` 写错会有什么现象？给出排查步骤。
 * ============================================================
 */
