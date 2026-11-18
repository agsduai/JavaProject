/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：55-06
 * 课名：租户配置分层
 * ------------------------------------------------------------
 * 知识点：
 *   - **SaaS 配置的四层叠加**：平台默认 -> 租户级覆盖 -> 用户级覆盖 -> 请求级参数——**优先级从低到高**（40-03 配置优先级的 SaaS 版）；租户级配置是 SaaS 的"定制化"产品能力（logo/主题/业务参数/功能开关）。
 *   - **租户配置的存储与分发**：配置表（tenant_id+key+value）或租户独立配置块；**热更新**（53-03 长轮询/推送，租户维度更细）；**配置变更审计**（谁改的租户配置，51-10 审计）。
 *   - **功能开关（feature flag）的租户维度**：同一功能按租户灰度（54-08 分流思想：按租户而非用户）——**SaaS 的功能发布天然按租户灰度**（大客户先上）。
 *   - **配置校验与回滚**：租户配置错误只影响该租户（爆炸半径天然小）——但**校验仍必须**（46-08 契约：类型/范围/依赖）；**租户级回滚**（配置历史版本）。
 *   - 易错点：租户配置无校验（一个租户的错配置拖垮共享应用——噪声邻居的配置版）；默认值硬编码散落（应集中声明）；配置缓存无租户键（55-01 切面清单）。
 *   - **与噪声邻居的联动**：租户配置里常含"资源类配置"（批处理并发/导出行数上限）——**大租户的激进配置要受平台上限约束**（55-08）。
 * ============================================================
 */

package com.heima.ch55;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class TenantConfig {
    static final Map<String, String> PLATFORM_DEFAULT = Map.of(
            "page.size", "20", "export.rows", "10000", "theme", "light");
    static final Map<String, Map<String, String>> tenantOverride = new HashMap<>();

    static String get(String tenant, String key) {
        Map<String, String> ov = tenantOverride.get(tenant);
        if (ov != null && ov.containsKey(key)) {
            return ov.get(key);            // 租户覆盖
        }
        return PLATFORM_DEFAULT.get(key);  // 平台默认
    }

    public static void main(String[] args) {
        tenantOverride.put("t-big", new LinkedHashMap<>(Map.of("page.size", "100")));
        System.out.println("  小租户 page.size = " + get("t-small", "page.size"));
        System.out.println("  大租户 page.size = " + get("t-big", "page.size") + "（租户覆盖）");
        System.out.println();
        System.out.println("  上限约束：大租户 page.size 平台硬上限 100（55-08 噪声邻居防护）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现四层配置叠加（平台/租户/用户/请求级），写优先级测试用例。
 * 2. 实现租户配置的校验器（类型/范围/平台上限）与变更审计，演示一次坏配置被拦截。
 * 3. 思考题：为什么租户级配置错误"只影响该租户"但不等于"可以不校验"？从共享应用资源与噪声邻居角度分析。
 * ============================================================
 */
