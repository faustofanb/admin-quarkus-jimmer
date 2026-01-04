package io.github.faustofan.admin.shared.cache.interceptor;

import io.github.faustofan.admin.shared.cache.CacheFacade;
import io.github.faustofan.admin.shared.cache.annotation.Cacheable;
import io.github.faustofan.admin.shared.cache.config.CacheConfig;
import io.github.faustofan.admin.shared.cache.constants.CacheStrategy;
import io.github.faustofan.admin.shared.cache.spel.CacheKeyExpressionParser;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;

/**
 * 缓存读取拦截器
 * <p>
 * 处理 {@link Cacheable} 注解，实现 Cache-Aside 模式：
 * <ol>
 *   <li>解析缓存Key</li>
 *   <li>检查缓存条件</li>
 *   <li>尝试从缓存获取</li>
 *   <li>缓存未命中则执行方法</li>
 *   <li>将结果写入缓存</li>
 * </ol>
 */
@Cacheable
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class CacheableInterceptor {

    private static final Logger LOG = Logger.getLogger(CacheableInterceptor.class);

    @Inject
    CacheFacade cacheFacade;

    @Inject
    CacheConfig cacheConfig;

    @Inject
    CacheKeyExpressionParser expressionParser;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        Cacheable annotation = getAnnotation(context);
        if (annotation == null || !cacheConfig.enabled()) {
            return context.proceed();
        }

        // 1. 检查缓存条件
        if (!checkCondition(annotation.condition(), context)) {
            LOG.debugv("Cache condition not met, skipping cache: {0}", annotation.condition());
            return context.proceed();
        }

        // 2. 解析缓存Key
        String cacheKey = resolveCacheKey(annotation, context);
        Class<?> returnType = context.getMethod().getReturnType();

        // 3. 尝试从缓存获取
        try {
            if (annotation.lockProtection()) {
                // 使用分布式锁保护的加载
                Duration ttl = resolveTtl(annotation);
                return cacheFacade.getOrLoadWithLock(
                        cacheKey,
                        Object.class,
                        () -> {
                            try {
                                Object result = context.proceed();
                                // 如果不应该缓存，直接返回
                                if (!shouldCache(annotation, context, result)) {
                                    return result;
                                }
                                return result;
                            } catch (Exception e) {
                                throw new RuntimeException("Method invocation failed", e);
                            }
                        },
                        ttl
                );
            } else {
                // 普通的缓存读取
                Optional<?> cached = cacheFacade.get(cacheKey, returnType);
                if (cached.isPresent()) {
                    LOG.debugv("Cache hit: {0}", cacheKey);
                    return cached.get();
                }

                // 4. 缓存未命中，执行方法
                LOG.debugv("Cache miss: {0}", cacheKey);
                Object result = context.proceed();

                // 5. 检查 unless 条件
                if (shouldCache(annotation, context, result)) {
                    // 6. 写入缓存
                    Duration ttl = resolveTtl(annotation);
                    cacheFacade.put(cacheKey, result, ttl);
                    LOG.debugv("Cache put: {0}", cacheKey);
                }

                return result;
            }
        } catch (Exception e) {
            LOG.warnv("Cache operation failed: {0}, falling back to method execution", e.getMessage());
            return context.proceed();
        }

    }


    /**
     * 获取注解
     */
    private Cacheable getAnnotation(InvocationContext context) {
        Cacheable annotation = context.getMethod().getAnnotation(Cacheable.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(Cacheable.class);
        }
        return annotation;
    }

    /**
     * 解析缓存Key
     */
    private String resolveCacheKey(Cacheable annotation, InvocationContext context) {
        String key = expressionParser.parseKey(annotation.key(), context, null);

        // 添加命名空间前缀
        String cacheName = annotation.cacheName();
        if (cacheName != null && !cacheName.isEmpty()) {
            key = cacheName + ":" + key;
        }

        return key;
    }

    /**
     * 解析TTL
     */
    private Duration resolveTtl(Cacheable annotation) {
        if (annotation.ttl() != null && !annotation.ttl().isEmpty()) {
            try {
                return Duration.parse(annotation.ttl());
            } catch (Exception e) {
                LOG.warnv("Invalid TTL format: {0}, using default", annotation.ttl());
            }
        }
        return null; // 使用默认TTL
    }

    /**
     * 检查缓存条件
     */
    private boolean checkCondition(String condition, InvocationContext context) {
        return expressionParser.parseCondition(condition, context, null);
    }

    /**
     * 检查是否应该缓存结果
     */
    private boolean shouldCache(Cacheable annotation, InvocationContext context, Object result) {
        // 检查 null 值
        if (result == null && !annotation.cacheNullValue()) {
            return false;
        }

        // 检查 unless 条件
        String unless = annotation.unless();
        if (unless != null && !unless.isEmpty()) {
            boolean shouldExclude = expressionParser.parseCondition(unless, context, result);
            if (shouldExclude) {
                LOG.debugv("Cache excluded by unless condition: {0}", unless);
                return false;
            }
        }

        return true;
    }
}
