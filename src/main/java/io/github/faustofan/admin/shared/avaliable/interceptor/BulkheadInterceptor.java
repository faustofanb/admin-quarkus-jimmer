package io.github.faustofan.admin.shared.avaliable.interceptor;

import io.github.faustofan.admin.shared.avaliable.AvailabilityFacade;
import io.github.faustofan.admin.shared.avaliable.annotation.Bulkhead;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.time.Duration;

/**
 * 隔离舱拦截器
 * <p>
 * 处理 @Bulkhead 注解
 */
@Bulkhead
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 500)
public class BulkheadInterceptor {

    @Inject
    AvailabilityFacade availabilityFacade;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        Bulkhead annotation = getAnnotation(context);
        if (annotation == null) {
            return context.proceed();
        }

        String name = resolveName(annotation.name(), context);

        // 配置隔离舱
        availabilityFacade.configureBulkhead(
                name,
                annotation.maxConcurrentCalls(),
                annotation.waitingTaskQueue()
        );

        // 执行带隔离保护的操作
        return availabilityFacade.executeWithBulkhead(
                name,
                () -> {
                    try {
                        return context.proceed();
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException("Bulkhead execution failed", e);
                    }
                }
        );
    }

    private Bulkhead getAnnotation(InvocationContext context) {
        Bulkhead annotation = context.getMethod().getAnnotation(Bulkhead.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(Bulkhead.class);
        }
        return annotation;
    }

    private String resolveName(String name, InvocationContext context) {
        if (name == null || name.isEmpty()) {
            return context.getTarget().getClass().getSimpleName() +
                    "." + context.getMethod().getName();
        }
        return name;
    }
}
