### 34-05 哈希表原理与冲突处理

**知识点**
- 哈希表用"**数组 + 哈希函数 + 冲突解决**"把查找从 `O(n)` 降到平均 `O(1)`。核心是**把 key 映射到一个下标**，然后直接访问那个槽位。
- **好的哈希函数**要求：计算快、分布均匀、对输入微小变化敏感。`hashCode()` 返回 int，还要经过扰动（`h ^ (h >>> 16)`）再取模，否则低位相同的 key 会全部撞在一起。
- **取模用 `(n - 1) & hash` 而不是 `%`**：前提是容量为 2 的幂。位运算比取模快，且 `n-1` 的二进制全 1 能保留 hash 的低位。这是 JDK `HashMap` 容量必须是 2 的幂的原因。
- **冲突解决两大流派**：**链地址法**（同槽位挂链表/红黑树，JDK 用法）和**开放寻址法**（线性探测/二次探测/双重哈希，冲突了就往后找空位）。链地址法删除简单、装载因子可以超过 1；开放寻址法缓存友好但删除要打"墓碑"标记。
- **装载因子（load factor）** = 元素数 / 桶数。超过阈值（JDK 默认 0.75）就扩容。0.75 是"空间 vs 冲突"的经验平衡点：太高冲突多查询慢，太低浪费内存。
- **JDK 8 的树化**：单桶链表长度 ≥ 8 且总容量 ≥ 64 时，链表转红黑树，最坏查询从 `O(n)` 降到 `O(log n)`。长度降到 6 再退回链表。
- **扩容（resize）**要重新分配所有元素。JDK 8 利用"容量是 2 的幂"这个性质，把每个元素分成"低位链"和"高位链"两条，不需要重新计算 hash，只判断 `(hash & oldCap) == 0`。
- **`equals` 与 `hashCode` 的契约**：`equals` 相等则 `hashCode` 必须相等；`hashCode` 相等 `equals` 不一定相等。重写 `equals` 必须重写 `hashCode`，否则放进 `HashSet` 就找不回来了。
- **可变对象做 key 是灾难**：对象进桶后修改了参与 `hashCode` 的字段，就再也定位不到。key 要用不可变类型（`String`、`Integer`、`record`）。
- 易错：`hashCode` 用可变字段；只重写 `equals`；用 `%` 取模但容量不是 2 的幂；以为 `HashMap` 线程安全（并发扩容会丢数据甚至死循环，JDK 8 已修死循环但仍不安全）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HashTableDemo {
    /** 链地址法哈希表：容量 2 的幂 + 扰动 + 树化阈值统计 */
    static final class ChainedMap<K, V> {
        private static final int DEFAULT_CAP = 16;
        private static final float LOAD_FACTOR = 0.75f;
        private static final int TREEIFY_THRESHOLD = 8;

        static final class Entry<K, V> {
            final int hash;
            final K key;
            V value;
            Entry<K, V> next;

            Entry(int hash, K key, V value, Entry<K, V> next) {
                this.hash = hash;
                this.key = key;
                this.value = value;
                this.next = next;
            }
        }

        private Entry<K, V>[] table;
        private int size;
        private int threshold;
        private int resizeCount;
        private int maxChain;

        @SuppressWarnings({"unchecked", "rawtypes"})
        ChainedMap() {
            table = (Entry<K, V>[]) new Entry[DEFAULT_CAP];
            threshold = (int) (DEFAULT_CAP * LOAD_FACTOR);
        }

        static int spread(Object key) {
            int h;
            return key == null ? 0 : (h = key.hashCode()) ^ (h >>> 16);
        }

        int capacity() {
            return table.length;
        }

        int size() {
            return size;
        }

        int resizeCount() {
            return resizeCount;
        }

        int maxChain() {
            return maxChain;
        }

        V put(K key, V value) {
            int hash = spread(key);
            int index = (table.length - 1) & hash;
            for (Entry<K, V> e = table[index]; e != null; e = e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    V old = e.value;
                    e.value = value;
                    return old;
                }
            }
            table[index] = new Entry<>(hash, key, value, table[index]);
            size++;
            int chain = chainLength(index);
            if (chain > maxChain) {
                maxChain = chain;
            }
            if (chain >= TREEIFY_THRESHOLD) {
                System.out.println("    [提示] 桶 " + index + " 链表长度=" + chain
                        + "，真实 HashMap 在此会树化");
            }
            if (size > threshold) {
                resize();
            }
            return null;
        }

        V get(K key) {
            int hash = spread(key);
            int index = (table.length - 1) & hash;
            for (Entry<K, V> e = table[index]; e != null; e = e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    return e.value;
                }
            }
            return null;
        }

        V remove(K key) {
            int hash = spread(key);
            int index = (table.length - 1) & hash;
            Entry<K, V> prev = null;
            for (Entry<K, V> e = table[index]; e != null; prev = e, e = e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    if (prev == null) {
                        table[index] = e.next;
                    } else {
                        prev.next = e.next;
                    }
                    size--;
                    return e.value;
                }
            }
            return null;
        }

        private int chainLength(int index) {
            int n = 0;
            for (Entry<K, V> e = table[index]; e != null; e = e.next) {
                n++;
            }
            return n;
        }

        /** JDK 8 式扩容：按 (hash & oldCap) 拆成低位链和高位链，无需重算 hash */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private void resize() {
            Entry<K, V>[] oldTable = table;
            int oldCap = oldTable.length;
            int newCap = oldCap << 1;
            Entry<K, V>[] newTable = (Entry<K, V>[]) new Entry[newCap];
            for (int i = 0; i < oldCap; i++) {
                Entry<K, V> loHead = null;
                Entry<K, V> loTail = null;
                Entry<K, V> hiHead = null;
                Entry<K, V> hiTail = null;
                for (Entry<K, V> e = oldTable[i]; e != null; e = e.next) {
                    if ((e.hash & oldCap) == 0) {
                        if (loTail == null) {
                            loHead = e;
                        } else {
                            loTail.next = e;
                        }
                        loTail = e;
                    } else {
                        if (hiTail == null) {
                            hiHead = e;
                        } else {
                            hiTail.next = e;
                        }
                        hiTail = e;
                    }
                }
                if (loTail != null) {
                    loTail.next = null;
                    newTable[i] = loHead;
                }
                if (hiTail != null) {
                    hiTail.next = null;
                    newTable[i + oldCap] = hiHead;
                }
            }
            table = newTable;
            threshold = (int) (newCap * LOAD_FACTOR);
            resizeCount++;
        }

        String buckets() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < table.length; i++) {
                int len = chainLength(i);
                if (len > 0) {
                    sb.append("    [" ).append(i).append("] ");
                    for (Entry<K, V> e = table[i]; e != null; e = e.next) {
                        sb.append(e.key).append("=").append(e.value).append(" -> ");
                    }
                    sb.append("null\n");
                }
            }
            return sb.toString();
        }
    }

    /** 开放寻址法（线性探测），演示删除必须打墓碑 */
    static final class OpenAddressMap {
        private static final Object TOMBSTONE = new Object();
        private Object[] keys;
        private Object[] values;
        private int size;
        private int probes;

        OpenAddressMap(int capacity) {
            keys = new Object[capacity];
            values = new Object[capacity];
        }

        int probes() {
            return probes;
        }

        void put(Object key, Object value) {
            int cap = keys.length;
            int index = Math.floorMod(key.hashCode(), cap);
            int firstTombstone = -1;
            for (int step = 0; step < cap; step++) {
                probes++;
                int i = (index + step) % cap;
                if (keys[i] == TOMBSTONE) {
                    if (firstTombstone < 0) {
                        firstTombstone = i;
                    }
                } else if (keys[i] == null) {
                    int target = firstTombstone >= 0 ? firstTombstone : i;
                    keys[target] = key;
                    values[target] = value;
                    size++;
                    return;
                } else if (keys[i].equals(key)) {
                    values[i] = value;
                    return;
                }
            }
            if (firstTombstone >= 0) {
                keys[firstTombstone] = key;
                values[firstTombstone] = value;
                size++;
                return;
            }
            throw new IllegalStateException("表已满");
        }

        Object get(Object key) {
            int cap = keys.length;
            int index = Math.floorMod(key.hashCode(), cap);
            for (int step = 0; step < cap; step++) {
                probes++;
                int i = (index + step) % cap;
                if (keys[i] == null) {
                    return null;
                }
                if (keys[i] != TOMBSTONE && keys[i].equals(key)) {
                    return values[i];
                }
            }
            return null;
        }

        boolean remove(Object key) {
            int cap = keys.length;
            int index = Math.floorMod(key.hashCode(), cap);
            for (int step = 0; step < cap; step++) {
                probes++;
                int i = (index + step) % cap;
                if (keys[i] == null) {
                    return false;
                }
                if (keys[i] != TOMBSTONE && keys[i].equals(key)) {
                    keys[i] = TOMBSTONE;
                    values[i] = null;
                    size--;
                    return true;
                }
            }
            return false;
        }
    }

    /** 故意写坏的 key：hashCode 恒定，全部落一个桶 */
    static final class BadKey {
        final String name;

        BadKey(String name) {
            this.name = name;
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BadKey && ((BadKey) o).name.equals(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** 参与 hashCode 的可变字段 —— 反面教材 */
    static final class MutableKey {
        String id;

        MutableKey(String id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MutableKey && ((MutableKey) o).id.equals(id);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 基本操作与桶分布 ===");
        ChainedMap<String, Integer> map = new ChainedMap<>();
        String[] words = {"apple", "banana", "cherry", "date", "elderberry", "fig"};
        for (int i = 0; i < words.length; i++) {
            map.put(words[i], i);
        }
        System.out.println("  size=" + map.size() + " capacity=" + map.capacity());
        System.out.println("  get(\"cherry\") = " + map.get("cherry"));
        System.out.println("  remove(\"banana\") = " + map.remove("banana"));
        System.out.println("  get(\"banana\") 移除后 = " + map.get("banana"));
        System.out.print(map.buckets());

        System.out.println();
        System.out.println("=== 2. 扩容：16 -> 32，元素被拆到 i 和 i+16 两个桶 ===");
        for (int i = 0; i < 30; i++) {
            map.put("key-" + i, i);
        }
        System.out.println("  插入 36 个后 capacity=" + map.capacity()
                + " resizeCount=" + map.resizeCount() + " size=" + map.size());
        System.out.println("  扩容不需要重算 hash：只看 (hash & oldCap) 是 0 还是 1。");

        System.out.println();
        System.out.println("=== 3. 哈希函数质量：好 key vs 坏 key ===");
        ChainedMap<Integer, String> good = new ChainedMap<>();
        for (int i = 0; i < 32; i++) {
            good.put(i, "v" + i);
        }
        System.out.println("  连续整数 key: maxChain=" + good.maxChain() + "（分布均匀）");

        ChainedMap<BadKey, String> bad = new ChainedMap<>();
        for (int i = 0; i < 8; i++) {
            bad.put(new BadKey("k" + i), "v" + i);
        }
        System.out.println("  恒定 hashCode 的 key: maxChain=" + bad.maxChain()
                + "（退化成链表，查询 O(n)）");

        System.out.println();
        System.out.println("=== 4. 开放寻址法：冲突后线性探测 ===");
        OpenAddressMap oa = new OpenAddressMap(8);
        // 构造 3 个必然落在同一槽位的 key
        for (int i = 0; i < 3; i++) {
            oa.put(new Key8(i * 8), "v" + i);
        }
        System.out.println("  3 个同槽 key 写入后探测次数=" + oa.probes());
        oa.put(new Key8(0), "updated");
        System.out.println("  更新 key=0 后 get(0) = " + oa.get(new Key8(0)));
        oa.remove(new Key8(8));
        System.out.println("  删除 key=8 后 get(8) = " + oa.get(new Key8(8)) + "（墓碑生效）");
        System.out.println("  get(16) = " + oa.get(new Key8(16)) + "（删除留下的墓碑不阻断探测链）");

        System.out.println();
        System.out.println("=== 5. 可变 key 的反面教材 ===");
        ChainedMap<MutableKey, String> broken = new ChainedMap<>();
        MutableKey k = new MutableKey("order-1");
        broken.put(k, "订单详情");
        System.out.println("  放入时 get = " + broken.get(new MutableKey("order-1")));
        k.id = "order-2";
        System.out.println("  改了参与 hashCode 的字段后 get(new MutableKey(\"order-1\")) = "
                + broken.get(new MutableKey("order-1")) + "（找不回来了）");
        System.out.println("  结论：key 必须不可变，否则哈希值变了就定位不到原桶。");

        System.out.println();
        System.out.println("=== 6. 装载因子对冲突的影响 ===");
        for (float lf : new float[]{0.5f, 0.75f, 0.9f}) {
            int cap = 1024;
            int limit = (int) (cap * lf);
            List<List<Integer>> buckets = new ArrayList<>();
            for (int i = 0; i < cap; i++) {
                buckets.add(new ArrayList<>());
            }
            for (int i = 0; i < limit; i++) {
                int h = i * 0x9E3779B9;
                h ^= h >>> 16;
                buckets.get((cap - 1) & h).add(i);
            }
            int max = 0;
            int empty = 0;
            for (List<Integer> b : buckets) {
                max = Math.max(max, b.size());
                if (b.isEmpty()) {
                    empty++;
                }
            }
            System.out.printf("  装载因子 %.2f：最长桶=%d 空桶=%d/%d%n", lf, max, empty, cap);
        }
    }

    /** 固定落点的 key，方便演示线性探测 */
    static final class Key8 {
        final int v;

        Key8(int v) {
            this.v = v;
        }

        @Override
        public int hashCode() {
            return v;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key8 && ((Key8) o).v == v;
        }

        @Override
        public String toString() {
            return "K" + v;
        }
    }
}
```

**课后练习**
1. 给 `ChainedMap` 实现真正的树化：当单桶链表长度 ≥ 8 时，把该桶转成你手写的红黑树（或至少是 `TreeMap` 逻辑），并测量 10 万个"恶意同 hash" key 的查询耗时变化。
2. 对比链地址法与线性探测法在装载因子 0.9 时的平均探测次数（用随机 key 跑 10 万次），解释为什么 JDK 选 0.75。
3. 用 `record` 定义一个 `Point(int x, int y)` 作为 key，放进 `HashMap` 验证能正确取回；再写一个不重写 `hashCode` 的普通类做同样实验，说明差异。
4. 实现一个"按访问顺序淘汰"的 `LinkedHashMap`（`accessOrder=true`），观察 `get` 之后遍历顺序的变化。

---

### 34-06 二叉搜索树、平衡与四种遍历

**知识点**
- **二叉搜索树（BST）** 的性质：左子树所有节点 < 根 < 右子树所有节点。由此得到**中序遍历有序**这个最重要的推论，凡是"验证 BST"或"求第 k 小"都靠它。
- BST 的查找/插入/删除都是 `O(h)`，`h` 是树高。**随机插入**时期望 `h ≈ log n`；**有序插入**时退化成链表，`h = n`，全部操作变 `O(n)`。这是 BST 最致命的缺陷。
- **删除节点分三种情况**：叶子直接删；只有一个孩子用孩子顶替；有两个孩子找**右子树最小节点**（中序后继）或左子树最大节点顶替，再递归删除那个后继。
- **四种遍历**：前序（根左右，用于复制/序列化）、中序（左根右，BST 有序输出）、后序（左右根，用于释放/自底向上计算）、层序（BFS，按层处理）。
- **递归 vs 迭代**：递归写法短但深度受栈限制（退化树会 `StackOverflowError`）；迭代用显式栈，不受调用栈限制。生产代码里"用户数据可能有序"时要用迭代。
- **平衡树**的意义：把 `h` 强制压在 `O(log n)`。AVL 严格平衡（左右高度差 ≤ 1，查询快、旋转多）；**红黑树**弱平衡（最长路径 ≤ 2×最短路径，插入删除旋转少），所以 JDK 的 `TreeMap`、`HashMap` 树化桶都用红黑树。
- **旋转**是平衡的基本操作：左旋把右孩子提上来、右旋把左孩子提上来。旋转不改变中序序列，只改变形状。
- **验证 BST 不能只比较父子**：`5` 的右子 `6` 的左子 `4` 是非法 BST，但 `4 < 6` 成立。正确做法是传上下界（`min, max`）或中序检查严格递增。
- 易错：删除双子节点时忘了递归删后继；中序遍历用 `List` 收集导致 `O(n)` 额外空间（可用 Morris 遍历做到 `O(1)`）；把"层序"写成"前序"；退化树用递归导致栈溢出。

**示例代码**
```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
```

**课后练习**
1. 给 `BinarySearchTree` 增加 `floor(int x)` 和 `ceil(int x)`，分别返回小于等于 / 大于等于 x 的最大 / 最小值，要求 `O(h)`。
2. 实现 AVL 树的插入与四种旋转（LL/RR/LR/RL），对 1..1000 顺序插入后打印树高，与普通 BST 对比。
3. 用 Morris 中序遍历把空间降到 `O(1)`，验证输出与递归版一致（提示：利用叶子节点的空 right 指针临时指向后继）。
4. 实现"把有序数组转成高度平衡的 BST"（`sortedArrayToBst`），并验证生成树的高度为 `⌈log2(n+1)⌉`。

---

### 34-07 堆与优先队列

**知识点**
- **堆**是**完全二叉树**，用数组存储：下标 `i` 的左右孩子是 `2i+1` / `2i+2`，父节点是 `(i-1)/2`。完全二叉树"中间没有空洞"，所以数组存不浪费空间，且不需要指针。
- **大顶堆**：每个节点 ≥ 它的孩子，所以堆顶是全局最大值。**小顶堆**反之。JDK 的 `PriorityQueue` 默认是**小顶堆**，要大顶堆得传 `Comparator.reverseOrder()`。
- **上浮（sift up）**用于插入：新元素放数组末尾，然后不断和父节点比较交换，直到满足堆性质。`O(log n)`。
- **下沉（sift down）**用于删除堆顶：把末尾元素换到堆顶，然后不断和较大的孩子比较交换。`O(log n)`。
- **建堆是 `O(n)` 不是 `O(n log n)`**：从最后一个非叶子节点（`n/2 - 1`）往前逐个下沉。越靠近底层的节点越多但下沉距离越短，求和后是线性。这个结论常被考。
- **堆排序**：建大顶堆 → 反复把堆顶换到末尾、堆大小减一、新堆顶下沉。时间 `O(n log n)`、空间 `O(1)`、**不稳定**。
- **`PriorityQueue` 的坑**：不是线程安全的（并发用 `PriorityBlockingQueue`）；不允许 `null`；`iterator()` **不保证有序**（只有 `poll` 才有序）；初始容量小于 1 抛异常；`Comparator` 返回 0 时元素顺序不确定。
- **Top-K 问题**用"大小为 k 的堆"：求最大的 k 个用小顶堆（堆顶是当前 k 个里最小的，来了更大的就替换），时间 `O(n log k)`，空间 `O(k)`。比全排序 `O(n log n)` 更适合海量数据。
- **多路归并**用堆：k 个有序链表/文件归并，堆里放每条链的当前头，每次取最小再补下一个，`O(N log k)`。
- **延迟删除**技巧：要删堆中任意元素时，不真的删（`O(n)` 查找），而是记一个"已删除"计数表，`poll` 出来时如果已删除就丢弃。这是滑动窗口最值、任务调度常用手法。
- 易错：`PriorityQueue` 遍历打印以为有序；用 `-` 号取反做"大顶堆"但数值溢出（`Integer.MIN_VALUE`）；`siftDown` 只比较左孩子忘了右孩子；建堆从 `n-1` 开始（多做了无用功）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

public class HeapDemo {
    /** 手写大顶堆 */
    static final class MaxHeap {
        private int[] data = new int[8];
        private int size;

        int size() {
            return size;
        }

        boolean isEmpty() {
            return size == 0;
        }

        private void ensure(int need) {
            if (need > data.length) {
                data = Arrays.copyOf(data, Math.max(need, data.length * 2));
            }
        }

        void add(int v) {
            ensure(size + 1);
            data[size] = v;
            siftUp(size);
            size++;
        }

        int peek() {
            if (size == 0) {
                throw new IllegalStateException("堆为空");
            }
            return data[0];
        }

        int poll() {
            if (size == 0) {
                throw new IllegalStateException("堆为空");
            }
            int top = data[0];
            data[0] = data[--size];
            siftDown(0, size);
            return top;
        }

        private void siftUp(int i) {
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                if (data[parent] >= data[i]) {
                    break;
                }
                swap(i, parent);
                i = parent;
            }
        }

        private void siftDown(int i, int n) {
            while (true) {
                int left = i * 2 + 1;
                int right = left + 1;
                int largest = i;
                if (left < n && data[left] > data[largest]) {
                    largest = left;
                }
                if (right < n && data[right] > data[largest]) {
                    largest = right;
                }
                if (largest == i) {
                    break;
                }
                swap(i, largest);
                i = largest;
            }
        }

        private void swap(int i, int j) {
            int t = data[i];
            data[i] = data[j];
            data[j] = t;
        }

        /** O(n) 建堆：从最后一个非叶子节点往前下沉 */
        static MaxHeap heapify(int[] values) {
            MaxHeap h = new MaxHeap();
            h.data = Arrays.copyOf(values, Math.max(values.length, 1));
            h.size = values.length;
            for (int i = values.length / 2 - 1; i >= 0; i--) {
                h.siftDown(i, h.size);
            }
            return h;
        }

        String raw() {
            return Arrays.toString(Arrays.copyOf(data, size));
        }
    }

    /** 堆排序：原地、O(n log n)、不稳定 */
    static void heapSort(int[] a) {
        int n = a.length;
        for (int i = n / 2 - 1; i >= 0; i--) {
            siftDown(a, i, n);
        }
        for (int end = n - 1; end > 0; end--) {
            int t = a[0];
            a[0] = a[end];
            a[end] = t;
            siftDown(a, 0, end);
        }
    }

    private static void siftDown(int[] a, int i, int n) {
        while (true) {
            int l = i * 2 + 1;
            int r = l + 1;
            int big = i;
            if (l < n && a[l] > a[big]) {
                big = l;
            }
            if (r < n && a[r] > a[big]) {
                big = r;
            }
            if (big == i) {
                return;
            }
            int t = a[i];
            a[i] = a[big];
            a[big] = t;
            i = big;
        }
    }

    /** Top-K：用小顶堆，堆顶是当前 k 个最大里最小的那个 */
    static int[] topK(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        for (int v : nums) {
            if (minHeap.size() < k) {
                minHeap.offer(v);
            } else if (v > minHeap.peek()) {
                minHeap.poll();
                minHeap.offer(v);
            }
        }
        int[] out = new int[minHeap.size()];
        for (int i = out.length - 1; i >= 0; i--) {
            out[i] = minHeap.poll();
        }
        return out;
    }

    /** 多路归并：k 条有序数组合成一条 */
    static int[] mergeK(int[]... arrays) {
        record Cursor(int[] arr, int idx) { }
        PriorityQueue<Cursor> pq = new PriorityQueue<>(Comparator.comparingInt(c -> c.arr()[c.idx()]));
        int total = 0;
        for (int[] arr : arrays) {
            total += arr.length;
            if (arr.length > 0) {
                pq.offer(new Cursor(arr, 0));
            }
        }
        int[] out = new int[total];
        int p = 0;
        while (!pq.isEmpty()) {
            Cursor c = pq.poll();
            out[p++] = c.arr()[c.idx()];
            if (c.idx() + 1 < c.arr().length) {
                pq.offer(new Cursor(c.arr(), c.idx() + 1));
            }
        }
        return out;
    }

    /** 延迟删除：滑动窗口中位数 / 任意删除场景的通用手法 */
    static final class LazyDeleteHeap {
        private final PriorityQueue<Integer> heap;
        private final Map<Integer, Integer> pending = new HashMap<>();
        private int logicalSize;

        LazyDeleteHeap(boolean max) {
            heap = max ? new PriorityQueue<>(Comparator.reverseOrder()) : new PriorityQueue<>();
        }

        void add(int v) {
            heap.offer(v);
            logicalSize++;
        }

        void remove(int v) {
            pending.merge(v, 1, Integer::sum);
            logicalSize--;
        }

        int peek() {
            while (!heap.isEmpty() && pending.getOrDefault(heap.peek(), 0) > 0) {
                int dead = heap.poll();
                pending.merge(dead, -1, Integer::sum);
            }
            if (heap.isEmpty()) {
                throw new IllegalStateException("逻辑上已空");
            }
            return heap.peek();
        }

        int size() {
            return logicalSize;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 大顶堆插入与出堆 ===");
        MaxHeap heap = new MaxHeap();
        for (int v : new int[]{35, 10, 50, 20, 5, 60, 15}) {
            heap.add(v);
            System.out.println("  add(" + v + ") 后数组 = " + heap.raw() + "，堆顶=" + heap.peek());
        }
        StringBuilder out = new StringBuilder();
        while (!heap.isEmpty()) {
            out.append(heap.poll()).append(' ');
        }
        System.out.println("  依次出堆（降序）: " + out.toString().trim());

        System.out.println();
        System.out.println("=== 2. O(n) 建堆 vs 逐个插入 ===");
        int[] vals = {3, 9, 2, 1, 4, 5};
        MaxHeap h1 = MaxHeap.heapify(vals.clone());
        System.out.println("  heapify(" + Arrays.toString(vals) + ") 数组 = " + h1.raw());
        MaxHeap h2 = new MaxHeap();
        for (int v : vals) {
            h2.add(v);
        }
        System.out.println("  逐个 add 后数组 = " + h2.raw());
        System.out.println("  两者都满足堆性质，但 heapify 只做 O(n) 次比较。");

        System.out.println();
        System.out.println("=== 3. 堆排序 ===");
        int[] sortMe = {5, 2, 9, 1, 7, 3, 8, 6, 4};
        System.out.println("  排序前: " + Arrays.toString(sortMe));
        heapSort(sortMe);
        System.out.println("  排序后: " + Arrays.toString(sortMe) + "（原地、O(n log n)、不稳定）");

        System.out.println();
        System.out.println("=== 4. Top-K：n=10000 取最大 5 个 ===");
        Random rnd = new Random(7);
        int[] big = new int[10_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = rnd.nextInt(100_000);
        }
        System.out.println("  topK(5) = " + Arrays.toString(topK(big, 5)));
        int[] sortedAll = big.clone();
        Arrays.sort(sortedAll);
        System.out.println("  全排序后最后 5 个 = "
                + Arrays.toString(Arrays.copyOfRange(sortedAll, sortedAll.length - 5, sortedAll.length)));
        System.out.println("  堆方案 O(n log k) 只需 k 个额外空间，不必全排序。");

        System.out.println();
        System.out.println("=== 5. 多路归并 ===");
        int[] a1 = {1, 4, 7, 10};
        int[] a2 = {2, 5, 8, 11, 14};
        int[] a3 = {3, 6, 9, 12};
        System.out.println("  A=" + Arrays.toString(a1) + " B=" + Arrays.toString(a2) + " C=" + Arrays.toString(a3));
        System.out.println("  归并 = " + Arrays.toString(mergeK(a1, a2, a3)));

        System.out.println();
        System.out.println("=== 6. 延迟删除 ===");
        LazyDeleteHeap lazy = new LazyDeleteHeap(true);
        for (int v : new int[]{5, 1, 9, 7, 3}) {
            lazy.add(v);
        }
        System.out.println("  初始最大值 = " + lazy.peek());
        lazy.remove(9);
        System.out.println("  删除 9 后最大值 = " + lazy.peek() + "，逻辑 size=" + lazy.size());
        lazy.remove(7);
        System.out.println("  再删 7 后最大值 = " + lazy.peek());

        System.out.println();
        System.out.println("=== 7. PriorityQueue 的遍历不保证有序 ===");
        PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(5, 1, 9, 7, 3));
        List<Integer> iterated = new ArrayList<>(pq);
        System.out.println("  遍历结果: " + iterated + "（只是数组顺序）");
        List<Integer> polled = new ArrayList<>();
        while (!pq.isEmpty()) {
            polled.add(pq.poll());
        }
        System.out.println("  poll 结果: " + polled + "（才是有序的）");
    }
}
```

**课后练习**
1. 实现 `MedianFinder`：支持 `addNum` 与 `findMedian`，用一个**大顶堆 + 小顶堆**维护数据流的中位数，要求 `addNum` 为 `O(log n)`。
2. 实现"合并 k 条有序链表"（不是数组），要求时间 `O(N log k)`、空间 `O(k)`，并处理 `null` 链表。
3. 用堆实现一个任务调度器：任务有 `priority` 和 `deadline`，按优先级执行，同优先级按 deadline 先到先服务。
4. 证明"从 `n/2-1` 开始下沉建堆是 `O(n)`"：对 n=1024 统计 `heapify` 的总比较次数，与 `n log n` 对比。

---

### 34-08 图：邻接表、BFS/DFS 与拓扑排序

**知识点**
- 图的两种存法：**邻接矩阵**（`n×n`，查边 `O(1)`，空间 `O(n²)`，适合稠密图）和**邻接表**（每个点挂一个邻居列表，空间 `O(n+m)`，遍历邻居快，适合稀疏图）。工程上 99% 用邻接表。
- **BFS** 用队列，按"距离起点的层数"扩散，所以**无权图的最短路径**就是 BFS 第一次到达的层数。**DFS** 用栈（或递归），一条路走到黑，适合找连通分量、判环、拓扑排序。
- 两者都要 **visited 标记**，否则有环图会死循环。BFS 在**入队时**标记（避免同一节点重复入队），DFS 在**访问时**标记。
- **连通分量**：对每个未访问节点做一次 DFS/BFS，次数就是分量个数。
- **拓扑排序**（有向无环图 DAG）两种实现：**Kahn 算法**（统计入度，入度为 0 的入队，出队时把邻居入度减 1）和 **DFS 后序逆序**。Kahn 的副产品是**能检测环**：如果输出节点数 < 总数，说明有环。
- **拓扑排序的实际用途**：任务依赖调度、构建系统（Maven 模块依赖）、课程先修关系、包管理器依赖解析。出现环就是"循环依赖"，必须报错。
- **Dijkstra** 是 BFS 的加权版：用优先队列每次取当前距离最小的节点，要求**边权非负**。有负权要用 Bellman-Ford。
- **并查集（Union-Find）** 解决"连通性"问题：`find` 带路径压缩 + `union` 按秩合并，均摊接近 `O(1)`。用于判环、Kruskal 最小生成树、朋友圈合并。
- 易错：BFS 用 `ArrayList` 当队列（`remove(0)` 是 `O(n)`）；有环图忘记 visited 导致栈溢出；拓扑排序只统计入度忘了处理"多个起点"；`Dijkstra` 用 `visited` 提前返回导致重复入队节点处理错误。

**示例代码**
```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class GraphDemo {
    /** 邻接表有向图，节点用字符串表示 */
    static final class Graph {
        private final Map<String, List<String>> adj = new HashMap<>();
        private final Map<String, Integer> indegree = new HashMap<>();

        void addNode(String v) {
            adj.putIfAbsent(v, new ArrayList<>());
            indegree.putIfAbsent(v, 0);
        }

        void addEdge(String from, String to) {
            addNode(from);
            addNode(to);
            adj.get(from).add(to);
            indegree.merge(to, 1, Integer::sum);
        }

        void addUndirectedEdge(String a, String b) {
            addEdge(a, b);
            addEdge(b, a);
        }

        int nodeCount() {
            return adj.size();
        }

        int edgeCount() {
            return adj.values().stream().mapToInt(List::size).sum();
        }

        /** BFS：返回从 start 到各点的最短边数 */
        Map<String, Integer> bfs(String start) {
            Map<String, Integer> dist = new HashMap<>();
            if (!adj.containsKey(start)) {
                return dist;
            }
            Deque<String> queue = new ArrayDeque<>();
            dist.put(start, 0);
            queue.offer(start);
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                for (String next : adj.get(cur)) {
                    if (!dist.containsKey(next)) {
                        dist.put(next, dist.get(cur) + 1);
                        queue.offer(next);
                    }
                }
            }
            return dist;
        }

        /** DFS 迭代版：显式栈，避免深图栈溢出 */
        List<String> dfs(String start) {
            List<String> order = new ArrayList<>();
            if (!adj.containsKey(start)) {
                return order;
            }
            Deque<String> stack = new ArrayDeque<>();
            java.util.Set<String> visited = new java.util.HashSet<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                String cur = stack.pop();
                if (!visited.add(cur)) {
                    continue;
                }
                order.add(cur);
                List<String> neighbors = adj.get(cur);
                for (int i = neighbors.size() - 1; i >= 0; i--) {
                    if (!visited.contains(neighbors.get(i))) {
                        stack.push(neighbors.get(i));
                    }
                }
            }
            return order;
        }

        /** Kahn 拓扑排序；有环时返回的列表短于节点数 */
        List<String> topoSortKahn() {
            Map<String, Integer> deg = new HashMap<>(indegree);
            Deque<String> queue = new ArrayDeque<>();
            for (Map.Entry<String, Integer> e : deg.entrySet()) {
                if (e.getValue() == 0) {
                    queue.offer(e.getKey());
                }
            }
            List<String> order = new ArrayList<>();
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                order.add(cur);
                for (String next : adj.get(cur)) {
                    int d = deg.merge(next, -1, Integer::sum);
                    if (d == 0) {
                        queue.offer(next);
                    }
                }
            }
            return order;
        }

        /** DFS 后序逆序拓扑排序 */
        List<String> topoSortDfs() {
            List<String> order = new ArrayList<>();
            java.util.Set<String> visited = new java.util.HashSet<>();
            for (String v : adj.keySet()) {
                if (!visited.contains(v)) {
                    dfsPost(v, visited, order);
                }
            }
            java.util.Collections.reverse(order);
            return order;
        }

        private void dfsPost(String v, java.util.Set<String> visited, List<String> order) {
            visited.add(v);
            for (String next : adj.get(v)) {
                if (!visited.contains(next)) {
                    dfsPost(next, visited, order);
                }
            }
            order.add(v);
        }

        int connectedComponents() {
            java.util.Set<String> visited = new java.util.HashSet<>();
            int count = 0;
            for (String v : adj.keySet()) {
                if (!visited.contains(v)) {
                    count++;
                    Deque<String> stack = new ArrayDeque<>();
                    stack.push(v);
                    while (!stack.isEmpty()) {
                        String cur = stack.pop();
                        if (!visited.add(cur)) {
                            continue;
                        }
                        for (String next : adj.get(cur)) {
                            if (!visited.contains(next)) {
                                stack.push(next);
                            }
                        }
                    }
                }
            }
            return count;
        }

        boolean hasCycleDirected() {
            return topoSortKahn().size() < adj.size();
        }
    }

    /** 带权图 + Dijkstra */
    static final class WeightedGraph {
        record Edge(String to, int weight) { }

        private final Map<String, List<Edge>> adj = new HashMap<>();

        void addEdge(String from, String to, int w) {
            adj.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, w));
            adj.computeIfAbsent(to, k -> new ArrayList<>());
        }

        /** 返回起点到各点的最短距离；不可达的点不出现在结果里 */
        Map<String, Integer> dijkstra(String start) {
            Map<String, Integer> dist = new HashMap<>();
            record Node(String v, int d) { }
            PriorityQueue<Node> pq = new PriorityQueue<>((x, y) -> Integer.compare(x.d(), y.d()));
            dist.put(start, 0);
            pq.offer(new Node(start, 0));
            java.util.Set<String> settled = new java.util.HashSet<>();
            while (!pq.isEmpty()) {
                Node cur = pq.poll();
                if (!settled.add(cur.v())) {
                    continue;
                }
                for (Edge e : adj.getOrDefault(cur.v(), List.of())) {
                    int nd = cur.d() + e.weight();
                    if (nd < dist.getOrDefault(e.to(), Integer.MAX_VALUE)) {
                        dist.put(e.to(), nd);
                        pq.offer(new Node(e.to(), nd));
                    }
                }
            }
            return dist;
        }
    }

    /** 并查集：路径压缩 + 按秩合并 */
    static final class UnionFind {
        private final int[] parent;
        private final int[] rank;
        private int components;

        UnionFind(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
            components = n;
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        boolean union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return false;
            }
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
            }
            components--;
            return true;
        }

        int components() {
            return components;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 有向图 + BFS 最短边数 ===");
        Graph g = new Graph();
        g.addEdge("A", "B");
        g.addEdge("A", "C");
        g.addEdge("B", "D");
        g.addEdge("C", "D");
        g.addEdge("D", "E");
        System.out.println("  节点数=" + g.nodeCount() + " 边数=" + g.edgeCount());
        System.out.println("  从 A 出发的 BFS 距离: " + g.bfs("A"));
        System.out.println("  从 C 出发的 BFS 距离: " + g.bfs("C"));

        System.out.println();
        System.out.println("=== 2. DFS 遍历顺序 ===");
        System.out.println("  DFS(A) = " + g.dfs("A"));
        System.out.println("  （BFS 按层，DFS 一条路走到底，顺序不同但都覆盖全部可达点）");

        System.out.println();
        System.out.println("=== 3. 拓扑排序：任务依赖 ===");
        Graph tasks = new Graph();
        tasks.addEdge("解析需求", "设计接口");
        tasks.addEdge("设计接口", "写代码");
        tasks.addEdge("设计接口", "写文档");
        tasks.addEdge("写代码", "单元测试");
        tasks.addEdge("写文档", "评审");
        tasks.addEdge("单元测试", "评审");
        System.out.println("  Kahn: " + tasks.topoSortKahn());
        System.out.println("  DFS : " + tasks.topoSortDfs());
        System.out.println("  两者都是合法拓扑序：每个任务都排在其依赖之后。");

        System.out.println();
        System.out.println("=== 4. 环检测 ===");
        Graph cyclic = new Graph();
        cyclic.addEdge("A", "B");
        cyclic.addEdge("B", "C");
        cyclic.addEdge("C", "A");
        System.out.println("  A->B->C->A 的拓扑序长度=" + cyclic.topoSortKahn().size()
                + "，节点数=" + cyclic.nodeCount() + "，hasCycle=" + cyclic.hasCycleDirected());
        System.out.println("  （拓扑序不完整就说明剩下的点都在环里）");

        System.out.println();
        System.out.println("=== 5. 连通分量（无向图）===");
        Graph undirected = new Graph();
        undirected.addUndirectedEdge("1", "2");
        undirected.addUndirectedEdge("2", "3");
        undirected.addUndirectedEdge("5", "6");
        undirected.addNode("9");
        System.out.println("  节点数=" + undirected.nodeCount()
                + "，连通分量数=" + undirected.connectedComponents() + "（期望 3）");

        System.out.println();
        System.out.println("=== 6. Dijkstra 最短路 ===");
        WeightedGraph wg = new WeightedGraph();
        wg.addEdge("北京", "天津", 120);
        wg.addEdge("北京", "石家庄", 280);
        wg.addEdge("天津", "济南", 300);
        wg.addEdge("石家庄", "郑州", 410);
        wg.addEdge("济南", "郑州", 380);
        wg.addEdge("郑州", "武汉", 520);
        Map<String, Integer> dist = wg.dijkstra("北京");
        System.out.println("  从北京出发的最短距离:");
        dist.forEach((city, d) -> System.out.println("    " + city + " = " + d + " km"));
        System.out.println("  注意：BFS 只能算无权图，带权图必须用 Dijkstra（边权不能为负）。");

        System.out.println();
        System.out.println("=== 7. 并查集判环 ===");
        int[][] edges = {{0, 1}, {1, 2}, {2, 0}, {3, 4}};
        UnionFind uf = new UnionFind(5);
        for (int[] e : edges) {
            if (!uf.union(e[0], e[1])) {
                System.out.println("  加入边 " + Arrays.toString(e) + " 时发现环");
            }
        }
        System.out.println("  最终连通分量数=" + uf.components());
    }
}
```

**课后练习**
1. 实现"岛屿数量"：给定 `char[][]` 的 `'1'`/`'0'` 网格，用 DFS 或 BFS 统计连通块个数，并对比两种写法的代码量。
2. 用拓扑排序实现"课程表"：给定 `numCourses` 与先修关系数组，判断能否修完所有课程，能则返回一个可行顺序。
3. 实现 Dijkstra 的"路径还原"：除了距离，还要返回从起点到终点的具体路径（提示：记录每个节点的前驱）。
4. 用并查集实现 Kruskal 最小生成树，在 5 个城市的带权图上求出最小连通代价，并说明为什么必须"按边权排序 + 判环"。
