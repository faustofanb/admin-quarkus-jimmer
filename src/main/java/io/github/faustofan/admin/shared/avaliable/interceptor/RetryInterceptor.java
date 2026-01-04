package io.github.faustofan.admin.shared.avaliable.interceptor;

import io.github.faustofan.admin.shared.avaliable.AvailabilityFacade;
import io.github.faustofan.admin.shared.avaliable.annotation.Retry;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * 重试拦截器
 * <p>
 * 处理 @Retry 注解
 */
@Retry
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 300)
public class RetryInterceptor {

    @Inject
    AvailabilityFacade availabilityFacade;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        Retry annotation = getAnnotation(context);
        if (annotation == null) {
            return context.proceed();
        }

        String name = resolveName(annotation.name(), context);

        // 配置重试器
        availabilityFacade.configureRetry(
                name,
                annotation.maxRetries(),
                Duration.parse(annotation.delay()),
                annotation.strategy()
        );

        // 执行带重试的操作
        return availabilityFacade.executeWithRetry(
                name,
                (Callable<Object>) context::proceed
        );
    }

    private Retry getAnnotation(InvocationContext context) {
        Retry annotation = context.getMethod().getAnnotation(Retry.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(Retry.class);
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
