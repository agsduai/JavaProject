/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-04
 * 课名：制品管理与版本策略
 * ------------------------------------------------------------
 * 知识点：
 *   - **制品（artifact）是流水线的"合同标的物"**：源码不是交付物，制品才是。制品库（Maven Central、Nexus、镜像仓库）存储不可变制品与元数据（版本、构建号、提交哈希、签名、漏洞扫描结果）。**"部署哪个版本"必须是一个可以准确回答的问题**。
 *   - **版本号的语义（SemVer）**：`主.次.修订`（2.4.1）——主版本破坏兼容、次版本新增功能、修订只修 bug。加构建号与提交摘要形成完全可追溯版本：`2.4.1-142-a1b2c3d`。**" latest "不是版本**，是赌博。
 *   - **制品晋升（promotion）不是重新构建**：同一个制品从 dev -> test -> staging -> prod，变的只有"部署在哪个环境"，制品本身一个字节不变。第 37 章 `digest 固定` 的原则在制品层同样成立。
 *   - **不可变与防篡改**：已发布的制品禁止覆盖（`redeploy=false`）；只有 CI 机器账号有发布权限；制品签名 + 部署时验签。**同名同版本制品内容不同 = 供应链事故的温床**。
 *   - **依赖治理**：每次构建拉最新 SNAPSHOT 依赖 = 构建不可重现。规范：依赖一律用 release 版本锁定；`mvn dependency:tree` 定期审计；传递依赖的漏洞靠 SBOM（软件物料清单）追踪。
 *   - **保留策略**：制品库不是无限仓库——release 制品永久保留（回滚可能用到），SNAPSHOT 保留 30 天，过期清理。清理策略要写进运维文档，不然磁盘满在深夜。
 *   - **回滚就是重新部署旧制品**：制品库保留的历史版本 + 不可变保证，让"回滚"只是部署 v-1 的制品，而不是"找到旧源码重新构建"（旧源码可能已经不在了）。
 *   - 易错点：制品版本号用时间戳却没有提交摘要（无法对应源码）；本地 `mvn deploy` 手工发版（绕过 CI，制品来历不明）；同一版本号反复发布不同内容；把构建产物拷贝在跳板机目录当"制品库"（无签名无审计无保留策略）。
 * ============================================================
 */

package com.heima.ch41;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ArtifactRegistry {
    record Artifact(String version, String commit, String sha256, String stage) {
    }

    static Map<String, Artifact> registry = new LinkedHashMap<>();
    static final String KEY = "order-service";

    static void versionAnatomy() {
        System.out.println("【版本号的解剖：主.次.修订-构建-提交】");
        String v = "2.4.1-142-a1b2c3d";
        String[] p = v.split("-");
        System.out.printf("  %s%n", v);
        System.out.printf("  语义版本 %s（主=%s 次=%s 修订=%s）构建#%s 提交=%s%n",
                p[0], p[0].split("\\.")[0], p[0].split("\\.")[1], p[0].split("\\.")[2], p[1], p[2]);
        System.out.println("  反推：git checkout a1b2c3d + 构建#142 可复现该制品的构建过程");
        System.out.println("  latest 是指针不是版本：生产环境禁用");
        System.out.println();
    }

    static void promotion() {
        System.out.println("【制品晋升：同一制品，换个环境部署】");
        registry.put("2.4.1-142-a1b2c3d",
                new Artifact("2.4.1-142-a1b2c3d", "a1b2c3d", "sha256:9f2e...", "test"));
        Artifact a = registry.get("2.4.1-142-a1b2c3d");
        System.out.println("  test 环境验证通过：sha256=" + a.sha256());
        registry.put(a.version(), new Artifact(a.version(), a.commit(), a.sha256(), "staging"));
        System.out.println("  晋升 staging：版本不变、sha256 不变，只改 stage=" + a.version());
        registry.put(a.version(), new Artifact(a.version(), a.commit(), a.sha256(), "prod"));
        System.out.println("  部署 prod：部署脚本重新校验 sha256 -> 一致才放行");
        System.out.println("  对照组：重新构建 -> 依赖拉到新版 -> sha256 变了 -> 测试作废");
        System.out.println();
    }

    static void immutability() {
        System.out.println("【不可变与签名：同名同版本必须同内容】");
        record Upload(String version, String by, String result) {
        }
        List<Upload> uploads = List.of(
                new Upload("2.4.1-142", "ci-bot（流水线）", "接受：签名入库，redeploy=false"),
                new Upload("2.4.1-142", "developer-laptop", "拒绝：本地发版禁用，制品必须出自流水线"),
                new Upload("2.4.1-142", "ci-bot（重复上传）", "拒绝：已存在同名同版本，内容不可覆盖"));
        for (Upload u : uploads) {
            System.out.printf("  上传 %-12s by %-18s -> %s%n", u.version(), u.by(), u.result());
        }
        System.out.println("  部署前验签：签名不匹配 -> 拦截 + 告警（供应链防线）");
        System.out.println();
    }

    static void retention() {
        System.out.println("【保留策略与回滚的关系】");
        String[][] rows = {
                {"release 制品", "永久保留", "回滚 = 重新部署旧版本，依赖历史制品存在"},
                {"SNAPSHOT", "30 天", "开发期调试用，过期清理"},
                {"feature 分支产物", "7 天", "PR 验证制品，合入后无价值"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-8s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  清理是磁盘运维；保留是回滚保障——两条策略写进文档并自动化");
        System.out.println("  回滚演练：随机抽一个月前版本重新部署到 staging，验证仍可运行");
    }

    public static void main(String[] args) {
        versionAnatomy();
        promotion();
        immutability();
        retention();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的制品设计完整版本号方案（含构建号与提交摘要），实现一个"提交哈希 -> 制品版本"的反查脚本。
 * 2. 搭一个本地 Nexus/用目录模拟制品库，实现"上传签名、redeploy 拒绝、部署验签"三件事，演示覆盖上传被拦截。
 * 3. 思考题：为什么制品晋升必须禁止重新构建？从依赖漂移与审计追溯两个角度说明后果。
 * ============================================================
 */
