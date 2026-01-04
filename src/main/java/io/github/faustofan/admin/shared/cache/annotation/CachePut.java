package io.github.faustofan.admin.shared.cache.annotation;

import io.github.faustofan.admin.shared.cache.constants.CacheStrategy;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 缓存更新注解
 * <p>
 * 在方法上使用此注解，方法执行后将返回值放入缓存（无论缓存是否存在）
 * <p>
 * 与 {@link Cacheable} 的区别：
 * <ul>
 *   <li>{@code @Cacheable}：先查缓存，命中则直接返回</li>
 *   <li>{@code @CachePut}：始终执行方法，并更新缓存</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 更新用户时同步更新缓存
 * @CachePut(key = "'user:' + #user.id")
 * public User updateUser(User user) {
 *     return userRepository.save(user);
 * }
 *
 * // 创建用户时写入缓存
 * @CachePut(key = "'user:' + #result.id", condition = "#result != null")
 * public User createUser(UserRequest request) {
 *     return userRepository.save(convertToUser(request));
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface CachePut {

    /**
     * 缓存Key表达式
     * <p>
     * 支持 SpEL 表达式，可使用方法参数和返回值：
     * <ul>
     *   <li>{@code #paramName} - 参数名</li>
     *   <li>{@code #result} - 方法返回值</li>
     * </ul>
     */
    @Nonbinding
    String key() default "";

    /**
     * 缓存名称/命名空间
     */
    @Nonbinding
    String cacheName() default "";

    /**
     * 缓存过期时间（ISO-8601 duration 格式）
     */
    @Nonbinding
    String ttl() default "";

    /**
     * 缓存策略
     */
    @Nonbinding
    CacheStrategy strategy() default CacheStrategy.TWO_LEVEL;

    /**
     * 是否使用配置的默认策略
     */
    @Nonbinding
    boolean useDefaultStrategy() default true;

    /**
     * 缓存条件表达式（SpEL）
     */
    @Nonbinding
    String condition() default "";

    /**
     * 结果排除条件表达式（SpEL）
     * <p>
     * 返回 true 时不缓存结果
     */
    @Nonbinding
    String unless() default "";

    /**
     * 是否缓存空值
     */
    @Nonbinding
    boolean cacheNullValue() default false;
}
