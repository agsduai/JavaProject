/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 22-01
 * 课名：依赖冲突调解器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现依赖树与冲突调解：依赖节点（groupId/artifactId/version/依赖列表）；实现最近优先（路径短的胜出）与最先声明优先（同深度按声明顺序）；输出解析后的有效版本树与"被忽略的依赖"清单；模拟 fastjson 2.4 与 2.9 经由不同路径汇聚的冲突场景。
 *   - 【考核点】依赖树解析、Maven 调解规则（最近优先/最先声明）、依赖排除（exclusion）、冲突的发现方法。
 *   - 【易错点】以为声明的版本一定生效（被更短路径覆盖）；不知道版本冲突的存在（无 dependency:tree 习惯）；升级依赖不查传递依赖。
 *   - 【提示】递归解析时记录路径深度，冲突时按"深度小者胜，同深度先声明胜"裁决并记录被淘汰者。
 * ============================================================
 */

package com.heima.practice.framework;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class DependencyMediator {
    static Map<String, String> resolved = new LinkedHashMap<>();

    static void declare(String artifact, String version, int depth) {
        String cur = resolved.get(artifact);
        if (cur == null) {
            resolved.put(artifact, version);
        } else {
            System.out.printf("  冲突：%s 已解析为 %s（更近路径），忽略 %s%n", artifact, cur, version);
        }
    }

    public static void main(String[] args) {
        declare("lib-a", "1.0", 1);
        declare("lib-a", "2.0", 3);
        System.out.println("  解析结果：" + resolved);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整调解器（递归树 + 两条规则 + 排除清单），构造三路冲突场景验证裁决顺序。
 * 2. 实现"依赖树打印"（缩进树形 + 深度标注），模拟 dependency:tree 的输出。
 * 3. 思考题：为什么"升级传递依赖"经常引发运行时 NoSuchMethodError？从"编译期版本 vs 运行期解析版本"的差异分析。
 * ============================================================
 */
