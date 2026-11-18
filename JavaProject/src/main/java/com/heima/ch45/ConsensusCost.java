/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-10
 * 课名：共识的工程代价
 * ------------------------------------------------------------
 * 知识点：
 *   - **共识不便宜，先算代价再上**：每次写至少一次多数派 RTT（同机房 1~2ms，跨机房 30ms+）+ 多数派 fsync（磁盘毫秒级）。**etcd 单集群写 QPS 上千是健康极限**——共识集群是"协调者"不是"数据主库"。
 *   - **集群规模的收益递减**：3 节点容忍 1 故障，5 节点容忍 2——每加一台，写路径都要联系更多人，**延迟与故障概率同步上升**。生产 3 或 5 台是甜蜜点，7 台以上要有特殊理由。
 *   - **跨机房部署的取舍**：三个机房各一台（任一机房挂仍有过半）但写延迟 = 最慢机房 RTT；同机房三台（快）但机房故障全挂。**PC-EC 判断：机房级容灾 > 毫秒级延迟时才跨机房**，或用"两层架构"（本地快集群 + 异步跨机房镜像）。
 *   - **共识成员变更的危险性**：在线改集群成员（3->5）本身需要共识保证——配置错误瞬间可能没有过半（新旧配置各 3 台互不相认）。**joint consensus 两阶段变更**是安全做法；运维上更常用"停写 + 一次性替换"。
 *   - **日志增长与快照**：日志无限增长 -> 快照压缩（状态机快照 + 截断日志）-> 恢复可以走快照 + 追日志。**快照频率与大小**影响恢复时长，也是 43-05 checkpoint 思想的一致。
 *   - **监控与告警**（42 章方法论）：commit 耗时 P99、fsync 慢日志、换届频率、成员健康、磁盘水位——**共识集群挂了会拖垮依赖它的所有系统（K8s/配置/锁），必须最高优先级监控**。
 *   - 易错点：把业务数据写进共识集群（容量与吞吐双爆炸）；跨机房配置了共识却不接受写延迟（用户感知到"慢"）；在线成员变更无两阶段（分区分裂风险）；无快照（重启恢复要重放亿级日志）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ConsensusCost {
    static void latencyMath() {
        System.out.println("【写延迟的物理构成】");
        double rttSameRack = 0.3, rttCrossAz = 1.8, fsync = 1.2;
        double sameRoom = 2 * rttSameRack + fsync;      // leader->2 follower 并行 + 落盘
        double crossAz = 2 * rttCrossAz + fsync;
        System.out.printf("  同机房 3 节点：2×%.1fms(RTT) + %.1fms(fsync) ≈ %.1fms%n",
                rttSameRack, fsync, sameRoom);
        System.out.printf("  跨可用区：2×%.1fms + %.1fms ≈ %.1fms（慢 %.1f 倍）%n",
                rttCrossAz, fsync, crossAz, crossAz / sameRoom);
        System.out.println("  结论：共识集群放同机房，跨机房用异步镜像（45-11 案例）");
        System.out.println();
    }

    static void clusterSize() {
        System.out.println("【集群规模：容忍度与代价】");
        String[][] rows = {
                {"1", "0 故障", "无共识可言（单点）"},
                {"3", "1 故障", "最小生产配置，写联系 2 台"},
                {"5", "2 故障", "滚动维护更从容，写联系 3 台"},
                {"7", "3 故障", "写延迟与故障概率上升，需特殊理由"},
        };
        for (String[] r : rows) {
            System.out.printf("  %s 台：容忍 %s 故障，%s%n", r[0], r[1], r[2]);
        }
        System.out.println("  甜蜜点：3 或 5。规模不是越多越可靠——写路径人数是代价");
        System.out.println();
    }

    static void membershipChange() {
        System.out.println("【成员变更：危险的在线操作】");
        List<String> steps = List.of(
                "危险做法：直接把配置从 {n1,n2,n3} 改成 {n1,n4,n5}",
                "  -> 旧配置 3 台与新配置 3 台同时存在，任一侧都可能“过半”",
                "  -> 两个 leader 并存的窗口（脑裂）",
                "安全做法一：joint consensus 两阶段（先联合配置 C_old,new，再切 C_new）",
                "安全做法二（运维常用）：停写 -> 一次性替换成员 -> 验证 -> 恢复");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  K8s etcd 扩容的“官方姿势”就是保守路线：能停就停，不能停走 joint");
        System.out.println();
    }

    static void usageDiscipline() {
        System.out.println("【使用纪律：什么该进共识，什么不该】");
        List<String> dos = List.of(
                "该进：选主结果、锁/租约、配置元数据、集群成员表、调度决策",
                "不该进：业务订单数据（走主库+复制）、高频计数（走 Redis+对账）、");
        System.out.println("  " + dos.get(0));
        System.out.println("  " + dos.get(1));
        System.out.println("  大 value 处理：日志只存引用（对象存 OSS，元数据进共识）");
        System.out.println("  判断式：这个数据需要“全局唯一顺序”吗？不需要就不进共识");
    }

    public static void main(String[] args) {
        latencyMath();
        clusterSize();
        membershipChange();
        usageDiscipline();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用模拟器统计 3/5/7 节点在正常与单节点故障下的写延迟分布，量化"规模的代价"。
 * 2. 演示"直接改成员配置"的脑裂窗口：模拟新旧配置各 3 台各自选举，验证双主可能；再实现 joint consensus 两阶段。
 * 3. 思考题：为什么 etcd 官方推荐"专用节点"而不是与业务容器混部？从磁盘延迟、噪声邻居与换届的关系分析。
 * ============================================================
 */
