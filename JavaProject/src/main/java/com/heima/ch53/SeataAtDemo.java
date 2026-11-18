/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-06
 * 课名：Seata 分布式事务
 * ------------------------------------------------------------
 * 知识点：
 *   - **Seata 的三种模式**：**AT**（自动补偿：一阶段本地提交+undo_log，二阶段异步删除或反向补偿——**业务无侵入但全局锁竞争**）、**TCC**（Try/Confirm/Cancel 手写三接口——高性能但侵入，51-09 的资金冻结场景）、**Saga**（长流程状态机编排）。**选型**：核心资金链路 TCC，一般业务 AT，超长流程 Saga（39-09 光谱的组件级印证）。
 *   - **AT 模式的全局锁**：TC（事务协调器）维护全局行锁——**同一行在全局事务提交前不能被其他全局事务修改**；这是 AT 隔离性的来源，也是吞吐瓶颈：**热点行（爆款库存）的 AT 会排队**——39 章热点方案在 AT 模式下依然必要。
 *   - **undo_log 的机制**：一阶段解析前后镜像存 undo_log（同本地事务），二阶段提交则异步删，回滚则按镜像反向补偿——**回滚失败（脏写）需要人工处理**（undo_log 校验不一致时告警）。
 *   - **Seata 的坑**：全局事务超时（默认 60s，长业务要调）；undo_log 表每个业务库都要建；@GlobalTransactional 与本地事务的边界（**方法内不要吞异常**，否则 TC 认为成功）；性能敏感接口的 AT 全局锁等待。
 *   - 易错点：所有跨服务事务都上 AT（全局锁拖垮核心链路）；undo_log 缺失（回滚时报表不存在）；TC 单点（生产要集群）；业务侧自行 commit 让 Seata 失明。
 * ============================================================
 */

package com.heima.ch53;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class SeataAtDemo {
    /** AT 两阶段：undo_log 反向补偿 */
    record UndoLog(String table, String pk, String before, String after) {
        String rollbackSql() {
            return "UPDATE " + table + " SET ... WHERE pk=" + pk + " （恢复 before 镜像: " + before + "）";
        }
    }

    static void twoPhase(boolean globalCommit) {
        List<UndoLog> logs = List.of(
                new UndoLog("stock", "sku=1001", "qty=50", "qty=49"),
                new UndoLog("account", "u=1001", "balance=10000", "balance=9400"));
        System.out.println("  一阶段：各本地事务提交 + undo_log 落库（全局锁已持有）");
        if (globalCommit) {
            System.out.println("  二阶段提交：异步删除 undo_log（业务数据已是最新）");
        } else {
            System.out.println("  二阶段回滚：按镜像反向补偿");
            for (UndoLog log : logs) {
                System.out.println("    " + log.rollbackSql());
            }
        }
        System.out.println();
    }

    public static void main(String[] args) {
        twoPhase(true);
        twoPhase(false);
        System.out.println("  全局锁提示：stock sku=1001 在全局事务期间对其他全局事务不可写");
        System.out.println("  热点行场景：AT 排队 -> 改用 TCC 预扣或 Redis（39 章方案）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 AT 两阶段模拟器（undo_log 生成/提交删除/回滚补偿 + 脏写检测），输出三场景轨迹。
 * 2. 对比 AT 与 TCC 在"爆款扣减"场景的吞吐（模拟全局锁排队 vs 预扣），量化差异。
 * 3. 思考题：为什么 AT 的全局锁让热点行吞吐骤降？从 TC 锁的粒度与全局事务持有时长分析，并给出三种缓解方案。
 * ============================================================
 */
