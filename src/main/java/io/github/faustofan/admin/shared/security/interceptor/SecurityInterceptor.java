package io.github.faustofan.admin.shared.security.interceptor;

import io.github.faustofan.admin.shared.security.annotation.Anonymous;
import io.github.faustofan.admin.shared.security.annotation.RequiresAuthentication;
import io.github.faustofan.admin.shared.security.annotation.RequiresPermission;
import io.github.faustofan.admin.shared.security.annotation.RequiresRole;
import io.github.faustofan.admin.shared.security.annotation.Secured;
import io.github.faustofan.admin.shared.security.context.SecurityContext;
import io.github.faustofan.admin.shared.security.context.SecurityContextHolder;
import io.github.faustofan.admin.shared.security.exception.AuthenticationException;
import io.github.faustofan.admin.shared.security.exception.AuthorizationException;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 权限拦截器
 * <p>
 * 拦截标注了安全注解的方法，进行认证和授权检查。
 *
 * <h3>处理的注解：</h3>
 * <ul>
 *   <li>{@link RequiresAuthentication} - 需要认证</li>
 *   <li>{@link RequiresPermission} - 需要权限</li>
 *   <li>{@link RequiresRole} - 需要角色</li>
 *   <li>{@link Anonymous} - 允许匿名</li>
 * </ul>
 */
@Secured
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class SecurityInterceptor {

    private static final Logger LOG = Logger.getLogger(SecurityInterceptor.class);

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        Method method = context.getMethod();
        Class<?> targetClass = context.getTarget().getClass();

        // 1. 检查@Anonymous注解（优先级最高）
        if (hasAnonymous(method, targetClass)) {
            LOG.debugv("Anonymous access allowed: {0}.{1}",
                targetClass.getSimpleName(), method.getName());
            return context.proceed();
        }

        // 2. 获取安全上下文
        SecurityContext securityContext = SecurityContextHolder.getContext();

        // 3. 检查@RequiresAuthentication
        if (hasRequiresAuthentication(method, targetClass)) {
            if (!securityContext.isAuthenticated()) {
                LOG.warnv("Authentication required: {0}.{1}",
                    targetClass.getSimpleName(), method.getName());
                throw AuthenticationException.tokenMissing();
            }
        }

        // 4. 检查@RequiresPermission
        RequiresPermission requiresPermission = getRequiresPermission(method, targetClass);
        if (requiresPermission != null) {
            checkPermission(securityContext, requiresPermission, method, targetClass);
        }

        // 5. 检查@RequiresRole
        RequiresRole requiresRole = getRequiresRole(method, targetClass);
        if (requiresRole != null) {
            checkRole(securityContext, requiresRole, method, targetClass);
        }

        return context.proceed();
    }

    // ===========================
    // 私有辅助方法
    // ===========================

    private boolean hasAnonymous(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(Anonymous.class)
            || targetClass.isAnnotationPresent(Anonymous.class);
    }

    private boolean hasRequiresAuthentication(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(RequiresAuthentication.class)
            || targetClass.isAnnotationPresent(RequiresAuthentication.class);
    }

    private RequiresPermission getRequiresPermission(Method method, Class<?> targetClass) {
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation == null) {
            annotation = targetClass.getAnnotation(RequiresPermission.class);
        }
        return annotation;
    }

    private RequiresRole getRequiresRole(Method method, Class<?> targetClass) {
        RequiresRole annotation = method.getAnnotation(RequiresRole.class);
        if (annotation == null) {
            annotation = targetClass.getAnnotation(RequiresRole.class);
        }
        return annotation;
    }

    private void checkPermission(
        SecurityContext context,
        RequiresPermission annotation,
        Method method,
        Class<?> targetClass
    ) {
        if (!context.isAuthenticated()) {
            throw AuthenticationException.tokenMissing();
        }

        String[] requiredPermissions = annotation.value();
        RequiresPermission.Logical logical = annotation.logical();
        List<String> userPermissions = context.getPermissions();

        boolean hasPermission;
        if (logical == RequiresPermission.Logical.AND) {
            hasPermission = Arrays.stream(requiredPermissions)
                .allMatch(p -> userPermissions.contains(p) || context.isSuperAdmin());
        } else {
            hasPermission = Arrays.stream(requiredPermissions)
                .anyMatch(p -> userPermissions.contains(p) || context.isSuperAdmin());
        }

        if (!hasPermission) {
            LOG.warnv("Permission denied: {0}.{1}, required={2}",
                targetClass.getSimpleName(), method.getName(), Arrays.toString(requiredPermissions));
            throw AuthorizationException.permissionDenied(String.join(",", requiredPermissions));
        }
    }

    private void checkRole(
        SecurityContext context,
        RequiresRole annotation,
        Method method,
        Class<?> targetClass
    ) {
        if (!context.isAuthenticated()) {
            throw AuthenticationException.tokenMissing();
        }

        String[] requiredRoles = annotation.value();
        RequiresPermission.Logical logical = annotation.logical();
        List<String> userRoles = context.getRoles();

        boolean hasRole;
        if (logical == RequiresPermission.Logical.AND) {
            hasRole = Arrays.stream(requiredRoles).allMatch(userRoles::contains);
        } else {
            hasRole = Arrays.stream(requiredRoles).anyMatch(userRoles::contains);
        }

        if (!hasRole) {
            LOG.warnv("Role denied: {0}.{1}, required={2}",
                targetClass.getSimpleName(), method.getName(), Arrays.toString(requiredRoles));
            throw AuthorizationException.roleDenied(String.join(",", requiredRoles));
        }
    }
}
