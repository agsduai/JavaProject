/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：22-01
 * 课名：为什么需要 Maven
 * ------------------------------------------------------------
 * 知识点：
 *   - 没有构建工具时，一个项目要手工下载 jar、手工拼 `-classpath`、手工 `javac`、手工打 jar；换台机器或换个同事，全部要重来一遍。
 *   - Maven 是 Apache 的项目管理与构建工具，用一份 `pom.xml`（Project Object Model）描述"项目是谁、依赖什么、怎么构建"。
 *   - Maven 的三大能力：**依赖管理**（自动下载 jar 及其传递依赖）、**标准化构建**（一套命令完成编译/测试/打包/安装）、**统一目录结构**（约定优于配置）。
 *   - **坐标**唯一标识一个构件：`groupId`（组织标识，通常域名反写）、`artifactId`（工程名）、`version`（版本号），合称 GAV。
 *   - 仓库分三级：本地仓库（默认 `~/.m2/repository`）→ 私服（公司内网 Nexus）→ 中央仓库。查找顺序是本地 → 私服 → 中央。
 *   - 易错：`groupId` 是组织标识不是 Java 包名，`artifactId` 才是工程名；同一个 GAV 在仓库里唯一，不存在"同版本不同内容"的两个 jar。
 * ============================================================
 */

package com.heima.ch22;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class MavenCoordinate {
    public static void main(String[] args) {
        String[] coords = {
                "org.springframework.boot:spring-boot-starter-web:3.3.2",
                "com.mysql:mysql-connector-j:8.4.0",
                "org.mybatis:mybatis:3.5.16",
                "com.heima:demo"    // 只写了两段，坐标不完整
        };
        List<String> valid = new ArrayList<>();
        for (String coord : coords) {
            String[] parts = coord.split(":");
            if (parts.length != 3) {
                System.out.println("[不合法] " + coord);
                System.out.println("         坐标必须由 groupId:artifactId:version 三段组成");
                System.out.println();
                continue;
            }
            String groupId = parts[0];
            String artifactId = parts[1];
            String version = parts[2];
            // 构件在仓库里的存放路径完全由坐标推导出来
            String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version
                    + "/" + artifactId + "-" + version + ".jar";
            System.out.println("groupId    = " + groupId);
            System.out.println("artifactId = " + artifactId);
            System.out.println("version    = " + version);
            System.out.println("仓库路径   = " + path);
            System.out.println();
            valid.add(artifactId);
        }
        System.out.println("合法坐标个数：" + valid.size() + " -> " + valid);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写出你打算做的毕业项目自己的 GAV 坐标，并推导出它在本地仓库中的完整路径。
 * 2. 解释为什么 Maven 必须用坐标而不能用"jar 包文件名"来管理依赖。
 * 3. 列出本地仓库、私服、中央仓库三者的查找顺序，并说明私服存在的两个意义。
 * ============================================================
 */
