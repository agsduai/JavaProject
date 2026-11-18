/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-06
 * 课名：二叉搜索树、平衡与四种遍历
 * ------------------------------------------------------------
 * 知识点：
 *   - **二叉搜索树（BST）** 的性质：左子树所有节点 < 根 < 右子树所有节点。由此得到**中序遍历有序**这个最重要的推论，凡是"验证 BST"或"求第 k 小"都靠它。
 *   - BST 的查找/插入/删除都是 `O(h)`，`h` 是树高。**随机插入**时期望 `h ≈ log n`；**有序插入**时退化成链表，`h = n`，全部操作变 `O(n)`。这是 BST 最致命的缺陷。
 *   - **删除节点分三种情况**：叶子直接删；只有一个孩子用孩子顶替；有两个孩子找**右子树最小节点**（中序后继）或左子树最大节点顶替，再递归删除那个后继。
 *   - **四种遍历**：前序（根左右，用于复制/序列化）、中序（左根右，BST 有序输出）、后序（左右根，用于释放/自底向上计算）、层序（BFS，按层处理）。
 *   - **递归 vs 迭代**：递归写法短但深度受栈限制（退化树会 `StackOverflowError`）；迭代用显式栈，不受调用栈限制。生产代码里"用户数据可能有序"时要用迭代。
 *   - **平衡树**的意义：把 `h` 强制压在 `O(log n)`。AVL 严格平衡（左右高度差 ≤ 1，查询快、旋转多）；**红黑树**弱平衡（最长路径 ≤ 2×最短路径，插入删除旋转少），所以 JDK 的 `TreeMap`、`HashMap` 树化桶都用红黑树。
 *   - **旋转**是平衡的基本操作：左旋把右孩子提上来、右旋把左孩子提上来。旋转不改变中序序列，只改变形状。
 *   - **验证 BST 不能只比较父子**：`5` 的右子 `6` 的左子 `4` 是非法 BST，但 `4 < 6` 成立。正确做法是传上下界（`min, max`）或中序检查严格递增。
 *   - 易错：删除双子节点时忘了递归删后继；中序遍历用 `List` 收集导致 `O(n)` 额外空间（可用 Morris 遍历做到 `O(1)`）；把"层序"写成"前序"；退化树用递归导致栈溢出。
 * ============================================================
 */

package com.heima.ch34;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// ==================== 示例代码 ====================

public class BinarySearchTree {
    static final class Node {
        int val;
        Node left;
        Node right;

        Node(int val) {
            this.val = val;
        }
    }

    private Node root;
    private int size;

    int size() {
        return size;
    }

    boolean isEmpty() {
        return root == null;
    }

    /** 迭代插入，避免有序数据把递归栈打爆 */
    void insert(int val) {
        if (root == null) {
            root = new Node(val);
            size++;
            return;
        }
        Node cur = root;
        while (true) {
            if (val == cur.val) {
                return;
            }
            if (val < cur.val) {
                if (cur.left == null) {
                    cur.left = new Node(val);
                    size++;
                    return;
                }
                cur = cur.left;
            } else {
                if (cur.right == null) {
                    cur.right = new Node(val);
                    size++;
                    return;
                }
                cur = cur.right;
            }
        }
    }

    boolean contains(int val) {
        Node cur = root;
        while (cur != null) {
            if (val == cur.val) {
                return true;
            }
            cur = val < cur.val ? cur.left : cur.right;
        }
        return false;
    }

    void remove(int val) {
        root = remove(root, val);
    }

    private Node remove(Node node, int val) {
        if (node == null) {
            return null;
        }
        if (val < node.val) {
            node.left = remove(node.left, val);
        } else if (val > node.val) {
            node.right = remove(node.right, val);
        } else {
            size--;
            if (node.left == null) {
                return node.right;
            }
            if (node.right == null) {
                return node.left;
            }
            // 两个孩子：用右子树最小节点（中序后继）顶替
            Node successor = minNode(node.right);
            node.val = successor.val;
            size++;
            node.right = remove(node.right, successor.val);
        }
        return node;
    }

    private Node minNode(Node node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }

    int height() {
        return height(root);
    }

    private int height(Node node) {
        return node == null ? 0 : 1 + Math.max(height(node.left), height(node.right));
    }

    List<Integer> preorder() {
        List<Integer> out = new ArrayList<>();
        preorder(root, out);
        return out;
    }

    private void preorder(Node node, List<Integer> out) {
        if (node == null) {
            return;
        }
        out.add(node.val);
        preorder(node.left, out);
        preorder(node.right, out);
    }

    List<Integer> inorder() {
        List<Integer> out = new ArrayList<>();
        inorder(root, out);
        return out;
    }

    private void inorder(Node node, List<Integer> out) {
        if (node == null) {
            return;
        }
        inorder(node.left, out);
        out.add(node.val);
        inorder(node.right, out);
    }

    List<Integer> postorder() {
        List<Integer> out = new ArrayList<>();
        postorder(root, out);
        return out;
    }

    private void postorder(Node node, List<Integer> out) {
        if (node == null) {
            return;
        }
        postorder(node.left, out);
        postorder(node.right, out);
        out.add(node.val);
    }

    /** 层序遍历：按层输出，每层一行 */
    List<List<Integer>> levelOrder() {
        List<List<Integer>> levels = new ArrayList<>();
        if (root == null) {
            return levels;
        }
        Deque<Node> queue = new ArrayDeque<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            int n = queue.size();
            List<Integer> level = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Node node = queue.poll();
                level.add(node.val);
                if (node.left != null) {
                    queue.offer(node.left);
                }
                if (node.right != null) {
                    queue.offer(node.right);
                }
            }
            levels.add(level);
        }
        return levels;
    }

    /** 中序遍历迭代版：显式栈，不受递归深度限制 */
    List<Integer> inorderIterative() {
        List<Integer> out = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        Node cur = root;
        while (cur != null || !stack.isEmpty()) {
            while (cur != null) {
                stack.push(cur);
                cur = cur.left;
            }
            cur = stack.pop();
            out.add(cur.val);
            cur = cur.right;
        }
        return out;
    }

    /** 正确验证 BST：传上下界，而不是只比较父子 */
    boolean isValidBst() {
        return isValid(root, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private boolean isValid(Node node, long min, long max) {
        if (node == null) {
            return true;
        }
        if (node.val <= min || node.val >= max) {
            return false;
        }
        return isValid(node.left, min, node.val) && isValid(node.right, node.val, max);
    }

    int kthSmallest(int k) {
        Deque<Node> stack = new ArrayDeque<>();
        Node cur = root;
        int count = 0;
        while (cur != null || !stack.isEmpty()) {
            while (cur != null) {
                stack.push(cur);
                cur = cur.left;
            }
            cur = stack.pop();
            if (++count == k) {
                return cur.val;
            }
            cur = cur.right;
        }
        throw new IllegalArgumentException("k 超出节点数: " + k);
    }

    String draw() {
        StringBuilder sb = new StringBuilder();
        draw(root, "", "", sb);
        return sb.toString();
    }

    private void draw(Node node, String prefix, String childPrefix, StringBuilder sb) {
        if (node == null) {
            return;
        }
        sb.append(prefix).append(node.val).append('\n');
        if (node.left != null || node.right != null) {
            if (node.right != null) {
                draw(node.right, childPrefix + "├── R ", childPrefix + "│   ", sb);
            }
            if (node.left != null) {
                draw(node.left, childPrefix + "└── L ", childPrefix + "    ", sb);
            }
        }
    }

    /** 故意构造的非法 BST：5 -> 右 6 -> 左 4 */
    static Node invalidBst() {
        Node n5 = new Node(5);
        Node n6 = new Node(6);
        Node n4 = new Node(4);
        n5.right = n6;
        n6.left = n4;
        return n5;
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 插入与结构 ===");
        BinarySearchTree bst = new BinarySearchTree();
        for (int v : new int[]{50, 30, 70, 20, 40, 60, 80, 35, 45}) {
            bst.insert(v);
        }
        System.out.println("  size=" + bst.size() + " height=" + bst.height());
        System.out.print(bst.draw());

        System.out.println();
        System.out.println("=== 2. 四种遍历 ===");
        System.out.println("  前序(根左右): " + bst.preorder());
        System.out.println("  中序(左根右): " + bst.inorder() + "  <- BST 必须有序");
        System.out.println("  后序(左右根): " + bst.postorder());
        System.out.println("  层序: " + bst.levelOrder());
        System.out.println("  中序迭代版: " + bst.inorderIterative());

        System.out.println();
        System.out.println("=== 3. 查找与第 k 小 ===");
        System.out.println("  contains(40) = " + bst.contains(40));
        System.out.println("  contains(41) = " + bst.contains(41));
        System.out.println("  第 3 小 = " + bst.kthSmallest(3) + "（期望 35）");

        System.out.println();
        System.out.println("=== 4. 删除三种情况 ===");
        BinarySearchTree del = new BinarySearchTree();
        for (int v : new int[]{50, 30, 70, 20, 40, 60, 80}) {
            del.insert(v);
        }
        System.out.println("  原始中序: " + del.inorder());
        del.remove(20);
        System.out.println("  删叶子 20: " + del.inorder());
        del.remove(30);
        System.out.println("  删单子节点 30: " + del.inorder());
        del.remove(50);
        System.out.println("  删双子节点 50: " + del.inorder() + "（用右子树最小 60 顶替）");

        System.out.println();
        System.out.println("=== 5. 退化：有序插入变链表 ===");
        BinarySearchTree degenerate = new BinarySearchTree();
        for (int i = 1; i <= 10; i++) {
            degenerate.insert(i);
        }
        System.out.println("  顺序插入 1..10: height=" + degenerate.height()
                + "（等于 n，查询退化为 O(n)）");
        BinarySearchTree balanced = new BinarySearchTree();
        for (int v : new int[]{50, 30, 70, 20, 40, 60, 80, 10, 25, 35, 45}) {
            balanced.insert(v);
        }
        System.out.println("  较随机插入 11 个: height=" + balanced.height() + "（接近 log2(11)≈3.5）");

        System.out.println();
        System.out.println("=== 6. 验证 BST：父子比较是不够的 ===");
        BinarySearchTree invalid = new BinarySearchTree();
        invalid.root = invalidBst();
        System.out.println("  树结构 5 -> R6 -> L4");
        System.out.println("  isValidBst() = " + invalid.isValidBst()
                + "（4<6 成立，但 4 出现在 5 的右子树里，非法）");
        System.out.println("  正确做法：向下传递 (min, max) 区间。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `BinarySearchTree` 增加 `floor(int x)` 和 `ceil(int x)`，分别返回小于等于 / 大于等于 x 的最大 / 最小值，要求 `O(h)`。
 * 2. 实现 AVL 树的插入与四种旋转（LL/RR/LR/RL），对 1..1000 顺序插入后打印树高，与普通 BST 对比。
 * 3. 用 Morris 中序遍历把空间降到 `O(1)`，验证输出与递归版一致（提示：利用叶子节点的空 right 指针临时指向后继）。
 * 4. 实现"把有序数组转成高度平衡的 BST"（`sortedArrayToBst`），并验证生成树的高度为 `⌈log2(n+1)⌉`。
 * ============================================================
 */
