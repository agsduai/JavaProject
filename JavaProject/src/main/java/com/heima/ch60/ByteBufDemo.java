/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-07
 * 课名：Netty：ByteBuf 与零拷贝
 * ------------------------------------------------------------
 * 知识点：
 *   - **ByteBuf 是 Netty 的数据容器**：**读写双指针**（`readerIndex`/`writerIndex`），读不移动数据、写不覆盖未读数据——**对比 JDK ByteBuffer 的单指针**（读要 flip）；动态扩容、池化复用（`PooledByteBufAllocator` 是默认，减少 GC）。
 *   - **引用计数（Reference Counting）**：ByteBuf 用 `retain()/release()` 管理生命周期，**归零即归还池**——**忘记 release = 内存泄漏**（Netty 最经典的坑）；`SimpleChannelInboundHandler` 会自动释放，`ChannelInboundHandlerAdapter` 需要手动。
 *   - **Netty 的"零拷贝"是三个不同层次的东西**：① **堆外内存**（DirectBuffer，避免 JVM 堆与内核缓冲区之间的一次拷贝）；② **`CompositeByteBuf`**（多个 buffer 逻辑拼接，不物理复制）；③ **`FileRegion`/`transferTo`**（发送文件时内核态直接拷贝，不经用户态）——**面试常问的"零拷贝"多指②③，而不是"完全没有拷贝"**。
 *   - **池化的收益与风险**：池化（复用 buffer）降低 GC 与分配开销，但**泄漏的代价更大**（池被占满后分配阻塞/失败）；Netty 提供 `ResourceLeakDetector`（`-Dio.netty.leakDetection.level=paranoid`）检测泄漏。
 *   - **编解码（Codec）**：`LengthFieldBasedFrameDecoder` 处理**粘包半包**（18-01 的长度头协议正是它的手工版）；`StringDecoder/StringEncoder` 做文本转换——**编解码器也是有状态的，不能随意 `@Sharable`**。
 *   - 易错点：忘记 `release()`（泄漏检测日志刷屏）；把 ByteBuf 交给其他线程使用（线程不安全）；用 `ByteBuf.array()`（堆外缓冲没有 array，抛异常）；以为写入一定立即发出（60-06 的异步写）。
 * ============================================================
 */

package com.heima.ch60;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ByteBufDemo {
    /** 极简 ByteBuf：双指针 + 引用计数 */
    static class MiniByteBuf {
        final byte[] data = new byte[64];
        int readerIndex = 0;
        int writerIndex = 0;
        int refCnt = 1;
        final String name;

        MiniByteBuf(String name) {
            this.name = name;
        }

        void writeBytes(byte[] bytes) {
            System.arraycopy(bytes, 0, data, writerIndex, bytes.length);
            writerIndex += bytes.length;
        }

        byte[] readBytes(int len) {
            byte[] out = new byte[len];
            System.arraycopy(data, readerIndex, out, 0, len);
            readerIndex += len;
            return out;
        }

        int readableBytes() {
            return writerIndex - readerIndex;
        }

        void retain() {
            refCnt++;
            System.out.println("    " + name + " retain -> refCnt=" + refCnt);
        }

        boolean release() {
            refCnt--;
            System.out.println("    " + name + " release -> refCnt=" + refCnt);
            if (refCnt == 0) {
                System.out.println("    " + name + " 归还池（可复用）");
                return true;
            }
            return false;
        }
    }

    /** 粘包/半包：长度头协议的解码（18-01 的 Netty 实现版） */
    static void decodeByLengthField(List<String> frames, String wire) {
        System.out.println("  线上字节流（含长度头）：" + wire);
        int i = 0;
        while (i + 4 <= wire.length()) {
            int len = Integer.parseInt(wire.substring(i, i + 4));
            i += 4;
            if (i + len > wire.length()) {
                System.out.println("  剩余 " + (wire.length() - i) + " 字节不足一帧 -> 等待更多数据（半包）");
                break;
            }
            frames.add(wire.substring(i, i + len));
            i += len;
        }
        System.out.println("  解析出的帧：" + frames);
    }

    public static void main(String[] args) {
        var buf = new MiniByteBuf("buf-1");
        buf.writeBytes("HELLO".getBytes());
        System.out.println("  写入后 writer=" + buf.writerIndex + " readable=" + buf.readableBytes());
        byte[] read = buf.readBytes(2);
        System.out.println("  读取 2 字节：" + new String(read) + "，剩余 readable=" + buf.readableBytes());
        System.out.println("  注意：读不移动数据，只挪 readerIndex（省去 flip）");
        System.out.println();
        buf.retain();
        buf.release();
        buf.release();
        System.out.println();
        List<String> frames = new ArrayList<>();
        System.out.println("【长度头解码（粘包/半包）】");
        decodeByLengthField(frames, "0005HELLO0005WORLD0003AB");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展 MiniByteBuf：实现 `slice()`（共享底层数组的视图）与 `composite(ByteBuf...)`（逻辑拼接不复制），验证二者零拷贝语义。
 * 2. 实现"引用计数泄漏检测"：每次分配记录，作业结束对比"分配数 - 释放数"，非零即泄漏并打印分配点。
 * 3. 思考题：为什么"池化 buffer 的泄漏"比"不池化"更严重？从池容量耗尽后的分配阻塞与故障放大分析。
 * ============================================================
 */
