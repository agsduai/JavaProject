/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-02
 * 课名：B+ 树索引与页分裂
 * ------------------------------------------------------------
 * 知识点：
 *   - **B+ 树是"为磁盘页设计的平衡树"**：**内部节点只存键与指针**（扇出大）、**叶子节点存键与数据/主键**、**叶子间用链表相连**（范围扫描友好）——**3 层即可支撑千万级行**（扇出 1000 时：1000^3 = 10 亿）。
 *   - **聚簇索引 vs 二级索引**：**InnoDB 主键即聚簇索引**（叶子存整行）；**二级索引叶子存主键值**，查非索引列要**回表**（再走一次聚簇索引）——**回表是二级索引最大的成本**。
 *   - **页分裂**：**叶子页写满后再插入**，会把页**一分为二**（通常各半），**分裂导致空间碎片与写放大**——**顺序主键（自增）只在最右页分裂，随机主键（UUID）会到处分裂**。
 *   - **树高与 IO 次数**：**一次点查 = 树高次页访问**（根常在缓冲池，实际 1~2 次物理读）；**范围查 = 定位 + 叶子链表扫描**——**所以范围查询要控制返回量**。
 *   - 与 54 章的关系：54 章讲"推荐系统"，本章的 B+ 树是 34 章"数据结构"在磁盘上的工程版——**同样的平衡思想，不同的存储介质**。
 *   - 易错点：用 UUID 做主键（随机写、页分裂、索引膨胀）；`SELECT *` 导致回表；范围查询不带边界（扫全表）；以为"索引越多越快"（写放大 77-09）。
 * ============================================================
 */

package com.heima.ch77;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class BPlusTreeIndex {
    static final int LEAF_CAP = 8;

    static class LeafPage {
        final List<Integer> keys = new ArrayList<>();

        int maxKey() {
            return keys.isEmpty() ? Integer.MIN_VALUE : keys.get(keys.size() - 1);
        }
    }

    static class BPlusTree {
        final List<LeafPage> leaves = new ArrayList<>();
        int splits;
        long inserted;

        BPlusTree() {
            leaves.add(new LeafPage());
        }

        /** 按"页的最大键"定位叶子（真实实现走内部节点，这里用目录线性查找） */
        int locate(int key) {
            for (int i = 0; i < leaves.size(); i++) {
                if (key <= leaves.get(i).maxKey()) {
                    return i;
                }
            }
            return leaves.size() - 1;
        }

        void insert(int key) {
            int idx = locate(key);
            LeafPage page = leaves.get(idx);
            int pos = 0;
            while (pos < page.keys.size() && page.keys.get(pos) < key) {
                pos++;
            }
            if (pos < page.keys.size() && page.keys.get(pos) == key) {
                return;
            }
            page.keys.add(pos, key);
            inserted++;
            if (page.keys.size() > LEAF_CAP) {
                split(idx);
            }
        }

        /** 页分裂：中间一分为二，右页插入目录 */
        void split(int idx) {
            LeafPage page = leaves.get(idx);
            int mid = page.keys.size() / 2;
            LeafPage right = new LeafPage();
            right.keys.addAll(page.keys.subList(mid, page.keys.size()));
            page.keys.subList(mid, page.keys.size()).clear();
            leaves.add(idx + 1, right);
            splits++;
        }

        /** 树高：叶子数按内部扇出逐层收敛 */
        int height() {
            int pages = leaves.size();
            int h = 1;
            int fanout = LEAF_CAP / 2 + 1;
            while (pages > 1) {
                pages = (pages + fanout - 1) / fanout;
                h++;
            }
            return h;
        }

        boolean search(int key) {
            LeafPage page = leaves.get(locate(key));
            int lo = 0;
            int hi = page.keys.size() - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int v = page.keys.get(mid);
                if (v == key) {
                    return true;
                }
                if (v < key) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return false;
        }

        double avgFill() {
            int total = 0;
            for (LeafPage p : leaves) {
                total += p.keys.size();
            }
            return (double) total / leaves.size() / LEAF_CAP;
        }
    }

    public static void main(String[] args) {
        BPlusTree tree = new BPlusTree();
        for (int i = 1; i <= 2000; i++) {
            tree.insert(i);
        }

        System.out.println("【顺序插入 1..2000（页容量 8）】");
        System.out.printf("  叶子页数=%d，分裂次数=%d，树高=%d，平均填充率=%.0f%%%n",
                tree.leaves.size(), tree.splits, tree.height(), tree.avgFill() * 100);

        System.out.println();
        System.out.println("【点查】");
        System.out.println("  search(777) = " + tree.search(777));
        System.out.println("  search(3000) = " + tree.search(3000));
        System.out.println("  一次点查的页访问次数 = 树高 = " + tree.height());

        System.out.println();
        System.out.println("【二级索引回表成本】");
        long rows = 2000;
        long tablePages = (rows + 99) / 100;              // 每页 100 行
        System.out.println("  二级索引定位到主键：" + tree.height() + " 次页访问");
        System.out.println("  回表走聚簇索引：" + tree.height() + " 次页访问");
        System.out.println("  返回 10 行 -> 约 " + (tree.height() * 2 * 10) + " 次页访问（未命中缓存时）");
        System.out.println("  表数据页共 " + tablePages + " 页");

        System.out.println();
        System.out.println("【页分裂对比】");
        System.out.println("  自增主键：只在最右页分裂，碎片少、写放大低");
        System.out.println("  UUID 主键：随机插入，页分裂分散，索引膨胀 1.5~2 倍");
        System.out.println("  分裂后平均填充率约 50%~70%，这是 B+ 树的空间代价");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用"页容量 4/8/16"分别插入 1 万条，统计分裂次数、树高与平均填充率，说明页大小的影响。
 * 2. 实现"随机顺序插入"对比自增插入，统计分裂次数差异并解释 UUID 主键的代价。
 * 3. 思考题：为什么二级索引查询非索引列要"回表"？从叶子节点存主键而非整行分析，并给出避免回表的方法。
 * ============================================================
 */
