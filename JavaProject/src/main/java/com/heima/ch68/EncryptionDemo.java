/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-04
 * 课名：加密与密钥管理
 * ------------------------------------------------------------
 * 知识点：
 *   - **加密的三个层次**：**传输加密**（TLS，35-07）、**存储加密**（静态加密：磁盘/表空间/字段级）、**应用层加密**（字段级 + 密钥自管）——**敏感字段（L3/L4）建议应用层加密**（不依赖 DBA 与云厂商）。
 *   - **算法选择**：**对称加密 AES-GCM**（快，适合大数据量，**必须带认证**，防篡改）；**非对称 RSA/EC**（密钥交换/签名，慢，不适合大数据）；**哈希+盐**（密码存储，36-06）——**"自己发明的加密"一律不可用**（36-01）。
 *   - **密钥管理的四要素**：**密钥与数据分离**（密钥在 KMS/Vault，不在数据库/代码库）、**分级密钥**（主密钥加密数据密钥（DEK），DEK 加密数据——信封加密）、**轮换**（定期换 DEK，主密钥更长周期）、**可销毁**（密钥销毁 = 数据不可恢复，用于"加密擦除" 68-10）。
 *   - **信封加密（Envelope Encryption）的流程**：数据用 DEK 加密 -> DEK 用主密钥（KMS 托管）加密 -> 加密后的 DEK 与数据一起存 -> 解密时先解 DEK 再解数据——**这样轮换主密钥不需要重加密全部数据**。
 *   - **与 36-06 的关系**：36-06 讲"密码要慢哈希"，本章讲"数据要可逆加密"——**场景不同，算法不同**（密码不需要解密，数据需要）。
 *   - 易错点：用 ECB 模式（相同明文出相同密文，可分析）；IV 复用（GCM 下复用 IV 会导致密钥泄露）；密钥硬编码/放代码库（36 章）；加密后无法检索（需要"盲索引"设计）。
 * ============================================================
 */

package com.heima.ch68;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class EncryptionDemo {
    /** AES-GCM 加密：返回 (iv + 密文) 的 Base64 */
    static String encrypt(String plain, SecretKey key) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ct = cipher.doFinal(plain.getBytes("UTF-8"));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    static String decrypt(String packed, SecretKey key) throws Exception {
        byte[] all = Base64.getDecoder().decode(packed);
        byte[] iv = new byte[12];
        System.arraycopy(all, 0, iv, 0, 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(all, 12, all.length - 12), "UTF-8");
    }

    /** 模拟 KMS：主密钥池 + DEK 池（信封加密） */
    static final Map<String, SecretKey> masterKeys = new LinkedHashMap<>();
    static final Map<String, String> encryptedDeks = new LinkedHashMap<>();
    static SecretKey currentDek;

    static void initKms() throws Exception {
        masterKeys.put("master-v1", generate());
        masterKeys.put("master-v2", generate());
        currentDek = generate();
        encryptedDeks.put("dek-v1", wrap(currentDek, masterKeys.get("master-v1")));
        System.out.println("  KMS 初始化：主密钥 v1，DEK v1 已用主密钥包裹");
    }

    static SecretKey generate() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        return kg.generateKey();
    }

    /** 包裹 DEK：用主密钥加密 DEK（真实系统用 AES-KW/RFC 3394 专用包裹算法，此处用 AES 简化） */
    static String wrap(SecretKey dek, SecretKey master) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, master);
        return Base64.getEncoder().encodeToString(cipher.doFinal(dek.getEncoded()));
    }

    static SecretKey unwrap(String wrapped, SecretKey master) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, master);
        byte[] raw = cipher.doFinal(Base64.getDecoder().decode(wrapped));
        return new SecretKeySpec(raw, "AES");
    }

    static void envelopeDemo() throws Exception {
        System.out.println("【信封加密】");
        String phone = "13800001234";
        String cipherText = encrypt(phone, currentDek);
        System.out.println("  明文：" + phone);
        System.out.println("  密文（iv+ct base64）：" + cipherText);
        System.out.println("  解密：" + decrypt(cipherText, currentDek));

        System.out.println();
        System.out.println("【密文不可分析】");
        String c1 = encrypt(phone, currentDek);
        String c2 = encrypt(phone, currentDek);
        System.out.println("  同一明文两次加密结果不同（随机 IV）-> " + (!c1.equals(c2)));

        System.out.println();
        System.out.println("【篡改检测（GCM 认证）】");
        byte[] tampered = Base64.getDecoder().decode(cipherText);
        tampered[tampered.length - 1] ^= 0x01;
        try {
            decrypt(Base64.getEncoder().encodeToString(tampered), currentDek);
            System.out.println("  篡改未被发现（不应该发生）");
        } catch (Exception e) {
            System.out.println("  篡改被拒绝：" + e.getClass().getSimpleName()
                    + "（GCM 认证标签校验失败）");
        }
    }

    static void keyRotation() {
        System.out.println();
        System.out.println("【密钥轮换：只换 DEK，不重加密全量数据】");
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put("1", "生成新 DEK v2，用主密钥包裹");
        steps.put("2", "新写入用 DEK v2；旧数据解密仍用 DEK v1");
        steps.put("3", "后台任务逐步用 v2 重加密旧数据（可分批）");
        steps.put("4", "全部迁移完成后销毁 DEK v1");
        steps.forEach((k, v) -> System.out.printf("  %s. %s%n", k, v));
        System.out.println();
        System.out.println("【密钥管理四原则】");
        System.out.println("  分离（KMS 托管）/ 分级（主密钥+DEK）/ 轮换（定期）/ 可销毁（加密擦除）");
    }

    public static void main(String[] args) throws Exception {
        initKms();
        System.out.println();
        envelopeDemo();
        keyRotation();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 AES-GCM 加解密（含随机 IV 与认证校验），验证"同明文不同密文"与"篡改被拒"。
 * 2. 实现完整的 DEK 轮换流程（新旧 DEK 并存 -> 逐步重加密 -> 销毁旧 DEK），用 10 条数据验证迁移过程可读。
 * 3. 思考题：为什么"密钥销毁"等同于"数据销毁"？从加密擦除（Crypto-shredding）与物理介质不可控的角度分析。
 * ============================================================
 */
