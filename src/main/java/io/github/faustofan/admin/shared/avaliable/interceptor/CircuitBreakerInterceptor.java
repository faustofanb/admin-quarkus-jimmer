package io.github.faustofan.admin.shared.avaliable.interceptor;

import io.github.faustofan.admin.shared.avaliable.AvailabilityFacade;
import io.github.faustofan.admin.shared.avaliable.annotation.CircuitBreaker;
import io.github.faustofan.admin.shared.avaliable.annotation.Fallback;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * 熔断器拦截器
 * <p>
 * 处理 @CircuitBreaker 注解
 */
@CircuitBreaker
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class CircuitBreakerInterceptor {

    private static final Logger LOG = Logger.getLogger(CircuitBreakerInterceptor.class);

    @Inject
    AvailabilityFacade availabilityFacade;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        CircuitBreaker annotation = getAnnotation(context);
        if (annotation == null) {
            return context.proceed();
        }

        String name = resolveName(annotation.name(), context);

        // 配置熔断器
        availabilityFacade.configureCircuitBreaker(
                name,
                annotation.failureRatio(),
                annotation.requestVolumeThreshold(),
                Duration.parse(annotation.delay()),
                annotation.successThreshold()
        );

        // 查找回退方法
        Supplier<Object> fallback = resolveFallback(context);

        // 执行带熔断保护的操作
        return availabilityFacade.executeWithCircuitBreaker(
                name,
                () -> {
                    try {
                        return context.proceed();
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException("Circuit breaker execution failed", e);
                    }
                },
                fallback
        );
    }

    private CircuitBreaker getAnnotation(InvocationContext context) {
        CircuitBreaker annotation = context.getMethod().getAnnotation(CircuitBreaker.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(CircuitBreaker.class);
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

    private Supplier<Object> resolveFallback(InvocationContext context) {
        Fallback fallbackAnnotation = context.getMethod().getAnnotation(Fallback.class);
        if (fallbackAnnotation == null || fallbackAnnotation.fallbackMethod().isEmpty()) {
            return null;
        }

        String fallbackMethodName = fallbackAnnotation.fallbackMethod();
        Object target = context.getTarget();

        return () -> {
            try {
                Method fallbackMethod = target.getClass().getDeclaredMethod(
                        fallbackMethodName,
                        context.getMethod().getParameterTypes()
                );
                fallbackMethod.setAccessible(true);
                return fallbackMethod.invoke(target, context.getParameters());
            } catch (Exception e) {
                LOG.warnf("Failed to invoke fallback method: %s", fallbackMethodName);
                return null;
            }
        };
    }
}
