/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-08
 * 课名：隐私计算入门：联邦学习概念
 * ------------------------------------------------------------
 * 知识点：
 *   - **联邦学习（Federated Learning）的核心思想：数据不动，模型动**——各方在本地用自己的数据训练，**只交换模型参数（梯度/权重）**，由聚合方合成全局模型——**原始数据永不离开本地**。
 *   - **横向联邦 vs 纵向联邦**：**横向**（各方样本不同、特征相同——如多家医院同一套检查项）、**纵向**（各方样本相同、特征不同——如银行+电商同一批用户的不同维度）；**联合建模**需要样本对齐（隐私求交 PSI）。
 *   - **FedAvg 聚合算法**：各方用本地数据训练若干轮 -> 上传参数 -> 服务端按样本量加权平均 -> 下发新参数 -> 重复——**简单有效，是联邦学习的基线**。
 *   - **联邦学习不是万能药**：**梯度也可能泄露信息**（梯度反演攻击可重建训练样本）——需要**安全聚合（Secure Aggregation，加密后聚合）**、**差分隐私加噪**（68-07）、**可信执行环境**；**通信成本高**（多轮往返）；**非独立同分布数据**（Non-IID）导致收敛慢。
 *   - **与 68-06 的关系**：跨境场景下"数据不出境、模型可共享"是合规路径之一——**但要注意：模型也可能记住数据（成员推断攻击）**。
 *   - 易错点：以为"只传梯度就绝对安全"（梯度反演可还原样本）；忽略 Non-IID（各方数据分布差异大，全局模型效果差）；通信轮次无上限（成本失控）；不做安全聚合（明文梯度可被截获）。
 * ============================================================
 */

package com.heima.ch68;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FederatedLearning {
    /** 简化模型：一个线性参数（y = w * x），演示聚合语义 */
    record Client(String id, int samples, double w) {
    }

    /** FedAvg：按样本量加权平均 */
    static double fedAvg(List<Client> clients) {
        double total = 0;
        long samples = 0;
        for (Client c : clients) {
            total += c.w() * c.samples();
            samples += c.samples();
        }
        return samples == 0 ? 0 : total / samples;
    }

    /** 本地训练一步：用本地数据往真值方向走一小步 */
    static double localStep(double w, double target, double lr) {
        return w + lr * (target - w);
    }

    /** 联邦训练模拟：多轮 本地训练 -> 加权聚合 -> 下发 */
    static void federatedTraining(List<Client> clients, double globalTarget, int rounds,
                                  double lr) {
        List<Double> weights = new ArrayList<>();
        clients.forEach(c -> weights.add(0.0));
        double globalW = 0.0;
        for (int r = 1; r <= rounds; r++) {
            List<Client> updated = new ArrayList<>();
            for (int i = 0; i < clients.size(); i++) {
                Client c = clients.get(i);
                // 每方本地数据略有偏差（Non-IID）
                double localTarget = globalTarget + (i - 1) * 0.5;
                double localW = localStep(globalW, localTarget, lr);
                updated.add(new Client(c.id(), c.samples(), localW));
            }
            globalW = fedAvg(updated);
            System.out.printf("  第%d轮：各方本地参数=%s -> 聚合 globalW=%.3f%n", r,
                    updated.stream().map(c -> String.format("%.2f", c.w())).toList(), globalW);
        }
    }

    static void security() {
        System.out.println("【联邦学习的安全增强】");
        Map<String, String> measures = new LinkedHashMap<>();
        measures.put("安全聚合", "各方梯度加密后由聚合方求和（看不到明文梯度）");
        measures.put("差分隐私", "梯度上加噪（68-07），防梯度反演");
        measures.put("TEE", "聚合在可信执行环境中完成");
        measures.put("成员推断防护", "限制输出置信度/加噪，防判断某样本是否在训练集");
        measures.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
        System.out.println();
    }

    static void limits() {
        System.out.println("【适用边界】");
        String[][] rows = {
                {"数据不能出域", "多医院联合建模、跨境合规", "联邦是对的选择"},
                {"数据量小、通信便宜", "单机房少量参与方", "集中式更简单高效"},
                {"极度敏感", "金融风控、医疗", "需联邦 + 安全聚合 + DP + 审计"},
                {"要求极致精度", "离线分析", "联邦的噪声与非 IID 会损失精度"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-16s %-22s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) {
        System.out.println("【横向联邦：3 家医院同一检查项】");
        List<Client> clients = List.of(
                new Client("医院A", 500, 0),
                new Client("医院B", 300, 0),
                new Client("医院C", 200, 0));
        System.out.println("  样本量：" + clients.stream().map(c -> c.id() + "=" + c.samples()).toList());
        System.out.println("  模拟目标参数 = 2.0");
        federatedTraining(clients, 2.0, 5, 0.5);
        System.out.println("  注意：样本量大的参与方对全局模型影响更大（加权聚合）");

        System.out.println();
        security();
        limits();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 FedAvg 训练模拟：3 个参与方（不同样本量、不同分布），观察 10 轮的收敛曲线与最终参数。
 * 2. 构造 Non-IID 场景（各参与方目标参数差异很大），观察全局模型的次优性并说明缓解手段。
 * 3. 思考题：为什么"只传梯度"仍可能泄露数据？用"梯度反演可重建图像/样本"的方向说明，并给出防护组合。
 * ============================================================
 */
