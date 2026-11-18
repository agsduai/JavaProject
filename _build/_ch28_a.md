### 28-01 为什么需要消息队列

**知识点**
- 消息队列解决三类问题：**解耦**（生产者不用知道谁在消费、有几个消费者）、**异步**（非核心链路移出主流程，接口 RT 从 100ms 降到 20ms）、**削峰**（瞬时流量先入队，消费者按自己的处理能力匀速取）。
- 同步调用的耦合代价：下单接口里串行调积分、短信、物流，任一环节超时或报错，要么整单失败，要么用户白等；加一个新下游就要改一次主流程代码。
- 异步的代价：**一致性从强变最终**——下单成功那一刻积分可能还没加；**问题定位变难**——链路断在队列里，必须有 traceId 和消费日志；**运维复杂度上升**——多了一个必须高可用的中间件。
- 判断标准：核心链路（扣库存、创建订单）保持同步；可补偿、可延迟、非强一致的旁路（通知、积分、报表、搜索索引）走异步。
- 削峰要看"消费能力"而不是"队列能存多少"：队列只是缓冲，如果消费者长期处理不过来，积压只会无限增长，最终还是要限流或扩容。
- 易错：把"异步"当成"不用管结果"，结果消息丢了没人知道；在事务提交前就发消息，事务回滚了消息却已经投出去（第 28-09 课专门解决）。

**示例代码**
```java
public class QueueIntro {
    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 模拟一次 RPC 或数据库操作 */
    static void step(String name, long ms) {
        sleep(ms);
        System.out.println("    " + name + " 完成（" + ms + "ms）");
    }

    public static void main(String[] args) {
        System.out.println("=== 同步串行：下单接口里干完所有事 ===");
        long begin = System.currentTimeMillis();
        step("写订单库", 20);
        step("加积分", 30);
        step("发短信", 40);
        step("通知物流", 30);
        long syncCost = System.currentTimeMillis() - begin;
        System.out.println("  同步总耗时 " + syncCost + "ms，用户一直在等；短信服务一挂，订单也得回滚");

        System.out.println();
        System.out.println("=== 异步解耦：下单只做核心链路，其余投递消息 ===");
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        begin = System.currentTimeMillis();
        step("写订单库", 20);
        queue.addLast("order.created:1001");
        long asyncCost = System.currentTimeMillis() - begin;
        System.out.println("  异步总耗时 " + asyncCost + "ms，队列待处理 " + queue.size() + " 条");

        System.out.println();
        System.out.println("=== 削峰：瞬时涌入 20 个请求，消费者每批只处理 5 个 ===");
        for (int i = 1; i <= 20; i++) {
            queue.addLast("order.created:" + (1000 + i));
        }
        System.out.println("  队列积压 " + queue.size() + " 条");
        int batch = 0;
        while (!queue.isEmpty()) {
            batch++;
            int handled = 0;
            for (int i = 0; i < 5 && !queue.isEmpty(); i++) {
                queue.pollFirst();
                handled++;
            }
            System.out.println("  第 " + batch + " 批处理 " + handled + " 条，剩余 " + queue.size() + " 条");
        }
        System.out.println("  峰值被摊平，数据库不会被瞬时流量打穿");
    }
}
```

**课后练习**
1. 把示例里的"加积分"改成必须成功才允许下单，说明此时还能不能用异步，为什么。
2. 统计同步流程与异步流程的耗时差，并解释这个差值在什么情况下会反过来（异步反而更慢）。
3. 设计一个判断表：列出你熟悉的 5 个业务动作，逐个标注"必须同步 / 可以异步 / 只能异步"。

### 28-02 两种消息模型：点对点与发布订阅

**知识点**
- **点对点（Queue）**：一条消息只会被**一个**消费者拿到，多个消费者是竞争关系，天然做负载均衡，适合"任务分发"（发邮件、生成报表）。
- **发布订阅（Topic）**：一条消息会被**每个**订阅组各收到一份，组内仍然是竞争消费，适合"事件广播"（订单创建后积分、短信、物流各自处理）。
- 关键区别在"消费组"：Kafka/RocketMQ 的 Topic 下挂多个 consumer group，组间广播、组内负载；RabbitMQ 的 fanout 交换机是纯广播，每个队列都收到。
- 语义差异决定重试策略：点对点任务失败重投只影响自己；发布订阅里某个订阅者反复失败会拖慢整个消费组，通常单独配死信队列。
- 易错：把"多消费者"当成"广播"——同组内加机器只会分走消息，不会让每台都收到；想广播必须用不同的消费组。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageModel {
    /** 点对点：一条消息只能被取走一次 */
    static class PointToPointQueue {
        private final List<String> buffer = new ArrayList<>();

        void send(String msg) {
            buffer.add(msg);
        }

        String receive() {
            return buffer.isEmpty() ? null : buffer.remove(0);
        }

        int size() {
            return buffer.size();
        }
    }

    /** 发布订阅：每个订阅者各有一份信箱 */
    static class Topic {
        private final String name;
        private final Map<String, List<String>> mailboxes = new HashMap<>();

        Topic(String name) {
            this.name = name;
        }

        void subscribe(String consumer) {
            mailboxes.putIfAbsent(consumer, new ArrayList<>());
        }

        void publish(String msg) {
            for (List<String> box : mailboxes.values()) {
                box.add(msg);
            }
        }

        List<String> drain(String consumer) {
            List<String> box = mailboxes.get(consumer);
            if (box == null) {
                return List.of();
            }
            List<String> copy = new ArrayList<>(box);
            box.clear();
            return copy;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 点对点：多个消费者竞争，消息不重复 ===");
        PointToPointQueue queue = new PointToPointQueue();
        for (int i = 1; i <= 5; i++) {
            queue.send("TASK-" + i);
        }
        System.out.println("  队列初始 " + queue.size() + " 条");
        System.out.println("  消费者A 取到 " + queue.receive() + "、" + queue.receive());
        System.out.println("  消费者B 取到 " + queue.receive() + "（不会和A重复）");
        System.out.println("  剩余 " + queue.size() + " 条待取");

        System.out.println();
        System.out.println("=== 发布订阅：每个订阅组都收到全量 ===");
        Topic topic = new Topic("order.created");
        topic.subscribe("积分服务");
        topic.subscribe("短信服务");
        topic.subscribe("物流服务");
        topic.publish("ORDER-1001");
        topic.publish("ORDER-1002");
        for (String consumer : List.of("积分服务", "短信服务", "物流服务")) {
            System.out.println("  " + consumer + " 收到 " + topic.drain(consumer) + "（topic=" + topic.name + "）");
        }

        System.out.println();
        System.out.println("=== 同一订阅组内加机器：只会分摊，不会重复 ===");
        topic.publish("ORDER-1003");
        List<String> shared = topic.drain("积分服务");
        System.out.println("  积分服务组内 2 台机器，本轮总共只消费 " + shared.size() + " 条：" + shared);
    }
}
```

**课后练习**
1. 给 `PointToPointQueue` 加上"取走但处理失败要还回队列"的方法，思考它与 28-04 的 nack 是什么关系。
2. 用发布订阅模型实现"用户注册成功后，积分服务、优惠券服务、欢迎邮件服务各自处理"，画出消息流向。
3. 解释为什么"给同一个消费组加机器"不能解决消息重复消费的问题，而只能提升吞吐。

### 28-03 可靠投递：发送确认、持久化与重试

**知识点**
- 消息可能丢在三个地方：**生产者到 Broker**（网络抖动、Broker 未落盘）、**Broker 内部**（内存写成功但宕机）、**Broker 到消费者**（消费失败但已 ack）。三处要分别兜底。
- 生产端可靠投递三板斧：**发送确认**（RabbitMQ publisher confirm、Kafka `acks=all`）、**失败重试**（有限次 + 退避）、**本地消息表兜底**（28-09）。
- 持久化：交换机、队列、消息三者都要持久化，只持久化消息而不持久化队列，重启后队列没了消息一样丢。
- `acks` 语义：`acks=0` 发出去就算成功（最快最不安全）；`acks=1` leader 落盘即返回（leader 挂了可能丢）；`acks=all` 等所有 ISR 副本确认（最安全最慢）。
- 重试要有上限和退避，否则一次 Broker 抖动会引发无限重投，把刚恢复的集群再打挂。
- 易错：只在 catch 里重试一次就放弃；重试时没有记录原始消息 ID，导致下游无法幂等（见 28-05）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ReliableSend {
    /** 模拟 Broker：内存队列 + 磁盘日志 */
    static class Broker {
        final List<String> memory = new ArrayList<>();
        final List<String> disk = new ArrayList<>();
        boolean persistent = true;

        /** 前两次投递模拟网络超时，第三次成功 */
        boolean publish(String msg, int attempt) {
            if (attempt <= 2) {
                System.out.println("    第 " + attempt + " 次投递失败：连接超时，消息未落盘");
                return false;
            }
            memory.add(msg);
            if (persistent) {
                disk.add(msg);
            }
            System.out.println("    第 " + attempt + " 次投递成功，收到 confirm，落盘=" + persistent);
            return true;
        }
    }

    public static void main(String[] args) {
        Broker broker = new Broker();
        String msg = "ORDER-1001";
        boolean ok = false;
        for (int attempt = 1; attempt <= 4 && !ok; attempt++) {
            ok = broker.publish(msg, attempt);
            if (!ok) {
                System.out.println("    -> 退避 200ms 后重试");
            }
        }
        System.out.println("最终投递=" + ok + "，内存=" + broker.memory + "，磁盘=" + broker.disk);

        System.out.println();
        System.out.println("=== 关掉持久化：confirm 依然成功，但重启后消息没了 ===");
        Broker noDisk = new Broker();
        noDisk.persistent = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (noDisk.publish(msg, attempt)) {
                break;
            }
        }
        System.out.println("  broker 重启后可从磁盘恢复的消息=" + noDisk.disk + "（空 => 消息丢了）");
        System.out.println("  结论：confirm 只证明 Broker 收到了，能不能活过重启取决于持久化");

        System.out.println();
        System.out.println("=== acks 三档对比 ===");
        System.out.println("  acks=0   吞吐最高，leader 没收到也可能算成功，宕机必丢");
        System.out.println("  acks=1   等 leader 落盘，leader 挂掉且未同步时丢");
        System.out.println("  acks=all 等 ISR 全部确认，最安全，延迟最高");
        System.out.println("  订单、支付类消息用 all；日志、埋点可以放宽到 1");
    }
}
```

**课后练习**
1. 给重试加上指数退避（200ms、400ms、800ms），并统计总等待时间。
2. 说明"消息持久化 + 队列非持久化"在 Broker 重启后会发生什么。
3. 为什么 `acks=all` 也不是绝对不丢？从 ISR 收缩的角度解释。

### 28-04 消费确认、重试与死信队列

**知识点**
- **自动 ack**：消息一投出就从 Broker 删除，消费者拿到后进程崩溃 → 消息永久丢失。只适合"丢了也无所谓"的场景（日志、埋点）。
- **手动 ack**：业务处理成功后调用 `basicAck`；失败调用 `basicNack`（`requeue=true` 重回队列，或 `false` 进死信）。
- 重回队列要小心：立刻 requeue 会让失败消息瞬间又回到队首，形成"毒消息"死循环，通常配合延迟重试（重试队列 / 延迟交换机）。
- **死信队列（DLQ）**：超过最大重试次数、被拒绝、TTL 过期的消息转入 DLQ，由人工或补偿任务处理，避免无限重试拖垮消费者。
- 消费端幂等是前提：手动 ack 在"业务成功但 ack 前宕机"时会重复投递，所以消费者必须能承受重复（28-05）。
- 易错：ack 写在业务逻辑之前；重试次数存在内存里（重启就归零）；死信队列没人看，等于换个地方丢消息。

**示例代码**
```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class AckAndRetry {
    static final Map<String, Integer> ATTEMPTS = new HashMap<>();
    static final int MAX_RETRY = 3;
    static final Deque<String> MAIN = new ArrayDeque<>();
    static final Deque<String> RETRY = new ArrayDeque<>();
    static final Deque<String> DEAD = new ArrayDeque<>();

    /** 模拟业务处理：消息里带 fail 就失败 */
    static boolean handle(String msg) {
        int times = ATTEMPTS.merge(msg, 1, Integer::sum);
        boolean ok = !msg.contains("fail");
        System.out.println("    处理 " + msg + " 第 " + times + " 次 => " + (ok ? "成功，ack" : "失败，nack"));
        return ok;
    }

    /** 处理失败后决定去向：还能重试进重试队列，超限进死信 */
    static void route(String msg) {
        if (ATTEMPTS.get(msg) < MAX_RETRY) {
            RETRY.addLast(msg);
        } else {
            DEAD.addLast(msg);
        }
    }

    public static void main(String[] args) {
        MAIN.add("ORDER-1001:ok");
        MAIN.add("ORDER-1002:fail");
        MAIN.add("ORDER-1003:ok");
        System.out.println("=== 手动 ack：成功才确认，失败进重试 ===");
        while (!MAIN.isEmpty()) {
            String msg = MAIN.pollFirst();
            if (!handle(msg)) {
                route(msg);
            }
        }
        System.out.println("  第一轮结束：重试队列=" + RETRY + "，死信队列=" + DEAD);

        int round = 1;
        while (!RETRY.isEmpty()) {
            System.out.println("  第 " + round + " 轮延迟重试（模拟 2s 后再投，避免毒消息打转）");
            int size = RETRY.size();
            for (int i = 0; i < size; i++) {
                String msg = RETRY.pollFirst();
                if (!handle(msg)) {
                    route(msg);
                }
            }
            round++;
        }
        System.out.println("  重试耗尽，死信队列=" + DEAD + "，转人工或补偿任务处理");
        System.out.println("  尝试次数记录：" + ATTEMPTS);

        System.out.println();
        System.out.println("=== 对比：自动 ack 的丢失场景 ===");
        System.out.println("  消息投出即删除 -> 消费者进程被 kill -> Broker 无副本 -> 消息永久丢失");
        System.out.println("  手动 ack 也可能重复：业务已提交、ack 前宕机 -> 重新投递 -> 必须靠幂等兜住");
    }
}
```

**课后练习**
1. 给示例加上"重试队列延迟 N 秒后再投"的时间字段，并说明为什么不能立即 requeue。
2. 实现一个死信告警：死信队列长度超过阈值时打印告警日志。
3. 分析"业务成功但 ack 失败"与"ack 成功但业务失败"两种异常，分别会导致什么后果。
