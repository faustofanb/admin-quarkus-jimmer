package io.github.faustofan.admin.shared.cache.annotation;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 缓存清除注解
 * <p>
 * 在方法上使用此注解可在方法执行后清除指定缓存
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 删除单个缓存
 * @CacheEvict(key = "'user:' + #id")
 * public void deleteUser(Long id) {
 *     userRepository.deleteById(id);
 * }
 *
 * // 更新时清除缓存
 * @CacheEvict(key = "'user:' + #user.id")
 * public User updateUser(User user) {
 *     return userRepository.save(user);
 * }
 *
 * // 清除整个命名空间的缓存
 * @CacheEvict(cacheName = "user", allEntries = true)
 * public void clearAllUserCache() {
 *     // ...
 * }
 *
 * // 方法执行前清除（默认是执行后）
 * @CacheEvict(key = "'order:' + #orderId", beforeInvocation = true)
 * public void processOrder(Long orderId) {
 *     // 先清除缓存，再处理订单
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface CacheEvict {

    /**
     * 要清除的缓存Key表达式
     * <p>
     * 支持 SpEL 表达式
     */
    @Nonbinding
    String key() default "";

    /**
     * 缓存名称/命名空间
     */
    @Nonbinding
    String cacheName() default "";

    /**
     * 是否清除命名空间下所有缓存
     * <p>
     * 当设置为 true 时，将忽略 key 属性，清除 cacheName 下的所有缓存
     */
    @Nonbinding
    boolean allEntries() default false;

    /**
     * 是否在方法执行前清除缓存
     * <p>
     * 默认在方法成功执行后清除
     */
    @Nonbinding
    boolean beforeInvocation() default false;

    /**
     * 清除条件表达式（SpEL）
     * <p>
     * 返回 true 时才清除缓存
     */
    @Nonbinding
    String condition() default "";
}
