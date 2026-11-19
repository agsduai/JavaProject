### 65-01 IM 系统全景：连接、消息、会话

**知识点**
- **IM 与 HTTP 服务的根本差异是"服务端主动推送"**：HTTP 是"客户端问、服务端答"；IM 是"A 发消息，服务端要主动推给 B"——**必须有长连接**（WebSocket/TCP 自研协议），否则只能靠轮询（延迟高、浪费大）。
- **IM 的四大子系统**：**连接层**（长连接网关：握手、心跳、断线重连）、**消息层**（可靠投递：ACK、重传、去重、顺序）、**会话层**（会话列表、已读、未读数、多端同步）、**存储层**（消息漫游、离线消息、历史查询）。
- **三个核心难点**：**可靠性**（消息不丢不重，43/62 章语义在 IM 的强化）、**一致性**（多端同步：手机读过，平板也要显示已读）、**扩展性**（10 万长连接、群聊 500 人扇出）。
- **与 47-02 WebSocket 的关系**：47-02 讲了 WebSocket 握手与心跳（传输层）；本章讲"建在长连接之上的消息系统"（应用层语义）——**传输与业务分层**。
- **IM 的存储模型**：**消息表按会话分片**（conversationId 为 key，同会话消息物理相邻，便于拉取最近 N 条）；**每用户一个收件箱**（写扩散）或**只存会话流水**（读扩散）——65-06 的核心权衡。
- 易错点：用轮询做"实时"（延迟与成本双输）；消息只存最新状态（聊天记录丢失）；没有消息序号（乱序、重复无法判断）；群聊按成员逐条复制（写放大爆炸）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImOverview {
    record Subsystem(String name, String duty, String keyProblem) {
    }

    static void subsystems() {
        System.out.println("【IM 四大子系统】");
        List<Subsystem> subs = List.of(
                new Subsystem("连接层", "握手、心跳、断线重连", "10 万连接的内存与调度"),
                new Subsystem("消息层", "ACK、重传、去重、顺序", "不丢不重不乱序"),
                new Subsystem("会话层", "会话列表、已读、未读、多端", "多端状态一致"),
                new Subsystem("存储层", "漫游、离线、历史查询", "海量消息的读写模型"));
        for (Subsystem s : subs) {
            System.out.printf("  %-6s %-24s 难点：%s%n", s.name(), s.duty(), s.keyProblem());
        }
        System.out.println();
    }

    static void pushVsPolling() {
        System.out.println("【推送 vs 轮询】");
        String[][] rows = {
                {"轮询（每秒）", "1s 平均延迟", "10 万在线 = 每秒 10 万次请求"},
                {"长轮询", "秒级，且连接占用", "仍是「请求-响应」模型"},
                {"长连接推送", "毫秒级", "维持 10 万连接（内存 + 心跳）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s 延迟=%-20s 代价=%s%n", r[0], r[1], r[2]);
        }
        System.out.println("  结论：实时 IM 必须长连接；轮询只用于降级兜底");
        System.out.println();
    }

    static void keyConcepts() {
        System.out.println("【三个必须记住的概念】");
        Map<String, String> concepts = new LinkedHashMap<>();
        concepts.put("会话（conversation）", "单聊是双人会话，群聊是多人会话；消息按会话聚合");
        concepts.put("消息序号（seq）", "会话内单调递增；用于排序、去重、断线补偿");
        concepts.put("收件箱（inbox）", "每个用户能看到的消息引用列表；写扩散或读扩散（65-06）");
        concepts.forEach((k, v) -> System.out.printf("  %-18s %s%n", k, v));
    }

    public static void main(String[] args) {
        subsystems();
        pushVsPolling();
        keyConcepts();
    }
}
```

**课后练习**
1. 画出你理解的 IM 四层架构（连接/消息/会话/存储），标注每层的数据流与存储位置。
2. 对比"每秒轮询"与"长连接推送"在 10 万在线用户下的请求量与延迟，量化差异。
3. 思考题：为什么"消息序号"必须是"会话内"而非"全局"递增？从单会话有序需求与全局序号瓶颈分析。

---

### 65-02 长连接网关：连接管理与心跳

**知识点**
- **连接网关的职责**：**握手鉴权**（连接建立时校验 token，绑定 userId 与连接）、**连接注册表**（userId/deviceId -> 连接句柄，内存 Map）、**心跳保活**（客户端与服务端双向心跳，检测半开连接）、**断线清理**（心跳超时摘除连接与注册）、**路由查询**（谁在线、在哪个网关）。
- **心跳的两个方向**：**客户端定时 ping**（告诉服务端我还活着）、**服务端定时探活**（写空帧或 ping，检测 TCP 半开——**半开连接是最隐蔽的问题**：网络断了但双方 socket 都没收到 FIN）。
- **连接的内存账**：每连接一个 Channel 对象 + 缓冲区 + 会话状态，**10 万连接约 1~3GB**（取决于缓冲区）；**心跳频率**（30s ping / 90s 超时是常见值）——**太频繁浪费，太稀疏发现慢**。
- **网关的水平扩展与路由**：多网关实例 + **路由表**（userId -> gatewayId）；**跨网关投递**（A 在网关 1，B 在网关 2 -> 网关 1 通过内部 RPC/消息把消息转给网关 2 推送）。
- **与 60-06 Netty 的对应**：网关就是 Netty 的经典应用（主从 Reactor + 业务线程池）；**不要在 EventLoop 里做阻塞操作**（60-06 铁律）。
- 易错点：只靠 TCP keepalive（默认 2 小时，太慢）；心跳只客户端单向（服务端挂死客户端不知道）；连接表无上限（内存爆）；网关重启不重连（用户全掉线）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConnectionGateway {
    record Conn(String connId, String userId, String deviceId, String gatewayId,
                long lastPingMs, boolean alive) {
    }

    record Gateway(String id, List<Conn> conns) {
    }

    static final long PING_TIMEOUT_MS = 90_000;

    /** 心跳扫描：超时连接摘除 */
    static List<Conn> reap(List<Conn> conns, long now) {
        List<Conn> alive = new ArrayList<>();
        for (Conn c : conns) {
            long idle = now - c.lastPingMs();
            boolean ok = c.alive() && idle <= PING_TIMEOUT_MS;
            System.out.printf("  连接 %-8s user=%-6s device=%-6s 空闲=%-7dms -> %s%n",
                    c.connId(), c.userId(), c.deviceId(), idle, ok ? "存活" : "摘除");
            if (ok) {
                alive.add(c);
            }
        }
        return alive;
    }

    /** 路由：按 userId 查所在网关（多端则多连接） */
    static Map<String, List<String>> route(List<Conn> conns) {
        Map<String, List<String>> routes = new LinkedHashMap<>();
        for (Conn c : conns) {
            routes.computeIfAbsent(c.userId(), k -> new ArrayList<>())
                    .add(c.gatewayId() + "/" + c.deviceId());
        }
        return routes;
    }

    /** 跨网关投递：本地推送或转给目标网关 */
    static String deliver(Map<String, List<String>> routes, String toUser,
                          String fromGateway) {
        List<String> targets = routes.get(toUser);
        if (targets == null) {
            return "用户 " + toUser + " 不在线 -> 转离线消息（65-07）";
        }
        List<String> local = new ArrayList<>();
        List<String> remote = new ArrayList<>();
        for (String t : targets) {
            String gw = t.split("/")[0];
            if (gw.equals(fromGateway)) {
                local.add(t);
            } else {
                remote.add(t);
            }
        }
        return "本地推送=" + local + " 跨网关转发=" + remote;
    }

    public static void main(String[] args) {
        long now = 1_000_000;
        List<Conn> conns = new ArrayList<>(List.of(
                new Conn("c-1", "u-1", "phone", "gw-1", now - 10_000, true),
                new Conn("c-2", "u-1", "pad", "gw-2", now - 20_000, true),
                new Conn("c-3", "u-2", "phone", "gw-1", now - 200_000, true),
                new Conn("c-4", "u-3", "phone", "gw-2", now - 5_000, false)));
        System.out.println("【心跳扫描】");
        List<Conn> alive = reap(conns, now);
        System.out.println();
        System.out.println("【路由表】");
        Map<String, List<String>> routes = route(alive);
        routes.forEach((u, ts) -> System.out.printf("  %-6s -> %s%n", u, ts));
        System.out.println();
        System.out.println("【投递】");
        System.out.println("  u-1：" + deliver(routes, "u-1", "gw-1"));
        System.out.println("  u-2：" + deliver(routes, "u-2", "gw-1"));
        System.out.println("  u-9：" + deliver(routes, "u-9", "gw-1"));
    }
}
```

**课后练习**
1. 实现连接网关：连接注册/心跳刷新/超时摘除/路由表，模拟"半开连接（alive 但长时间无心跳）"被正确摘除。
2. 实现多端登录：同一 userId 多 deviceId 各自一条连接，验证"新设备登录踢旧设备"与"多端并存"两种策略。
3. 思考题：为什么 TCP keepalive 不能替代应用层心跳？从默认两小时探测间隔与半开连接的检测时效分析。

---

### 65-03 协议设计：消息帧与版本兼容

**知识点**
- **长连接协议必须自己定义帧边界**：TCP 是字节流（18-01），**必须用"长度头 + 类型 + 载荷"**划帧——否则粘包半包无法解析；**二进制协议**（紧凑、省流量、解析快）是 IM 主流，JSON 文本协议（易调试）用于内部或低频场景。
- **帧结构设计**：`magic（魔数，识别协议）| version（协议版本）| type（帧类型）| flags（压缩/加密标记）| length（载荷长度）| payload | checksum（可选）`——**魔数用于快速区分非法连接**，版本用于**灰度升级**。
- **帧类型**：握手、心跳、单聊消息、群消息、ACK、已读回执、拉取历史、踢下线——**用 type 区分业务，而不是在 payload 里塞不同 JSON 结构**（解析路径清晰）。
- **版本兼容**：**协议版本只在"不兼容变更"时升级**（字段语义改变）；**新增字段用"可选字段 + 忽略未知"**；**服务端要同时支持 N 与 N-1 版本**（65-05 的灰度思想）。
- **与 18-01/60-07 的对应**：18-01 的长度头协议是手工版，60-07 的 `LengthFieldBasedFrameDecoder` 是 Netty 实现——**本章把帧的语义层设计完整**。
- 易错点：只用分隔符（消息体含分隔符就崩）；不做版本号（升级即全量不兼容）；payload 用 Java 原生序列化（36-08 的反序列化漏洞 + 跨语言困难）；帧无长度上限（恶意大包打爆内存）。

**示例代码**
```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

public class FrameProtocol {
    static final int MAGIC = 0xCAFE;
    static final byte VERSION = 2;
    static final int MAX_PAYLOAD = 1024 * 1024;

    /** 帧类型 */
    enum Type {
        HANDSHAKE(1), PING(2), PONG(3), SINGLE_MSG(4), GROUP_MSG(5),
        ACK(6), READ_RECEIPT(7), HISTORY_REQ(8), KICK(9);

        final int code;

        Type(int code) {
            this.code = code;
        }

        static Type of(int code) {
            for (Type t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
            throw new IllegalArgumentException("未知帧类型：" + code);
        }
    }

    record Frame(Type type, String payload) {
    }

    static byte[] encode(Frame frame) throws Exception {
        byte[] body = frame.payload().getBytes("UTF-8");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeShort(MAGIC);
            out.writeByte(VERSION);
            out.writeByte(frame.type().code);
            out.writeInt(body.length);
            out.write(body);
        }
        return bos.toByteArray();
    }

    /** 解码：先读头再按长度读 body（解决粘包半包） */
    static List<Frame> decode(byte[] stream) throws Exception {
        List<Frame> frames = new ArrayList<>();
        int pos = 0;
        while (stream.length - pos >= 8) {
            ByteArrayInputStream bis = new ByteArrayInputStream(stream, pos, stream.length - pos);
            DataInputStream in = new DataInputStream(bis);
            int magic = in.readUnsignedShort();
            if (magic != MAGIC) {
                System.out.println("  非法魔数 " + Integer.toHexString(magic) + " -> 关闭连接");
                break;
            }
            int version = in.readUnsignedByte();
            int typeCode = in.readUnsignedByte();
            int len = in.readInt();
            if (len < 0 || len > MAX_PAYLOAD) {
                System.out.println("  载荷长度非法 " + len + " -> 关闭连接（防大包攻击）");
                break;
            }
            if (stream.length - pos - 8 < len) {
                System.out.println("  剩余 " + (stream.length - pos - 8) + " 字节不足 -> 半包，等待更多数据");
                break;
            }
            byte[] body = new byte[len];
            in.readFully(body);
            frames.add(new Frame(Type.of(typeCode), new String(body, "UTF-8")));
            if (version < VERSION) {
                System.out.println("  收到旧版本 v" + version + " 帧 -> 兼容处理（服务端同时支持 v1/v2）");
            }
            pos += 8 + len;
        }
        return frames;
    }

    public static void main(String[] args) throws Exception {
        List<Frame> outbound = List.of(
                new Frame(Type.HANDSHAKE, "token=abc;device=phone"),
                new Frame(Type.SINGLE_MSG, "to=u-2;text=hello"),
                new Frame(Type.PING, ""),
                new Frame(Type.GROUP_MSG, "gid=g-1;text=hi all"));
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (Frame f : outbound) {
            all.write(encode(f));
        }
        byte[] stream = all.toByteArray();
        System.out.println("【编码后字节数】" + stream.length);
        System.out.println("【一次解码（粘包）】");
        List<Frame> decoded = decode(stream);
        decoded.forEach(f -> System.out.printf("  type=%-10s payload=%s%n", f.type(), f.payload()));

        System.out.println();
        System.out.println("【半包：截断 5 字节】");
        byte[] partial = java.util.Arrays.copyOf(stream, stream.length - 5);
        decode(partial);

        System.out.println();
        System.out.println("【非法魔数】");
        byte[] bad = stream.clone();
        bad[0] = 0x00;
        decode(bad);
    }
}
```

**课后练习**
1. 扩展帧协议：加入 flags（压缩/加密位）与 checksum，验证"压缩位开启时 payload 解压后一致"。
2. 实现"未知字段忽略"的兼容解码：v3 帧带额外字段，v2 解析器忽略而不报错。
3. 思考题：为什么协议帧要设"最大载荷"？从内存攻击与慢速攻击（slowloris）两个角度分析。

---

### 65-04 消息可靠投递：ACK、重传与去重

**知识点**
- **IM 的投递是"至少一次 + 客户端去重"**（28/62 章语义的应用）：服务端把消息写入存储 -> 推送给在线接收方 -> **接收方回 ACK** -> 服务端标记"已送达"；**未收到 ACK 则重传**（指数退避），**客户端按 msgId 去重**——**任何"可靠投递"最终都要落到幂等**。
- **三级 ACK 链路**：**客户端 -> 服务端**（上行 ACK：消息已收到并落库）、**服务端 -> 接收端**（下行 ACK：已推送）、**接收端 -> 服务端**（接收 ACK：已收到）——**每一跳都可能丢，所以每一跳都要 ACK**。
- **重传的边界**：**只重传"未 ACK"的消息**（不能重传已 ACK 的）；**重传有上限**（超时多次 -> 标记失败，转为"发送失败"提示用户）；**重传要按 msgId 幂等**（服务端与客户端都去重）。
- **消息状态机**：`CREATED -> STORED -> PUSHED -> DELIVERED -> READ`——**未读数 = STORED - READ**（65-05 的已读回执）；状态只进不退（51-03 同款）。
- **消息 ID 的生成**：**服务端生成**（雪花 39-07：全局唯一 + 趋势递增，便于排序）；**客户端生成 clientMsgId**（用于上行的幂等与"发送中"状态关联）——**双 ID 体系**是 IM 的标准做法。
- 易错点：无 ACK 直接标已送达（消息丢了不知道）；重传不去重（客户端重复显示）；消息 ID 用时间戳（同毫秒冲突）；状态可回退（已读变未读）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReliableDelivery {
    enum State {
        CREATED, STORED, PUSHED, DELIVERED, READ, FAILED
    }

    record Message(String msgId, String clientMsgId, String from, String to,
                   String text, State state, int retries) {
        Message advance(State next) {
            return new Message(msgId, clientMsgId, from, to, text, next, retries);
        }

        Message retry() {
            return new Message(msgId, clientMsgId, from, to, text, state, retries + 1);
        }
    }

    static final Map<String, Message> store = new LinkedHashMap<>();
    static final Map<String, String> clientIdIndex = new LinkedHashMap<>();   // 上行幂等

    /** 上行：客户端发消息（clientMsgId 幂等） */
    static String send(String clientMsgId, String from, String to, String text, long seq) {
        String existing = clientIdIndex.get(clientMsgId);
        if (existing != null) {
            System.out.println("  上行重复 clientMsgId=" + clientMsgId + " -> 返回已存在的 msgId");
            return existing;
        }
        String msgId = "m-" + seq;
        store.put(msgId, new Message(msgId, clientMsgId, from, to, text, State.STORED, 0));
        clientIdIndex.put(clientMsgId, msgId);
        System.out.println("  上行消息落库：msgId=" + msgId + " 状态 STORED");
        return msgId;
    }

    /** 下行推送：标记 PUSHED，等待接收 ACK */
    static void push(String msgId) {
        Message m = store.get(msgId);
        if (m != null) {
            store.put(msgId, m.advance(State.PUSHED));
            System.out.println("  推送 " + msgId + " -> 等待接收 ACK");
        }
    }

    /** 接收 ACK：幂等（已 DELIVERED 则忽略），未收到则重传 */
    static void onAck(String msgId, boolean ackReceived) {
        Message m = store.get(msgId);
        if (m == null) {
            return;
        }
        if (ackReceived) {
            if (m.state() == State.DELIVERED || m.state() == State.READ) {
                System.out.println("  重复 ACK " + msgId + " -> 忽略（幂等）");
                return;
            }
            store.put(msgId, m.advance(State.DELIVERED));
            System.out.println("  收到 ACK " + msgId + " -> DELIVERED");
            return;
        }
        if (m.retries() >= 3) {
            store.put(msgId, m.advance(State.FAILED));
            System.out.println("  " + msgId + " 重传 3 次仍无 ACK -> FAILED（提示发送失败）");
            return;
        }
        store.put(msgId, m.retry());
        System.out.println("  " + msgId + " 无 ACK -> 第 " + (m.retries() + 1) + " 次重传");
    }

    /** 已读回执：DELIVERED -> READ，只进不退 */
    static void onRead(String msgId) {
        Message m = store.get(msgId);
        if (m == null || m.state() == State.READ) {
            return;
        }
        if (m.state() == State.DELIVERED || m.state() == State.PUSHED
                || m.state() == State.STORED) {
            store.put(msgId, m.advance(State.READ));
            System.out.println("  " + msgId + " 已读回执 -> READ");
        }
    }

    public static void main(String[] args) {
        System.out.println("【上行幂等】");
        send("client-1", "u-1", "u-2", "你好", 1);
        send("client-1", "u-1", "u-2", "你好", 2);

        System.out.println();
        System.out.println("【下行 ACK 与重传】");
        push("m-1");
        onAck("m-1", false);
        onAck("m-1", false);
        onAck("m-1", true);
        onAck("m-1", true);

        System.out.println();
        System.out.println("【失败路径】");
        send("client-2", "u-1", "u-2", "在吗", 3);
        push("m-3");
        for (int i = 0; i < 4; i++) {
            onAck("m-3", false);
        }

        System.out.println();
        System.out.println("【已读回执】");
        onRead("m-1");
        onRead("m-1");

        System.out.println();
        System.out.println("【最终状态】");
        store.values().forEach(m -> System.out.printf("  %-4s %-10s retries=%d%n",
                m.msgId(), m.state(), m.retries()));
    }
}
```

**课后练习**
1. 实现完整投递状态机：五态流转 + 三级 ACK + 重传上限 + 上行/下行双向去重，用 3 条消息跑通"成功/重传成功/失败"三条路径。
2. 实现"未读数计算"：`STORED - READ`，构造"多端读取"场景验证未读数在各端的表现。
3. 思考题：为什么 IM 采用"至少一次 + 去重"而不是"尝试做到恰好一次"？从网络不可靠与端侧去重成本分析。

---

### 65-05 会话与消息同步：多端一致性

**知识点**
- **会话同步的核心是"每会话一个递增序号（seq）"**：同一会话内消息 seq 严格递增，**客户端记录"已拉取到 seq=N"**，断线重连后请求 `seq > N` 的增量——**这是"增量同步"的唯一可靠方式**（比时间戳可靠，时钟不可信 45-07）。
- **多端状态的三类同步**：**消息**（每端各自拉增量，互不影响）、**已读位点**（一端读了，其他端要同步"已读到 seq=M"）、**会话列表**（最后一条消息/未读数/置顶/免打扰）。
- **已读位点的实现**：每个（用户 × 会话）记录一个 `readSeq`——**多端上报取最大值**（手机读到 100，平板读到 80 -> readSeq=100）；**已读回执广播**给会话内其他成员（单聊双方、群聊全员或仅发送者）。
- **多端消息的重复与丢失**：**同一账号多端**（手机/平板/PC）各自独立拉取，**服务端按会话分发**；**多端同时在线**时要确保"一端删除会话不影响另一端的数据"（软删除 + 同步操作事件）。
- **与 62-07 Offset 的类比**：IM 的 seq 就是"会话级 offset"——**幂等、单调、可重放**；差异是 IM 要按会话维护（一个用户 N 个会话 = N 个位点）。
- 易错点：用时间戳做增量同步（时钟漂移导致漏拉/重复）；已读位点每端独立（不同步，未读数混乱）；会话列表不同步（一端删了另一端还在）；seq 由客户端生成（多端冲突）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConversationSync {
    /** 会话消息：seq 由服务端单调分配 */
    static final Map<String, List<String>> messages = new LinkedHashMap<>();
    static long[] seqOf = new long[1];

    /** 每用户每会话的已读位点 */
    static final Map<String, Long> readSeq = new LinkedHashMap<>();

    static void append(String convId, String text) {
        messages.computeIfAbsent(convId, k -> new ArrayList<>()).add(text);
        seqOf[0]++;
        System.out.printf("  追加 %s seq=%d：%s%n", convId, seqOf[0], text);
    }

    /** 增量同步：从 lastSeq 之后拉取 */
    static List<String> sync(String convId, long lastSeq) {
        List<String> all = messages.getOrDefault(convId, List.of());
        List<String> delta = new ArrayList<>();
        for (int i = (int) lastSeq; i < all.size(); i++) {
            delta.add((i + 1) + ":" + all.get(i));
        }
        return delta;
    }

    /** 多端上报已读：取最大值（幂等、只进不退） */
    static long reportRead(String userId, String convId, long seq) {
        String key = userId + "@" + convId;
        long current = readSeq.getOrDefault(key, 0L);
        long next = Math.max(current, seq);
        readSeq.put(key, next);
        System.out.printf("  %s 在 %s 上报已读 %d -> readSeq=%d%s%n",
                userId, convId, seq, next, next == current ? "（未推进）" : "");
        return next;
    }

    static long unread(String convId, String userId) {
        long total = messages.getOrDefault(convId, List.of()).size();
        long read = readSeq.getOrDefault(userId + "@" + convId, 0L);
        return Math.max(0, total - read);
    }

    public static void main(String[] args) {
        System.out.println("【消息写入（服务端分配 seq）】");
        append("c-1", "你好");
        append("c-1", "在吗");
        append("c-1", "今天发货吗");

        System.out.println();
        System.out.println("【增量同步：客户端已拉到 seq=1】");
        System.out.println("  delta=" + sync("c-1", 1));

        System.out.println();
        System.out.println("【多端已读同步】");
        reportRead("u-1", "c-1", 2);
        reportRead("u-1", "c-1", 3);
        reportRead("u-1", "c-1", 1);            // 旧位点不回退
        System.out.println("  当前 readSeq=" + readSeq.get("u-1@c-1"));

        System.out.println();
        System.out.println("【未读数】u-1 未读=" + unread("c-1", "u-1")
                + "  u-2 未读=" + unread("c-1", "u-2"));
        System.out.println();
        System.out.println("  关键：seq 单调 + 位点取最大 + 增量按 seq —— 多端天然一致");
    }
}
```

**课后练习**
1. 实现完整多端同步：3 个设备各自维护 lastSeq，模拟"设备 A 拉取 -> 设备 B 断线 -> B 重连拉增量"，验证不重不漏。
2. 实现"会话列表同步"：最后一条消息/未读数/置顶状态，模拟"一端删除会话"通过操作事件同步到其他端。
3. 思考题：为什么增量同步用 seq 而不是时间戳？从"时钟不同步"与"同毫秒多条消息"两个角度分析（45-07 的呼应）。

---

### 65-06 群聊扇出：写扩散与读扩散

**知识点**
- **群聊的核心问题是"一条消息发给谁、存几份"**：**写扩散（Write Fanout）**——消息写入每个成员的收件箱（N 份），读时只读自己的收件箱（快）；**读扩散（Read Fanout）**——只存一份在群会话里，读时按会话拉取（省空间，但拉取要处理"我在这个群"的权限与分页）。
- **写扩散的代价**：500 人群 -> 1 条消息 500 份写入（**写放大**）；**大群（万人群）写扩散直接压垮存储**——所以大群通常用"读扩散 + 成员级已读"。
- **读扩散的代价**：读时要"聚合我加入的所有群的最近消息"（会话列表），**拉取历史要按 seq 分页**；**已读位点按会话存**（每个成员一个 readSeq）。
- **混合方案（工业主流）**：**小群写扩散**（成员少，读取快）、**大群读扩散**（成员多，写入省）——**按群规模阈值切换**；**会话列表用"最后消息 + 未读数"的轻量索引**（不展开全部消息）。
- **与 54-02 配额/62-01 分区的类比**：扇出是"消息的路由与复制策略"——**与广播查询 vs 单分片查询的取舍同构**（39-05）。
- 易错点：所有群都用写扩散（大群写放大爆炸）；读扩散不做会话列表索引（每次打开 App 要扫全部群）；群成员变更不同步（新成员看到旧消息或看不到）；已读位点按成员不按会话（群内已读混乱）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GroupFanout {
    /** 会话存储：群消息只存一份（读扩散基础） */
    static final Map<String, List<String>> groupMessages = new LinkedHashMap<>();
    /** 收件箱：userId -> 消息引用列表（写扩散） */
    static final Map<String, List<String>> inboxes = new LinkedHashMap<>();

    static final int WRITE_FANOUT_THRESHOLD = 200;   // 成员数阈值

    static void sendToGroup(String gid, List<String> members, String text) {
        groupMessages.computeIfAbsent(gid, k -> new ArrayList<>()).add(text);
        int seq = groupMessages.get(gid).size();
        boolean writeFanout = members.size() <= WRITE_FANOUT_THRESHOLD;
        if (writeFanout) {
            for (String m : members) {
                inboxes.computeIfAbsent(m, k -> new ArrayList<>())
                        .add(gid + "#" + seq + ":" + text);
            }
            System.out.printf("  群 %s（%d 人）-> 写扩散：1 条消息写 %d 份收件箱%n",
                    gid, members.size(), members.size());
        } else {
            System.out.printf("  群 %s（%d 人）-> 读扩散：只存 1 份，读时按成员聚合%n",
                    gid, members.size());
        }
    }

    /** 读：小群读收件箱，大群读群会话（简化：都按群拉取） */
    static List<String> readGroup(String gid, String userId, List<String> members,
                                  int limit) {
        if (!members.contains(userId)) {
            return List.of("403 非群成员");
        }
        List<String> all = groupMessages.getOrDefault(gid, List.of());
        int from = Math.max(0, all.size() - limit);
        List<String> out = new ArrayList<>();
        for (int i = from; i < all.size(); i++) {
            out.add((i + 1) + ":" + all.get(i));
        }
        return out;
    }

    static void compareCost(int members, int msgCount) {
        System.out.printf("  群 %d 人 × %d 条消息：%n", members, msgCount);
        System.out.printf("    写扩散存储 = %d 条（每成员每消息一份）%n", members * msgCount);
        System.out.printf("    读扩散存储 = %d 条（每群一份）%n", msgCount);
        System.out.printf("    写放大倍数 = %d×%n", members);
    }

    public static void main(String[] args) {
        List<String> small = List.of("u-1", "u-2", "u-3");
        List<String> big = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            big.add("u-" + i);
        }
        System.out.println("【小群（3 人）】");
        sendToGroup("g-small", small, "晚上开会");
        System.out.println("  收件箱 u-1：" + inboxes.get("u-1"));
        System.out.println("  读群消息：" + readGroup("g-small", "u-1", small, 10));

        System.out.println();
        System.out.println("【大群（5000 人）】");
        sendToGroup("g-big", big, "全员公告");
        System.out.println("  读群消息（u-0）：" + readGroup("g-big", "u-0", big, 10));
        System.out.println("  非成员读取：" + readGroup("g-big", "u-x", big, 10));

        System.out.println();
        System.out.println("【存储成本对比】");
        compareCost(3, 100);
        compareCost(5000, 100);
        System.out.println();
        System.out.println("  阈值策略：<=200 人写扩散（读快），>200 人读扩散（写省）");
    }
}
```

**课后练习**
1. 实现扇出策略切换：按成员数选择写/读扩散，用 3 人群与 5000 人群各发 100 条消息，输出存储量与读取路径。
2. 实现"群成员变更"：新成员入群后只能看到入群后的消息，退群后不能再读，验证权限边界。
3. 思考题：为什么"大群读扩散"需要额外的会话列表索引？从"打开 App 时如何快速展示最近会话"分析。

---

### 65-07 离线消息与推送

**知识点**
- **离线消息的本质是"暂存 + 补发"**：接收方不在线时，消息已落库（STORED），**不推送**；**用户上线时按会话 seq 拉取增量**——**所以"离线消息"不是单独的消息类型，而是"未 ACK 的消息"**（65-04 的状态机）。
- **两种取回方式**：**上线时主动拉取**（客户端发 `sync(lastSeq)`，最可靠）与**系统推送唤醒**（APNs/FCM 推通知，用户点开 App 再拉）——**推送只是"叫醒"，不是"投递"**（推送可能丢，拉取才是事实）。
- **离线消息的保留策略**：按时间（7 天）与条数（每会话 1000 条）双限制——**超出后只保留"未读汇总"**（提示"你有 N 条未读，更早的消息请在历史中查看"）；**存储上要与"消息漫游"区分**（65-08：漫游是全量历史，离线是"待补发"）。
- **推送的降级与合并**：同一会话短时间多条离线消息 -> **合并成一条推送**（避免轰炸）；**免打扰/静音**的会话不推送（只更新未读数）；**推送失败的兜底**是用户下次打开 App 主动拉取。
- **与 28/62 章的关系**：离线消息 = "未被消费的消息"，与 MQ 的 lag 同构；**保留策略 = 62-08 的 retention**。
- 易错点：把推送当投递（推送失败就以为消息丢了）；离线消息无限堆积（存储爆炸）；多条离线消息逐条推送（用户体验灾难）；无"已读汇总"（用户不知道有多少未读）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OfflineMessages {
    record Msg(long seq, String convId, String from, String text, long atMs) {
    }

    /** 每用户每会话的消息流水（服务端存储） */
    static final Map<String, List<Msg>> store = new LinkedHashMap<>();
    /** 每用户每会话的已读位点（65-05） */
    static final Map<String, Long> readSeq = new LinkedHashMap<>();
    /** 在线状态 */
    static final Map<String, Boolean> online = new LinkedHashMap<>();

    static void receive(String toUser, String convId, String from, String text, long now) {
        List<Msg> list = store.computeIfAbsent(toUser + "@" + convId, k -> new ArrayList<>());
        long seq = list.size() + 1;
        list.add(new Msg(seq, convId, from, text, now));
        if (Boolean.TRUE.equals(online.get(toUser))) {
            System.out.printf("  %s 在线 -> 实时推送 seq=%d%n", toUser, seq);
        } else {
            System.out.printf("  %s 离线 -> 暂存 seq=%d（等上线拉取）%n", toUser, seq);
        }
    }

    /** 上线拉取：按 seq 增量 + 保留策略 */
    static void login(String user, int maxPerConv) {
        online.put(user, true);
        System.out.println("  " + user + " 上线 -> 拉取增量");
        store.forEach((key, list) -> {
            if (!key.startsWith(user + "@")) {
                return;
            }
            String convId = key.substring(key.indexOf('@') + 1);
            long last = readSeq.getOrDefault(key, 0L);
            List<Msg> delta = new ArrayList<>();
            for (Msg m : list) {
                if (m.seq() > last) {
                    delta.add(m);
                }
            }
            if (delta.isEmpty()) {
                return;
            }
            if (delta.size() > maxPerConv) {
                System.out.printf("    会话 %s：未读 %d 条，超出上限 %d -> 只返回最新 %d 条 + 未读汇总%n",
                        convId, delta.size(), maxPerConv, maxPerConv);
                delta = delta.subList(delta.size() - maxPerConv, delta.size());
            }
            System.out.printf("    会话 %s：补发 %d 条 -> %s%n", convId, delta.size(),
                    delta.stream().map(Msg::text).toList());
            readSeq.put(key, list.get(list.size() - 1).seq());
        });
    }

    /** 推送合并：同会话多条离线只推一条 */
    static void pushNotify(String user) {
        int totalUnread = 0;
        int convCount = 0;
        for (Map.Entry<String, List<Msg>> e : store.entrySet()) {
            if (!e.getKey().startsWith(user + "@")) {
                continue;
            }
            long last = readSeq.getOrDefault(e.getKey(), 0L);
            long unread = e.getValue().stream().filter(m -> m.seq() > last).count();
            if (unread > 0) {
                convCount++;
                totalUnread += unread;
            }
        }
        if (totalUnread == 0) {
            System.out.println("  无未读，不推送");
            return;
        }
        if (convCount == 1) {
            System.out.printf("  推送：你有 %d 条新消息%n", totalUnread);
        } else {
            System.out.printf("  推送（合并）：你有 %d 个会话共 %d 条新消息%n", convCount, totalUnread);
        }
    }

    public static void main(String[] args) {
        online.put("u-2", false);
        System.out.println("【消息到达】");
        for (int i = 1; i <= 5; i++) {
            receive("u-2", "c-1", "u-1", "msg-" + i, i * 1000L);
        }
        receive("u-2", "c-2", "u-3", "另一会话", 6000L);

        System.out.println();
        System.out.println("【推送（合并）】");
        pushNotify("u-2");

        System.out.println();
        System.out.println("【上线拉取】");
        login("u-2", 3);

        System.out.println();
        System.out.println("【再次上线：已无未读】");
        login("u-2", 3);
        System.out.println();
        System.out.println("  结论：推送只负责叫醒，拉取才是投递的事实来源");
    }
}
```

**课后练习**
1. 实现离线消息完整流程：暂存/上线拉取/保留上限/未读汇总，用 100 条离线消息验证"超限只补最新 + 汇总提示"。
2. 实现推送合并与静音：会话免打扰时不推送只累加未读，验证"3 个会话 10 条消息只推 1 条"。
3. 思考题：为什么"推送失败不等于消息丢失"？从"推送只叫醒、拉取是事实源"的设计分离分析。

---

### 65-08 消息存储与漫游

**知识点**
- **消息存储的三层需求**：**在线推送**（最新消息，内存/缓存）、**离线补发**（未 ACK 消息，按会话 seq）、**历史漫游**（全量历史，分页查询）——**三者共用一份底层存储**（消息表），只是查询维度不同。
- **存储模型的关键决策**：**按会话分片**（conversationId 作为分片键，同会话消息物理相邻，`WHERE convId=? ORDER BY seq DESC LIMIT 20` 极快 39-05）；**row key 设计**（convId + seq 作为主键，天然有序）；**冷热分离**（近 3 个月热存，更早归档/对象存储 39-10）。
- **消息与"会话列表"的分离**：**会话列表是派生数据**（最后一条消息/未读数/时间），**单独存一张轻量表**（每用户每会话一行）——**否则每次打开 App 要扫全部消息表**。
- **漫游的权限与合规**：**消息属于会话成员**（不是单方数据，55-04 双键校验）；**撤回/删除**（用户删除只影响自己视图，服务端保留用于合规或按策略真删 68-10）；**加密存储**（68-04）。
- **与 39/46 章的对照**：消息表是"高写入 + 按会话范围查询"——**分片键选 convId**（不是 userId，因为群聊消息要共享）；**历史查询走分片内 seq 范围**；**全量分析走 CDC 到数仓**（46 章）。
- 易错点：按 userId 分片（群消息要写 N 份，写放大 65-06）；会话列表实时聚合消息表（慢）；历史删除只删客户端（服务端无限涨）；消息无 TTL（存储成本失控）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessageStorage {
    record StoredMsg(String convId, long seq, String from, String payload, long atMs) {
    }

    /** 分片：convId 哈希到 N 个分片 */
    static final int SHARDS = 4;
    static final List<Map<String, List<StoredMsg>>> shards = new ArrayList<>();

    static {
        for (int i = 0; i < SHARDS; i++) {
            shards.add(new LinkedHashMap<>());
        }
    }

    static int shardOf(String convId) {
        return Math.abs(convId.hashCode()) % SHARDS;
    }

    static void append(String convId, String from, String payload, long atMs) {
        int shard = shardOf(convId);
        List<StoredMsg> list = shards.get(shard)
                .computeIfAbsent(convId, k -> new ArrayList<>());
        long seq = list.size() + 1;
        list.add(new StoredMsg(convId, seq, from, payload, atMs));
        System.out.printf("  写入 conv=%-6s seq=%-3d -> 分片%d（共 %d 条）%n",
                convId, seq, shard, list.size());
    }

    /** 分片内按 seq 倒序取最近 N 条（无需跨分片） */
    static List<StoredMsg> recent(String convId, int limit) {
        List<StoredMsg> list = shards.get(shardOf(convId)).getOrDefault(convId, List.of());
        int from = Math.max(0, list.size() - limit);
        return list.subList(from, list.size());
    }

    /** 历史漫游分页：按 seq 游标（39-03 的游标分页） */
    static List<StoredMsg> history(String convId, long beforeSeq, int limit) {
        List<StoredMsg> list = shards.get(shardOf(convId)).getOrDefault(convId, List.of());
        List<StoredMsg> out = new ArrayList<>();
        for (StoredMsg m : list) {
            if (m.seq() < beforeSeq) {
                out.add(m);
            }
        }
        int from = Math.max(0, out.size() - limit);
        return out.subList(from, out.size());
    }

    static void conversationList(String userId, List<String> convIds) {
        System.out.println("【会话列表（派生数据，单独存储）】");
        for (String convId : convIds) {
            List<StoredMsg> recent = recent(convId, 1);
            long total = shards.get(shardOf(convId)).getOrDefault(convId, List.of()).size();
            System.out.printf("  %s 用户=%s 最后一条=%s 总数=%d%n", convId, userId,
                    recent.isEmpty() ? "无" : recent.get(0).payload(), total);
        }
        System.out.println("  真实系统：这张表每用户每会话一行（最后消息/未读数/时间），不扫消息表");
    }

    public static void main(String[] args) {
        System.out.println("【按会话分片写入】");
        for (int i = 1; i <= 5; i++) {
            append("c-1", "u-1", "消息" + i, i * 1000L);
        }
        append("c-2", "u-3", "另一会话", 6000L);

        System.out.println();
        System.out.println("【最近 2 条（分片内查询）】");
        recent("c-1", 2).forEach(m -> System.out.printf("  seq=%d %s%n", m.seq(), m.payload()));

        System.out.println();
        System.out.println("【历史漫游：seq<4 的最近 2 条】");
        history("c-1", 4, 2).forEach(m -> System.out.printf("  seq=%d %s%n", m.seq(), m.payload()));

        System.out.println();
        conversationList("u-1", List.of("c-1"));
        System.out.println();
        System.out.println("  分片键=convId：同会话物理相邻；群消息只存一份（65-06 读扩散）");
    }
}
```

**课后练习**
1. 实现消息存储：convId 分片 + 最近 N 条 + seq 游标分页，用 1000 条消息验证"查询只碰一个分片"。
2. 实现会话列表表：每用户每会话一行（最后消息 seq/未读数/时间），验证"打开 App 只查这张表"。
3. 思考题：为什么分片键选 convId 而不是 userId？从"群消息只存一份"与"跨用户共享"两个角度分析（65-06 的呼应）。

---

### 65-09 在线状态与 Presence

**知识点**
- **Presence（在线状态）是"高频变更 + 低价值读"的数据**：用户在线/离线/忙碌/离开，状态变化频繁（每次登录/登出/切后台），**读取方多但一致性要求低**（看到几秒前的状态可接受）。
- **状态的三级粒度**：**全局在线**（是否登录）、**设备级**（手机在线/Pad 离线）、**活跃度**（前台活跃/后台挂起）——**业务通常只需要"全局 + 最近活跃时间"**。
- **实现方式**：**连接注册表推导**（有活跃连接即在线，65-02）+ **显式状态上报**（用户手动设"忙碌"）+ **TTL 过期**（心跳超时自动转离线）——**推导 + 覆盖 + 过期**三件套。
- **广播风暴是 Presence 的经典问题**：10 万在线用户互相订阅状态，每次状态变更广播给所有好友——**必须限制**：只订阅"当前可见的会话/好友"、批量合并（1 秒内的变更合并一次）、**只推给"最近有交互"的联系人**。
- **不持久化 vs 持久化**：在线状态放 Redis（TTL 自动过期，55-05 配额思想的存储版）；**最近活跃时间持久化**（用于"3 天前活跃"展示）；**手动状态（忙碌/勿扰）持久化**（用户设置）。
- 易错点：状态变更全量广播（广播风暴）；用数据库存在线状态（高频写打爆 DB）；状态无 TTL（进程崩溃永远"在线"）；把在线状态当强一致数据（不值得）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PresenceService {
    /** 在线状态：userId -> (状态, 最后心跳, 手动状态) */
    record Presence(String status, long lastHeartbeatMs, String manualStatus) {
        boolean online(long now, long ttlMs) {
            return now - lastHeartbeatMs <= ttlMs;
        }
    }

    static final Map<String, Presence> presence = new LinkedHashMap<>();
    static final long TTL = 60_000;

    static void heartbeat(String userId, long now) {
        Presence p = presence.getOrDefault(userId, new Presence("online", now, null));
        presence.put(userId, new Presence("online", now, p.manualStatus()));
        System.out.printf("  %s 心跳 -> 在线%n", userId);
    }

    static void setManual(String userId, String manual, long now) {
        Presence p = presence.getOrDefault(userId, new Presence("online", now, null));
        presence.put(userId, new Presence(p.status(), p.lastHeartbeatMs(), manual));
        System.out.printf("  %s 手动设置 -> %s%n", userId, manual);
    }

    static void logout(String userId, long now) {
        Presence p = presence.getOrDefault(userId, new Presence("offline", now, null));
        presence.put(userId, new Presence("offline", now, p.manualStatus()));
        System.out.printf("  %s 登出 -> 离线%n", userId);
    }

    /** 查询：推导在线 + 覆盖手动状态 + 超时降级 */
    static String query(String userId, long now) {
        Presence p = presence.get(userId);
        if (p == null) {
            return "offline（无记录）";
        }
        if (p.manualStatus() != null) {
            return p.manualStatus() + "（手动）";
        }
        if (!p.online(now, TTL)) {
            return "offline（心跳超时，最后活跃 " + (now - p.lastHeartbeatMs()) / 1000 + "s 前）";
        }
        return "online";
    }

    /** 批量订阅：只查当前可见的好友，且合并变更（1s 窗口） */
    static void batchQuery(List<String> visible, long now) {
        System.out.println("  批量查询（可见 " + visible.size() + " 人）：");
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String u : visible) {
            String s = query(u, now).split("（")[0];
            byStatus.merge(s, 1, Integer::sum);
        }
        System.out.println("    " + byStatus + "（一次批量，不逐个广播）");
    }

    public static void main(String[] args) {
        long now = 1_000_000;
        System.out.println("【状态流转】");
        heartbeat("u-1", now);
        setManual("u-1", "忙碌", now);
        heartbeat("u-2", now);
        logout("u-3", now);
        System.out.println();

        System.out.println("【查询】");
        System.out.println("  u-1：" + query("u-1", now + 10_000));
        System.out.println("  u-2：" + query("u-2", now + 10_000));
        System.out.println("  u-3：" + query("u-3", now + 10_000));
        System.out.println("  u-9：" + query("u-9", now));
        System.out.println();

        System.out.println("【心跳超时自动降级】");
        System.out.println("  u-2 @+70s：" + query("u-2", now + 70_000));
        System.out.println();

        batchQuery(List.of("u-1", "u-2", "u-3", "u-9"), now + 70_000);
        System.out.println();
        System.out.println("  三件套：连接推导 + 手动覆盖 + TTL 过期；广播要按可见范围批量");
    }
}
```

**课后练习**
1. 实现 Presence 服务：心跳刷新/手动状态/登出/TTL 过期，用 4 个用户跑出各状态与降级。
2. 实现"广播限流"：状态变更只推给"最近 7 天有会话"的联系人，并合并 1 秒内的多次变更，量化推送量下降。
3. 思考题：为什么在线状态适合放 Redis 而不放 MySQL？从"高频写 + TTL 自动过期 + 可容忍丢失"三个特征分析。

---

### 65-10 弱网与移动端优化

**知识点**
- **移动端 IM 的网络特征**：**频繁切换网络**（WiFi/4G/5G）、**信号抖动**（延迟与丢包）、**系统杀后台**（连接被冻结）、**省电策略**（长连接被系统限制）——**"连接稳定"在移动端是奢侈品**，客户端必须处理"随时断开"。
- **弱网优化的五个手段**：**断线重连**（指数退避 + 抖动，40-02）、**增量同步**（重连后按 seq 拉，65-05）、**消息合并**（多条离线合并拉取）、**本地缓存**（已读消息本地存，弱网时先展示）、**发送队列**（离线时消息进队列，恢复后按序发送）。
- **发送队列的状态机**：`PENDING（本地待发）-> SENDING（已发出待 ACK）-> SENT（已 ACK）-> FAILED（重试超限）`——**客户端必须有"发送中/失败/重发"的 UI 状态**（用户知道消息到底发出去没有）。
- **重连的防雪崩**：**十万客户端同时重连会打爆网关**——服务端要**限流新连接**（网关容量保护），客户端要**随机退避**（不是所有客户端 1 秒后同时重连）；**连接数恢复要有梯度**。
- **与 47-02/50 章的对应**：长连接是"资源"，重连是"资源重建"——**重连风暴与重试风暴同构**（44-08 的退避与抖动）。
- 易错点：断线后不补偿（消息丢失）；重连立即拉全量历史（流量爆炸）；发送失败无 UI 提示（用户以为发出去了）；重连无退避（重启网关即全站雪崩）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WeakNetwork {
    enum SendState {
        PENDING, SENDING, SENT, FAILED
    }

    record Outgoing(String clientMsgId, String text, SendState state, int attempts) {
        Outgoing move(SendState next, int attempts) {
            return new Outgoing(clientMsgId, text, next, attempts);
        }
    }

    /** 发送队列推进：成功则 SENT，失败重试，超限 FAILED */
    static void drain(List<Outgoing> queue, boolean networkUp, int maxAttempts) {
        for (int i = 0; i < queue.size(); i++) {
            Outgoing o = queue.get(i);
            if (o.state() == SendState.SENT || o.state() == SendState.FAILED) {
                continue;
            }
            if (!networkUp) {
                queue.set(i, o.move(SendState.PENDING, o.attempts()));
                continue;
            }
            if (o.attempts() >= maxAttempts) {
                queue.set(i, o.move(SendState.FAILED, o.attempts()));
                continue;
            }
            boolean ack = o.attempts() < 1;             // 简化：第一次成功
            if (ack) {
                queue.set(i, o.move(SendState.SENT, o.attempts() + 1));
            } else {
                queue.set(i, o.move(SendState.SENDING, o.attempts() + 1));
            }
        }
    }

    static void printQueue(List<Outgoing> queue) {
        queue.forEach(o -> System.out.printf("  %-10s %-8s attempts=%d %s%n",
                o.clientMsgId(), o.state(), o.attempts(), o.text()));
    }

    static void reconnectStorm() {
        System.out.println("【重连风暴的两种策略】");
        long base = 1_000;
        System.out.println("  无退避：10 万客户端全部 1s 后重连 -> 网关瞬时 10 万连接");
        List<Long> delays = new ArrayList<>();
        for (int attempt = 1; attempt <= 5; attempt++) {
            delays.add(reconnectDelayJitter(attempt, base, attempt * 137L));
        }
        System.out.println("  指数 + 抖动（示意）：" + delays + " ms");
        System.out.println("  效果：重连在时间上摊开，网关可梯度恢复");
    }

    /** 简化版重连延迟（避免移位溢出） */
    static long reconnectDelayJitter(int attempt, long baseMs, long jitterMs) {
        long exp = baseMs * (long) Math.pow(2, Math.min(attempt - 1, 5));
        return exp + jitterMs;
    }

    public static void main(String[] args) {
        System.out.println("【离线时发送：进队列 PENDING】");
        List<Outgoing> queue = new ArrayList<>();
        queue.add(new Outgoing("c-1", "消息A", SendState.PENDING, 0));
        queue.add(new Outgoing("c-2", "消息B", SendState.PENDING, 0));
        drain(queue, false, 3);
        printQueue(queue);

        System.out.println();
        System.out.println("【网络恢复：按序发送】");
        drain(queue, true, 3);
        printQueue(queue);

        System.out.println();
        System.out.println("【失败与重试】");
        List<Outgoing> retryQueue = new ArrayList<>();
        retryQueue.add(new Outgoing("c-3", "消息C", SendState.PENDING, 0));
        for (int round = 0; round < 4; round++) {
            drain(retryQueue, true, 3);
        }
        printQueue(retryQueue);

        System.out.println();
        System.out.println("【重连补偿：只拉增量】");
        System.out.println("  重连后请求 sync(lastSeq=87) -> 只补 88..N（65-05），不拉全量");
        System.out.println();
        reconnectStorm();
        System.out.println();
        System.out.println("  移动端铁律：随时会断 -> 队列 + 增量 + 退避 + UI 状态");
    }
}
```

**课后练习**
1. 实现发送队列：PENDING/SENDING/SENT/FAILED 四态 + 网络开关 + 重试上限，用 3 条消息验证"离线排队 -> 恢复发送 -> 失败提示"。
2. 实现重连退避与网关限流：模拟 1 万客户端重连，对比"无退避"与"指数+抖动"下的瞬时连接峰值。
3. 思考题：为什么"重连后不能拉全量历史"？从流量成本与拉取耗时两个角度分析，并给出增量同步的条件。

---

### 65-11 综合案例：单聊 + 群聊闭环

**知识点**
- **把本章收口成一个完整可运行的 IM 核心**：**连接**（注册 + 心跳 + 在线判定 65-02）-> **协议**（长度头帧 + 类型 65-03）-> **上行**（clientMsgId 幂等 + seq 分配 65-04/05）-> **路由**（单聊直达 / 群聊扇出 65-06）-> **投递**（在线推送 + ACK；离线暂存 65-07）-> **同步**（seq 增量 + 已读位点 65-05）-> **存储**（会话分片 + 会话列表 65-08）。
- **闭环验收的四个断言**：**不丢**（每条消息最终 DELIVERED 或进离线待补）、**不重**（clientMsgId 与 msgId 双去重）、**有序**（会话内 seq 单调）、**可同步**（任意端按 seq 拉到一致结果）。
- **与全书的关系**：连接层用 60-06 的 Netty 模型、协议用 18-01/60-07 的帧设计、投递用 28/62 的语义、分片用 39-05、心跳用 29-01、在线状态用 Redis TTL（25 章）——**IM 是"分布式系统工程"的综合演练**。
- 易错点：只做单聊不验证群聊扇出（写放大）；没有 seq（多端同步无从谈起）；ACK 缺失（丢消息不可知）；离线消息与会话列表不一致（未读数错）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImCase {
    record Msg(String msgId, String clientMsgId, long seq, String convId,
               String from, String text, int acked) {
    }

    static final Map<String, List<Msg>> convStore = new LinkedHashMap<>();   // 会话流水
    static final Map<String, List<String>> inbox = new LinkedHashMap<>();     // 用户收件箱（引用）
    static final Map<String, List<String>> userConvs = new LinkedHashMap<>();
    static final Map<String, Long> readSeq = new LinkedHashMap<>();
    static final Map<String, Boolean> online = new LinkedHashMap<>();
    static long seqCounter = 0;

    static void register(String userId) {
        online.put(userId, true);
        System.out.println("  " + userId + " 上线");
    }

    static void send(String clientMsgId, String from, String convId,
                     List<String> members, String text) {
        List<Msg> conv = convStore.computeIfAbsent(convId, k -> new ArrayList<>());
        for (Msg m : conv) {
            if (m.clientMsgId().equals(clientMsgId)) {
                System.out.println("  上行重复 clientMsgId=" + clientMsgId + " -> 忽略");
                return;
            }
        }
        seqCounter++;
        Msg msg = new Msg("m-" + seqCounter, clientMsgId, conv.size() + 1L, convId, from, text, 0);
        conv.add(msg);
        for (String member : members) {
            userConvs.computeIfAbsent(member, k -> new ArrayList<>());
            if (!userConvs.get(member).contains(convId)) {
                userConvs.get(member).add(convId);
            }
            if (!member.equals(from)) {
                inbox.computeIfAbsent(member, k -> new ArrayList<>())
                        .add(msg.msgId() + "@" + convId);
            }
            boolean on = Boolean.TRUE.equals(online.get(member));
            System.out.printf("    -> %s %s（seq=%d）%n", member,
                    on ? "实时推送" : "离线暂存", msg.seq());
        }
        System.out.println("  发送成功：" + msg.msgId() + " 会话内 seq=" + msg.seq());
    }

    /** 同步：按会话拉增量 */
    static void sync(String userId, String convId, long lastSeq) {
        List<Msg> conv = convStore.getOrDefault(convId, List.of());
        List<String> delta = new ArrayList<>();
        for (Msg m : conv) {
            if (m.seq() > lastSeq) {
                delta.add(m.seq() + ":" + m.text());
            }
        }
        System.out.printf("  %s 同步 %s（after seq=%d）-> %s%n", userId, convId, lastSeq, delta);
        readSeq.put(userId + "@" + convId, (long) conv.size());
    }

    static long unread(String userId, String convId) {
        long total = convStore.getOrDefault(convId, List.of()).size();
        long read = readSeq.getOrDefault(userId + "@" + convId, 0L);
        return Math.max(0, total - read);
    }

    public static void main(String[] args) {
        register("u-1");
        register("u-2");
        online.put("u-3", false);

        System.out.println();
        System.out.println("【单聊】");
        send("c-msg-1", "u-1", "c-1", List.of("u-1", "u-2"), "你好");
        send("c-msg-1", "u-1", "c-1", List.of("u-1", "u-2"), "你好（重复）");

        System.out.println();
        System.out.println("【群聊（3 人，其中 u-3 离线）】");
        send("c-msg-2", "u-1", "g-1", List.of("u-1", "u-2", "u-3"), "开会了");

        System.out.println();
        System.out.println("【同步与未读】");
        sync("u-2", "c-1", 0);
        sync("u-2", "g-1", 0);
        System.out.printf("  u-3 未读：c-1=%d g-1=%d（上线后按 seq 拉取）%n",
                unread("u-3", "c-1"), unread("u-3", "g-1"));

        System.out.println();
        System.out.println("【闭环断言】");
        Map<String, String> asserts = new LinkedHashMap<>();
        asserts.put("不丢", "每条消息在会话流水中，离线用户上线可拉");
        asserts.put("不重", "clientMsgId + msgId 双去重");
        asserts.put("有序", "会话内 seq 单调递增");
        asserts.put("可同步", "任意端按 lastSeq 拉增量");
        asserts.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }
}
```

**课后练习**
1. 端到端实现 IM 闭环（连接/协议/上行/路由/投递/同步/存储），用 2 单聊 + 1 群聊验证四个断言。
2. 加入"已读回执"与"多端同步"：u-2 手机读完，平板未读数归零，验证 readSeq 取最大值。
3. 思考题：为什么 IM 的"闭环断言"里没有"恰好一次"？从"至少一次 + 双去重"的工程取舍分析（65-04 的呼应）。
