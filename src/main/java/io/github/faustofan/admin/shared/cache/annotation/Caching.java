package io.github.faustofan.admin.shared.cache.annotation;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 组合缓存注解
 * <p>
 * 当需要在同一方法上应用多个缓存操作时使用
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 更新用户时：更新主缓存，清除关联缓存
 * @Caching(
 *     put = {
 *         @CachePut(key = "'user:' + #result.id")
 *     },
 *     evict = {
 *         @CacheEvict(key = "'user:username:' + #user.username"),
 *         @CacheEvict(key = "'user:email:' + #user.email")
 *     }
 * )
 * public User updateUser(User user) {
 *     return userRepository.save(user);
 * }
 *
 * // 删除用户时清除多个相关缓存
 * @Caching(evict = {
 *     @CacheEvict(key = "'user:' + #userId"),
 *     @CacheEvict(key = "'user:roles:' + #userId"),
 *     @CacheEvict(key = "'user:permissions:' + #userId")
 * })
 * public void deleteUser(Long userId) {
 *     userRepository.deleteById(userId);
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Caching {

    /**
     * 缓存读取操作列表
     */
    @Nonbinding
    Cacheable[] cacheable() default {};

    /**
     * 缓存更新操作列表
     */
    @Nonbinding
    CachePut[] put() default {};

    /**
     * 缓存清除操作列表
     */
    @Nonbinding
    CacheEvict[] evict() default {};
}
