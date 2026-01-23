package io.github.faustofan.admin.shared.cache.interceptor;

import io.github.faustofan.admin.shared.cache.CacheFacade;
import io.github.faustofan.admin.shared.cache.annotation.CachePut;
import io.github.faustofan.admin.shared.cache.config.CacheConfig;
import io.github.faustofan.admin.shared.cache.spel.CacheKeyExpressionParser;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * 缓存更新拦截器
 * <p>
 * 处理 {@link CachePut} 注解，始终执行方法并将结果写入缓存
 */
@CachePut
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 120)
public class CachePutInterceptor {

    private static final Logger LOG = Logger.getLogger(CachePutInterceptor.class);

    @Inject
    CacheFacade cacheFacade;

    @Inject
    CacheConfig cacheConfig;

    @Inject
    CacheKeyExpressionParser expressionParser;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        CachePut annotation = getAnnotation(context);
        if (annotation == null || !cacheConfig.enabled()) {
            return context.proceed();
        }

        // 始终执行方法
        Object result = context.proceed();

        try {
            // 检查缓存条件
            if (!checkCondition(annotation.condition(), context, result)) {
                LOG.debugv("Cache put condition not met: {0}", annotation.condition());
                return result;
            }

            // 检查是否应该缓存结果
            if (!shouldCache(annotation, context, result)) {
                return result;
            }

            // 解析缓存Key（可以使用 result）
            String cacheKey = resolveCacheKey(annotation, context, result);

            // 写入缓存
            Duration ttl = resolveTtl(annotation);
            cacheFacade.put(cacheKey, result, ttl);
            LOG.debugv("Cache put: {0}", cacheKey);

        } catch (Exception e) {
            LOG.warnv("Cache put failed: {0}", e.getMessage());
        }

        return result;
    }

    /**
     * 获取注解
     */
    private CachePut getAnnotation(InvocationContext context) {
        CachePut annotation = context.getMethod().getAnnotation(CachePut.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(CachePut.class);
        }
        return annotation;
    }

    /**
     * 解析缓存Key
     */
    private String resolveCacheKey(CachePut annotation, InvocationContext context, Object result) {
        String key = expressionParser.parseKey(annotation.key(), context, result);

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
    private Duration resolveTtl(CachePut annotation) {
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
    private boolean checkCondition(String condition, InvocationContext context, Object result) {
        return expressionParser.parseCondition(condition, context, result);
    }

    /**
     * 检查是否应该缓存结果
     */
    private boolean shouldCache(CachePut annotation, InvocationContext context, Object result) {
        // 检查 null 值
        if (result == null && !annotation.cacheNullValue()) {
            return false;
        }

        // 检查 unless 条件
        String unless = annotation.unless();
        if (unless != null && !unless.isEmpty()) {
            boolean shouldExclude = expressionParser.parseCondition(unless, context, result);
            if (shouldExclude) {
                LOG.debugv("Cache put excluded by unless condition: {0}", unless);
                return false;
            }
        }

        return true;
    }
}
