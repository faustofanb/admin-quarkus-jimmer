package io.github.faustofan.admin.shared.avaliable.interceptor;

import io.github.faustofan.admin.shared.avaliable.AvailabilityFacade;
import io.github.faustofan.admin.shared.avaliable.annotation.Fallback;
import io.github.faustofan.admin.shared.avaliable.annotation.Protect;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * 组合保护拦截器
 * <p>
 * 处理 @Protect 注解
 */
@Protect
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE)
public class ProtectInterceptor {

    private static final Logger LOG = Logger.getLogger(ProtectInterceptor.class);

    @Inject
    AvailabilityFacade availabilityFacade;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        Protect annotation = getAnnotation(context);
        if (annotation == null) {
            return context.proceed();
        }

        String name = resolveName(annotation.name(), context);
        Supplier<Object> fallback = resolveFallback(annotation.fallbackMethod(), context);

        // 根据保护模式执行
        return switch (annotation.mode()) {
            case FULL -> availabilityFacade.protect(
                    name,
                    () -> {
                        try {
                            return context.proceed();
                        } catch (Exception e) {
                            if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                            }
                            throw new RuntimeException("Protect execution failed", e);
                        }
                    },
                    fallback
            );
            case STANDARD -> availabilityFacade.protectStandard(
                    name,
                    () -> {
                        try {
                            return context.proceed();
                        } catch (Exception e) {
                            if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                            }
                            throw new RuntimeException("Protect execution failed", e);
                        }
                    },
                    fallback
            );
            case LIGHT -> availabilityFacade.protectLight(
                    name,
                    () -> {
                        try {
                            return context.proceed();
                        } catch (Exception e) {
                            if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                            }
                            throw new RuntimeException("Protect execution failed", e);
                        }
                    },
                    fallback
            );
        };
    }

    private Protect getAnnotation(InvocationContext context) {
        Protect annotation = context.getMethod().getAnnotation(Protect.class);
        if (annotation == null) {
            annotation = context.getTarget().getClass().getAnnotation(Protect.class);
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

    private Supplier<Object> resolveFallback(String fallbackMethodName, InvocationContext context) {
        if (fallbackMethodName == null || fallbackMethodName.isEmpty()) {
            return null;
        }

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
