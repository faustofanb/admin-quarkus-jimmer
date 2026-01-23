package io.github.faustofan.admin.shared.distributed.interceptor;

import io.github.faustofan.admin.shared.distributed.DistributedFacade;
import io.github.faustofan.admin.shared.distributed.annotation.Idempotent;
import io.github.faustofan.admin.shared.distributed.config.DistributedConfig;
import io.github.faustofan.admin.shared.distributed.exception.DistributedException;
import io.github.faustofan.admin.shared.distributed.exception.DistributedExceptionType;
import io.github.faustofan.admin.shared.distributed.spel.DistributedKeyExpressionParser;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * 幂等拦截器
 * <p>
 * 处理 {@link Idempotent} 注解，实现请求幂等检查：
 * <ol>
 *   <li>解析幂等Key</li>
 *   <li>检查幂等条件</li>
 *   <li>检查是否为首次请求</li>
 *   <li>首次请求：执行方法并标记</li>
 *   <li>重复请求：抛出异常或返回null</li>
 * </ol>
 */
@Idempotent
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 50)
public class IdempotentInterceptor {

    private static final Logger LOG = Logger.getLogger(IdempotentInterceptor.class);

    @Inject
    DistributedFacade distributedFacade;

    @Inject
    DistributedConfig config;

    @Inject
    DistributedKeyExpressionParser expressionParser;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        Idempotent annotation = getAnnotation(context);
        if (annotation == null || !config.enabled() || !config.idempotent().enabled()) {
            return context.proceed();
        }

        // 1. 检查幂等条件
        if (!checkCondition(annotation.condition(), context)) {
            LOG.debugv("Idempotent condition not met, skipping: {0}", annotation.condition());
            return context.proceed();
        }

        // 2. 解析幂等Key
        String idempotentKey = resolveIdempotentKey(annotation, context);

        // 3. 解析TTL
        Duration ttl = resolveTtl(annotation);

        // 4. 检查并标记
        boolean isFirst = ttl != null 
            ? distributedFacade.checkAndMarkIdempotent(idempotentKey, ttl)
            : distributedFacade.checkAndMarkIdempotent(idempotentKey);

        if (!isFirst) {
            // 重复请求
            LOG.warnv("Duplicate request detected: {0}", idempotentKey);
            
            if (annotation.throwOnDuplicate()) {
                throw new DistributedException(
                    DistributedExceptionType.IDEMPOTENT_DUPLICATE_REQUEST,
                    annotation.message()
                );
            } else {
                return null;
            }
        }

        // 5. 执行方法
        try {
            LOG.debugv("Executing idempotent method: {0}", idempotentKey);
            return context.proceed();
        } catch (Exception e) {
            // 执行失败时，根据配置决定是否移除标记
            if (annotation.removeOnFailure()) {
                distributedFacade.removeIdempotent(idempotentKey);
                LOG.debugv("Removed idempotent mark due to failure: {0}", idempotentKey);
            }
            throw e;
        }
    }

    /**
     * 获取注解
     */
    private Idempotent getAnnotation(InvocationContext context) {
        Idempotent annotation = context.getMethod().getAnnotation(Idempotent.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(Idempotent.class);
        }
        return annotation;
    }

    /**
     * 解析幂等Key
     */
    private String resolveIdempotentKey(Idempotent annotation, InvocationContext context) {
        String key = expressionParser.parseKey(annotation.key(), context);

        // 添加前缀
        String prefix = annotation.prefix();
        if (prefix != null && !prefix.isEmpty()) {
            key = prefix + ":" + key;
        }

        return key;
    }

    /**
     * 解析TTL
     */
    private Duration resolveTtl(Idempotent annotation) {
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
     * 检查幂等条件
     */
    private boolean checkCondition(String condition, InvocationContext context) {
        return expressionParser.parseCondition(condition, context);
    }
}
