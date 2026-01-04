package io.github.faustofan.admin.shared.cache.interceptor;

import io.github.faustofan.admin.shared.cache.CacheFacade;
import io.github.faustofan.admin.shared.cache.annotation.CacheEvict;
import io.github.faustofan.admin.shared.cache.annotation.CachePut;
import io.github.faustofan.admin.shared.cache.annotation.Cacheable;
import io.github.faustofan.admin.shared.cache.annotation.Caching;
import io.github.faustofan.admin.shared.cache.config.CacheConfig;
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
 * 组合缓存拦截器
 * <p>
 * 处理 {@link Caching} 注解，支持在同一方法上组合多个缓存操作：
 * <ol>
 *   <li>执行前：处理 beforeInvocation=true 的 evict 操作</li>
 *   <li>缓存读取：处理 cacheable 操作（如有任一命中则返回）</li>
 *   <li>方法执行：执行实际方法</li>
 *   <li>缓存更新：处理 put 和 evict 操作</li>
 * </ol>
 */
@Caching
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 80)
public class CachingInterceptor {

    private static final Logger LOG = Logger.getLogger(CachingInterceptor.class);

    @Inject
    CacheFacade cacheFacade;

    @Inject
    CacheConfig cacheConfig;

    @Inject
    CacheKeyExpressionParser expressionParser;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        Caching annotation = getAnnotation(context);
        if (annotation == null || !cacheConfig.enabled()) {
            return context.proceed();
        }

        // 1. 执行前的缓存清除（beforeInvocation = true）
        for (CacheEvict evict : annotation.evict()) {
            if (evict.beforeInvocation()) {
                evictCache(evict, context, null);
            }
        }

        // 2. 尝试从缓存读取（cacheable）
        for (Cacheable cacheable : annotation.cacheable()) {
            if (checkCondition(cacheable.condition(), context, null)) {
                String cacheKey = resolveCacheKey(cacheable, context, null);
                Optional<?> cached = cacheFacade.get(cacheKey, context.getMethod().getReturnType());
                if (cached.isPresent()) {
                    LOG.debugv("Caching: cache hit: {0}", cacheKey);
                    return cached.get();
                }
            }
        }

        // 3. 执行方法
        Object result = context.proceed();

        // 4. 执行后的 cacheable 写入
        for (Cacheable cacheable : annotation.cacheable()) {
            if (checkCondition(cacheable.condition(), context, null) &&
                shouldCacheCacheable(cacheable, context, result)) {
                String cacheKey = resolveCacheKey(cacheable, context, null);
                Duration ttl = resolveTtl(cacheable.ttl());
                cacheFacade.put(cacheKey, result, ttl);
                LOG.debugv("Caching: cache put (cacheable): {0}", cacheKey);
            }
        }

        // 5. 执行后的 put 操作
        for (CachePut put : annotation.put()) {
            if (checkCondition(put.condition(), context, result) &&
                shouldCachePut(put, context, result)) {
                String cacheKey = resolveCacheKey(put, context, result);
                Duration ttl = resolveTtl(put.ttl());
                cacheFacade.put(cacheKey, result, ttl);
                LOG.debugv("Caching: cache put: {0}", cacheKey);
            }
        }

        // 6. 执行后的缓存清除（beforeInvocation = false，默认）
        for (CacheEvict evict : annotation.evict()) {
            if (!evict.beforeInvocation()) {
                evictCache(evict, context, result);
            }
        }

        return result;
    }

    /**
     * 获取注解
     */
    private Caching getAnnotation(InvocationContext context) {
        Caching annotation = context.getMethod().getAnnotation(Caching.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(Caching.class);
        }
        return annotation;
    }

    /**
     * 执行缓存清除
     */
    private void evictCache(CacheEvict evict, InvocationContext context, Object result) {
        try {
            if (!checkCondition(evict.condition(), context, result)) {
                return;
            }

            if (evict.allEntries()) {
                String cacheName = evict.cacheName();
                if (cacheName != null && !cacheName.isEmpty()) {
                    cacheFacade.invalidate(cacheName + ":*");
                } else {
                    cacheFacade.invalidateAll();
                }
            } else {
                String cacheKey = resolveCacheKey(evict, context, result);
                cacheFacade.invalidate(cacheKey);
            }
        } catch (Exception e) {
            LOG.warnv("Caching: evict failed: {0}", e.getMessage());
        }
    }

    /**
     * 解析 Cacheable 的缓存Key
     */
    private String resolveCacheKey(Cacheable annotation, InvocationContext context, Object result) {
        String key = expressionParser.parseKey(annotation.key(), context, result);
        String cacheName = annotation.cacheName();
        if (cacheName != null && !cacheName.isEmpty()) {
            key = cacheName + ":" + key;
        }
        return key;
    }

    /**
     * 解析 CachePut 的缓存Key
     */
    private String resolveCacheKey(CachePut annotation, InvocationContext context, Object result) {
        String key = expressionParser.parseKey(annotation.key(), context, result);
        String cacheName = annotation.cacheName();
        if (cacheName != null && !cacheName.isEmpty()) {
            key = cacheName + ":" + key;
        }
        return key;
    }

    /**
     * 解析 CacheEvict 的缓存Key
     */
    private String resolveCacheKey(CacheEvict annotation, InvocationContext context, Object result) {
        String key = expressionParser.parseKey(annotation.key(), context, result);
        String cacheName = annotation.cacheName();
        if (cacheName != null && !cacheName.isEmpty()) {
            key = cacheName + ":" + key;
        }
        return key;
    }

    /**
     * 解析TTL
     */
    private Duration resolveTtl(String ttlStr) {
        if (ttlStr != null && !ttlStr.isEmpty()) {
            try {
                return Duration.parse(ttlStr);
            } catch (Exception e) {
                LOG.warnv("Invalid TTL format: {0}", ttlStr);
            }
        }
        return null;
    }

    /**
     * 检查条件
     */
    private boolean checkCondition(String condition, InvocationContext context, Object result) {
        return expressionParser.parseCondition(condition, context, result);
    }

    /**
     * Cacheable 是否应该缓存
     */
    private boolean shouldCacheCacheable(Cacheable annotation, InvocationContext context, Object result) {
        if (result == null && !annotation.cacheNullValue()) {
            return false;
        }
        String unless = annotation.unless();
        if (unless != null && !unless.isEmpty()) {
            return !expressionParser.parseCondition(unless, context, result);
        }
        return true;
    }

    /**
     * CachePut 是否应该缓存
     */
    private boolean shouldCachePut(CachePut annotation, InvocationContext context, Object result) {
        if (result == null && !annotation.cacheNullValue()) {
            return false;
        }
        String unless = annotation.unless();
        if (unless != null && !unless.isEmpty()) {
            return !expressionParser.parseCondition(unless, context, result);
        }
        return true;
    }
}
