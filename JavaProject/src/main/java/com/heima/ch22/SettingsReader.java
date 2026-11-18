/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：22-02
 * 课名：安装与仓库配置
 * ------------------------------------------------------------
 * 知识点：
 *   - Maven 是绿色版：解压后把 `bin` 目录加入 `PATH`，用 `mvn -v` 验证，输出里会带上它使用的 JDK 版本。
 *   - 全局配置文件是 `conf/settings.xml`，用户级配置是 `~/.m2/settings.xml`，用户级优先级更高。
 *   - `localRepository` 指定本地仓库位置，默认在 `~/.m2/repository`；仓库体积会涨到几个 G，建议改到非系统盘。
 *   - `<mirrors>` 配置镜像，国内一般用阿里云 `https://maven.aliyun.com/repository/public`，否则从中央仓库拉包会非常慢。
 *   - IDEA 自带捆绑的 Maven，也可以在 `Settings → Build Tools → Maven` 里指定自己的安装目录和 settings 文件。
 *   - 易错：改完 `settings.xml` 后 IDEA 里要重新导入（Reload），否则用的还是旧配置。
 * ============================================================
 */

package com.heima.ch22;

// ==================== 示例代码 ====================

public class SettingsReader {
    public static void main(String[] args) {
        // 一个典型的 settings.xml 片段
        String xml = """
                <settings>
                    <localRepository>D:/maven-repo</localRepository>
                    <mirrors>
                        <mirror>
                            <id>aliyun</id>
                            <mirrorOf>central</mirrorOf>
                            <url>https://maven.aliyun.com/repository/public</url>
                        </mirror>
                    </mirrors>
                </settings>
                """;

        String repo = tag(xml, "localRepository");
        String mirrorOf = tag(xml, "mirrorOf");
        String url = tag(xml, "url");

        System.out.println("本地仓库：" + (repo.isEmpty() ? "未配置，使用默认 ~/.m2/repository" : repo));
        System.out.println("镜像覆盖：" + mirrorOf);
        System.out.println("镜像地址：" + url);

        // 本地仓库路径是否合法的简单校验
        boolean absolute = repo.matches("^[A-Za-z]:/.*") || repo.startsWith("/");
        System.out.println("仓库路径是绝对路径吗：" + absolute);
        if (!absolute) {
            System.out.println("警告：localRepository 建议写绝对路径，否则可能被解析到 Maven 安装目录下");
        }
    }

    // 极简标签取值：只取第一个同名标签之间的内容
    private static String tag(String xml, String name) {
        String open = "<" + name + ">";
        String close = "</" + name + ">";
        int start = xml.indexOf(open);
        int end = xml.indexOf(close, start);
        if (start < 0 || end < 0) {
            return "";
        }
        return xml.substring(start + open.length(), end).trim();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把本地仓库改到 `D:/maven-repo` 并配置阿里云镜像，然后执行 `mvn help:effective-settings` 检查配置是否生效。
 * 2. 说明 `settings.xml` 放在 Maven 安装目录和放在用户目录下有什么区别，团队协作时应该用哪一个。
 * 3. 如果不配置镜像，`mvn clean compile` 第一次执行会发生什么？给出两种排查思路。
 * ============================================================
 */
