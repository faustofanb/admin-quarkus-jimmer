package io.github.faustofan.admin.shared.distributed.interceptor;

import io.github.faustofan.admin.shared.distributed.DistributedFacade;
import io.github.faustofan.admin.shared.distributed.annotation.DistributedLock;
import io.github.faustofan.admin.shared.distributed.config.DistributedConfig;
import io.github.faustofan.admin.shared.distributed.constants.LockType;
import io.github.faustofan.admin.shared.distributed.exception.DistributedException;
import io.github.faustofan.admin.shared.distributed.exception.DistributedExceptionType;
import io.github.faustofan.admin.shared.distributed.lock.LockContext;
import io.github.faustofan.admin.shared.distributed.lock.LockProvider;
import io.github.faustofan.admin.shared.distributed.spel.DistributedKeyExpressionParser;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;

/**
 * 分布式锁拦截器
 * <p>
 * 处理 {@link DistributedLock} 注解，实现方法级分布式锁：
 * <ol>
 *   <li>解析锁Key</li>
 *   <li>检查锁条件</li>
 *   <li>尝试获取锁</li>
 *   <li>获取成功：执行方法</li>
 *   <li>执行完成：自动释放锁</li>
 *   <li>获取失败：抛出异常或返回null</li>
 * </ol>
 */
@DistributedLock
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 60)
public class DistributedLockInterceptor {

    private static final Logger LOG = Logger.getLogger(DistributedLockInterceptor.class);

    @Inject
    DistributedFacade distributedFacade;

    @Inject
    DistributedConfig config;

    @Inject
    DistributedKeyExpressionParser expressionParser;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        DistributedLock annotation = getAnnotation(context);
        if (annotation == null || !config.enabled()) {
            return context.proceed();
        }

        // 1. 检查锁条件
        if (!checkCondition(annotation.condition(), context)) {
            LOG.debugv("Lock condition not met, skipping: {0}", annotation.condition());
            return context.proceed();
        }

        // 2. 解析锁Key
        String lockKey = resolveLockKey(annotation, context);

        // 3. 获取锁提供者
        LockProvider lockProvider = getLockProvider(annotation);

        // 4. 解析等待时间和租约时间
        Duration waitTime = resolveWaitTime(annotation);
        Duration leaseTime = resolveLeaseTime(annotation);

        // 5. 尝试获取锁
        Optional<LockContext> lockContextOpt = lockProvider.tryLock(lockKey, waitTime, leaseTime);

        if (lockContextOpt.isEmpty()) {
            // 获取锁失败
            LOG.warnv("Failed to acquire lock: {0}", lockKey);
            
            if (annotation.throwOnFailure()) {
                throw new DistributedException(
                    DistributedExceptionType.LOCK_ACQUIRE_FAILED,
                    annotation.message()
                );
            } else {
                return null;
            }
        }

        LockContext lockContext = lockContextOpt.get();
        LOG.debugv("Lock acquired: {0}", lockKey);

        try {
            // 6. 执行方法
            return context.proceed();
        } finally {
            // 7. 释放锁
            lockProvider.unlock(lockContext);
            LOG.debugv("Lock released: {0}", lockKey);
        }
    }

    /**
     * 获取注解
     */
    private DistributedLock getAnnotation(InvocationContext context) {
        DistributedLock annotation = context.getMethod().getAnnotation(DistributedLock.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(DistributedLock.class);
        }
        return annotation;
    }

    /**
     * 解析锁Key
     */
    private String resolveLockKey(DistributedLock annotation, InvocationContext context) {
        String key = expressionParser.parseKey(annotation.key(), context);

        // 添加前缀
        String prefix = annotation.prefix();
        if (prefix != null && !prefix.isEmpty()) {
            key = prefix + ":" + key;
        }

        return key;
    }

    /**
     * 获取锁提供者
     */
    private LockProvider getLockProvider(DistributedLock annotation) {
        LockType type = annotation.type();
        return distributedFacade.getLockProvider(type);
    }

    /**
     * 解析等待时间
     */
    private Duration resolveWaitTime(DistributedLock annotation) {
        if (annotation.waitTime() != null && !annotation.waitTime().isEmpty()) {
            try {
                return Duration.parse(annotation.waitTime());
            } catch (Exception e) {
                LOG.warnv("Invalid waitTime format: {0}, using default", annotation.waitTime());
            }
        }
        return config.lock().waitTime();
    }

    /**
     * 解析租约时间
     */
    private Duration resolveLeaseTime(DistributedLock annotation) {
        if (annotation.leaseTime() != null && !annotation.leaseTime().isEmpty()) {
            try {
                return Duration.parse(annotation.leaseTime());
            } catch (Exception e) {
                LOG.warnv("Invalid leaseTime format: {0}, using default", annotation.leaseTime());
            }
        }
        return config.lock().leaseTime();
    }

    /**
     * 检查锁条件
     */
    private boolean checkCondition(String condition, InvocationContext context) {
        return expressionParser.parseCondition(condition, context);
    }
}
