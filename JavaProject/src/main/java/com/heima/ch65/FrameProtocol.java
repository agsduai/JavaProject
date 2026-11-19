/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-03
 * 课名：协议设计：消息帧与版本兼容
 * ------------------------------------------------------------
 * 知识点：
 *   - **长连接协议必须自己定义帧边界**：TCP 是字节流（18-01），**必须用"长度头 + 类型 + 载荷"**划帧——否则粘包半包无法解析；**二进制协议**（紧凑、省流量、解析快）是 IM 主流，JSON 文本协议（易调试）用于内部或低频场景。
 *   - **帧结构设计**：`magic（魔数，识别协议）| version（协议版本）| type（帧类型）| flags（压缩/加密标记）| length（载荷长度）| payload | checksum（可选）`——**魔数用于快速区分非法连接**，版本用于**灰度升级**。
 *   - **帧类型**：握手、心跳、单聊消息、群消息、ACK、已读回执、拉取历史、踢下线——**用 type 区分业务，而不是在 payload 里塞不同 JSON 结构**（解析路径清晰）。
 *   - **版本兼容**：**协议版本只在"不兼容变更"时升级**（字段语义改变）；**新增字段用"可选字段 + 忽略未知"**；**服务端要同时支持 N 与 N-1 版本**（65-05 的灰度思想）。
 *   - **与 18-01/60-07 的对应**：18-01 的长度头协议是手工版，60-07 的 `LengthFieldBasedFrameDecoder` 是 Netty 实现——**本章把帧的语义层设计完整**。
 *   - 易错点：只用分隔符（消息体含分隔符就崩）；不做版本号（升级即全量不兼容）；payload 用 Java 原生序列化（36-08 的反序列化漏洞 + 跨语言困难）；帧无长度上限（恶意大包打爆内存）。
 * ============================================================
 */

package com.heima.ch65;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展帧协议：加入 flags（压缩/加密位）与 checksum，验证"压缩位开启时 payload 解压后一致"。
 * 2. 实现"未知字段忽略"的兼容解码：v3 帧带额外字段，v2 解析器忽略而不报错。
 * 3. 思考题：为什么协议帧要设"最大载荷"？从内存攻击与慢速攻击（slowloris）两个角度分析。
 * ============================================================
 */
