package io.github.faustofan.admin.shared.avaliable.interceptor;

import io.github.faustofan.admin.shared.avaliable.AvailabilityFacade;
import io.github.faustofan.admin.shared.avaliable.annotation.Timeout;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.time.Duration;

/**
 * 超时拦截器
 * <p>
 * 处理 @Timeout 注解
 */
@Timeout
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 400)
public class TimeoutInterceptor {

    @Inject
    AvailabilityFacade availabilityFacade;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        Timeout annotation = getAnnotation(context);
        if (annotation == null) {
            return context.proceed();
        }

        String name = resolveName(annotation.name(), context);
        Duration duration = Duration.parse(annotation.duration());

        // 配置超时
        availabilityFacade.configureTimeout(name, duration);

        // 执行带超时控制的操作
        return availabilityFacade.executeWithTimeout(
                name,
                () -> {
                    try {
                        return context.proceed();
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException("Timeout execution failed", e);
                    }
                },
                duration
        );
    }

    private Timeout getAnnotation(InvocationContext context) {
        Timeout annotation = context.getMethod().getAnnotation(Timeout.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(Timeout.class);
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
