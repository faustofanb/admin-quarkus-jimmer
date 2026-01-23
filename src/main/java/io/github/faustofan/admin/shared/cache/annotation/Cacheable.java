package io.github.faustofan.admin.shared.cache.annotation;

import io.github.faustofan.admin.shared.cache.constants.CacheStrategy;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 方法级缓存注解（Cache-Aside 模式）
 * <p>
 * 在方法上使用此注解可自动应用缓存机制：
 * <ul>
 *   <li>方法执行前：尝试从缓存获取结果</li>
 *   <li>缓存命中：直接返回缓存值</li>
 *   <li>缓存未命中：执行方法并将结果放入缓存</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Cacheable(
 *     key = "'user:' + #id",
 *     ttl = "PT1H",
 *     strategy = CacheStrategy.TWO_LEVEL
 * )
 * public User findById(Long id) {
 *     return userRepository.findById(id);
 * }
 *
 * // 使用条件缓存
 * @Cacheable(
 *     key = "'user:' + #username",
 *     condition = "#username != null",
 *     unless = "#result == null"
 * )
 * public User findByUsername(String username) {
 *     return userRepository.findByUsername(username);
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Cacheable {

    /**
     * 缓存Key表达式
     * <p>
     * 支持 SpEL 表达式，可使用方法参数：
     * <ul>
     *   <li>{@code #paramName} - 参数名</li>
     *   <li>{@code #p0, #p1} - 参数索引</li>
     *   <li>{@code #result} - 方法返回值（仅在 unless 中可用）</li>
     * </ul>
     * <p>
     * 如不指定，将使用 "类名:方法名:参数哈希" 作为默认Key
     */
    @Nonbinding
    String key() default "";

    /**
     * 缓存名称/命名空间
     * <p>
     * 用于区分不同业务的缓存
     */
    @Nonbinding
    String cacheName() default "";

    /**
     * 缓存过期时间（ISO-8601 duration 格式）
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code PT5M} - 5分钟</li>
     *   <li>{@code PT1H} - 1小时</li>
     *   <li>{@code P1D} - 1天</li>
     * </ul>
     * <p>
     * 不指定则使用默认配置
     */
    @Nonbinding
    String ttl() default "";

    /**
     * 缓存策略
     * <p>
     * 默认使用配置文件中的策略
     */
    @Nonbinding
    CacheStrategy strategy() default CacheStrategy.TWO_LEVEL;

    /**
     * 是否使用配置的默认策略
     * <p>
     * 如果为 true，则忽略 strategy 参数，使用配置文件中的默认策略
     */
    @Nonbinding
    boolean useDefaultStrategy() default true;

    /**
     * 缓存条件表达式（SpEL）
     * <p>
     * 返回 true 时才执行缓存操作
     * <p>
     * 示例：{@code "#id > 0"}
     */
    @Nonbinding
    String condition() default "";

    /**
     * 结果排除条件表达式（SpEL）
     * <p>
     * 返回 true 时不缓存结果，常用于过滤空值
     * <p>
     * 示例：{@code "#result == null"}
     */
    @Nonbinding
    String unless() default "";

    /**
     * 是否启用布隆过滤器
     * <p>
     * 用于快速判断缓存是否可能存在，防止缓存穿透
     */
    @Nonbinding
    boolean bloom() default false;

    /**
     * 是否使用分布式锁保护加载过程
     * <p>
     * 防止缓存击穿：热点Key失效时只允许一个线程加载数据
     */
    @Nonbinding
    boolean lockProtection() default false;

    /**
     * 是否缓存空值
     * <p>
     * 开启后将对 null 结果进行占位缓存，防止缓存穿透
     */
    @Nonbinding
    boolean cacheNullValue() default true;
}
