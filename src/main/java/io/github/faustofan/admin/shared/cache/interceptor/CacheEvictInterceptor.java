package io.github.faustofan.admin.shared.cache.interceptor;

import io.github.faustofan.admin.shared.cache.CacheFacade;
import io.github.faustofan.admin.shared.cache.annotation.CacheEvict;
import io.github.faustofan.admin.shared.cache.config.CacheConfig;
import io.github.faustofan.admin.shared.cache.spel.CacheKeyExpressionParser;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

/**
 * 缓存清除拦截器
 * <p>
 * 处理 {@link CacheEvict} 注解，在方法执行前或后清除缓存
 */
@CacheEvict
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 150)
public class CacheEvictInterceptor {

    private static final Logger LOG = Logger.getLogger(CacheEvictInterceptor.class);

    @Inject
    CacheFacade cacheFacade;

    @Inject
    CacheConfig cacheConfig;

    @Inject
    CacheKeyExpressionParser expressionParser;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        CacheEvict annotation = getAnnotation(context);
        if (annotation == null || !cacheConfig.enabled()) {
            return context.proceed();
        }

        // 检查清除条件
        if (!checkCondition(annotation.condition(), context)) {
            LOG.debugv("Cache evict condition not met: {0}", annotation.condition());
            return context.proceed();
        }

        try {
            // 方法执行前清除
            if (annotation.beforeInvocation()) {
                evictCache(annotation, context, null);
            }

            // 执行方法
            Object result = context.proceed();

            // 方法执行后清除（默认）
            if (!annotation.beforeInvocation()) {
                evictCache(annotation, context, result);
            }

            return result;

        } catch (Exception e) {
            // 如果设置了 beforeInvocation，即使方法失败缓存也已清除
            // 如果使用默认的 afterInvocation，方法失败时不清除缓存
            throw e;
        }
    }

    /**
     * 执行缓存清除
     */
    private void evictCache(CacheEvict annotation, InvocationContext context, Object result) {
        try {
            if (annotation.allEntries()) {
                // 清除所有缓存（基于命名空间）
                String cacheName = annotation.cacheName();
                if (cacheName != null && !cacheName.isEmpty()) {
                    // 清除指定命名空间的所有缓存
                    cacheFacade.invalidate(cacheName + ":*");
                    LOG.debugv("Cache evict all entries in namespace: {0}", cacheName);
                } else {
                    // 清除所有缓存
                    cacheFacade.invalidateAll();
                    LOG.debug("Cache evict all entries");
                }
            } else {
                // 清除指定Key
                String cacheKey = resolveCacheKey(annotation, context, result);
                cacheFacade.invalidate(cacheKey);
                LOG.debugv("Cache evict: {0}", cacheKey);
            }
        } catch (Exception e) {
            LOG.warnv("Cache evict failed: {0}", e.getMessage());
        }
    }

    /**
     * 获取注解
     */
    private CacheEvict getAnnotation(InvocationContext context) {
        CacheEvict annotation = context.getMethod().getAnnotation(CacheEvict.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(CacheEvict.class);
        }
        return annotation;
    }

    /**
     * 解析缓存Key
     */
    private String resolveCacheKey(CacheEvict annotation, InvocationContext context, Object result) {
        String key = expressionParser.parseKey(annotation.key(), context, result);

        // 添加命名空间前缀
        String cacheName = annotation.cacheName();
        if (cacheName != null && !cacheName.isEmpty()) {
            key = cacheName + ":" + key;
        }

        return key;
    }

    /**
     * 检查清除条件
     */
    private boolean checkCondition(String condition, InvocationContext context) {
        return expressionParser.parseCondition(condition, context, null);
    }
}
