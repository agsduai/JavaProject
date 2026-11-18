/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-07
 * 课名：Spring Data Redis
 * ------------------------------------------------------------
 * 知识点：
 *   - 依赖：`spring-boot-starter-data-redis`，底层默认客户端是 Lettuce（基于 Netty，线程安全、支持集群）。
 *   - 核心 API：`RedisTemplate`（对象操作，需配置序列化器）、`StringRedisTemplate`（key/value 都是 String，最常用）。
 *   - 序列化器很关键：默认 `JdkSerializationRedisSerializer` 会把 key 写成带类名的乱码；生产一般配 `StringRedisSerializer` 做 key、`GenericJackson2JsonRedisSerializer` 做 value。
 *   - 常用操作：`opsForValue()` / `opsForHash()` / `opsForList()` / `opsForSet()` / `opsForZSet()`，命名与 Redis 命令一一对应。
 *   - 配置：`spring.data.redis.host/port/password/database/timeout`，连接池用 `lettuce.pool.*`。
 *   - 缓存抽象：`@EnableCaching` + `@Cacheable/@CachePut/@CacheEvict`，用 `RedisCacheManager` 指定 TTL 与 key 前缀，避免手写 `get/set` 样板代码。
 *   - 易错：`RedisTemplate` 直接注入后不配序列化器，用 `redis-cli` 看 key 是一串 `\xac\xed`；`@Cacheable` 同类内部调用不生效（代理失效，同 AOP 原理）。
 * ============================================================
 */

package com.heima.ch25;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class SpringDataRedisDemo {
    public static void main(String[] args) {
        System.out.println("=== 依赖与配置 ===");
        System.out.println("""
                <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-data-redis</artifactId>
                </dependency>

                spring:
                  data:
                    redis:
                      host: 127.0.0.1
                      port: 6379
                      password: 123456
                      database: 0
                      lettuce:
                        pool:
                          max-active: 8
                          max-idle: 8
                """);

        System.out.println("=== 序列化器配置 ===");
        System.out.println("""
                @Bean
                public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
                    RedisTemplate<String, Object> tpl = new RedisTemplate<>();
                    tpl.setConnectionFactory(factory);
                    tpl.setKeySerializer(new StringRedisSerializer());
                    tpl.setHashKeySerializer(new StringRedisSerializer());
                    tpl.setValueSerializer(new GenericJackson2JsonRedisSerializer());
                    tpl.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
                    return tpl;
                }
                """);

        System.out.println("=== 五种类型的 API 对照 ===");
        Map<String, String> api = new LinkedHashMap<>();
        api.put("String", "opsForValue().set(k, v, 10, TimeUnit.MINUTES) / get(k) / increment(k)");
        api.put("Hash", "opsForHash().put(k, field, v) / entries(k) / delete(k, field)");
        api.put("List", "opsForList().leftPush(k, v) / range(k, 0, -1) / rightPop(k)");
        api.put("Set", "opsForSet().add(k, v...) / members(k) / intersect(k1, k2)");
        api.put("ZSet", "opsForZSet().add(k, v, score) / reverseRangeWithScores(k, 0, 9)");
        api.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));

        System.out.println();
        System.out.println("=== 手写缓存 vs @Cacheable ===");
        System.out.println("  手写：");
        System.out.println("""
                    public Item getItem(Long id) {
                        String key = "item:" + id;
                        Object cached = redisTemplate.opsForValue().get(key);
                        if (cached != null) {
                            return (Item) cached;
                        }
                        Item item = itemMapper.selectById(id);
                        redisTemplate.opsForValue().set(key, item, 30, TimeUnit.MINUTES);
                        return item;
                    }
                """);
        System.out.println("  注解：");
        System.out.println("""
                    @Cacheable(value = "item", key = "#id")
                    public Item getItem(Long id) {
                        return itemMapper.selectById(id);
                    }
                """);
        System.out.println("  -> 缓存逻辑由 AOP 代理织入，业务代码只剩查库");

        System.out.println();
        System.out.println("=== 一次完整调用（模拟）===");
        Map<String, String> cache = new LinkedHashMap<>();
        long id = 10086L;
        String key = "item:" + id;
        System.out.println("  第 1 次请求：" + key + " 未命中 -> 查库 -> 回写缓存 TTL=30min");
        cache.put(key, "{\"id\":10086,\"name\":\"机械键盘\",\"price\":399}");
        System.out.println("  第 2 次请求：" + key + " 命中 -> 直接返回 " + cache.get(key));
        System.out.println("  更新商品后：@CacheEvict(value=\"item\", key=\"#id\") 删除缓存，下次重新加载");
        cache.remove(key);
        System.out.println("  删除后缓存大小 = " + cache.size());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么推荐 key 用 `StringRedisSerializer`？不配序列化器时 `redis-cli` 里看到的 key 长什么样？
 * 2. 用 `RedisTemplate` 实现"阅读量 +1 并返回新值"，说明为什么必须用 `increment` 而不是 `get` 后 `set`。
 * 3. `@Cacheable` 加在同一个类的两个方法上，A 调用 B 时 B 的缓存为什么不生效？怎么改？
 * ============================================================
 */
