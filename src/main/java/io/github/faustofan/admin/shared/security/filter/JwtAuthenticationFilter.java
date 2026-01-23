package io.github.faustofan.admin.shared.security.filter;

import io.github.faustofan.admin.domain.security.authentication.TokenProvider;
import io.github.faustofan.admin.domain.security.authorization.PermissionChecker;
import io.github.faustofan.admin.domain.security.valueobject.TokenClaims;
import io.github.faustofan.admin.shared.common.AppContext;
import io.github.faustofan.admin.shared.common.AppContextHolder;
import io.github.faustofan.admin.shared.security.config.SecurityConfig;
import io.github.faustofan.admin.shared.security.constants.SecurityConstants;
import io.github.faustofan.admin.shared.security.context.SecurityContext;
import io.github.faustofan.admin.shared.security.context.SecurityContextHolder;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JWT认证过滤器
 * <p>
 * 拦截所有HTTP请求，验证JWT Token并构建安全上下文。
 *
 * <h3>功能：</h3>
 * <ul>
 *   <li>从Authorization头提取JWT Token</li>
 *   <li>验证Token有效性</li>
 *   <li>构建SecurityContext并设置到ThreadLocal</li>
 *   <li>同步用户信息到AppContext</li>
 * </ul>
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class JwtAuthenticationFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(JwtAuthenticationFilter.class);

    private final TokenProvider tokenProvider;
    private final PermissionChecker permissionChecker;
    private final SecurityConfig securityConfig;
    private final Set<String> whitelist;

    @Inject
    public JwtAuthenticationFilter(
        TokenProvider tokenProvider,
        PermissionChecker permissionChecker,
        SecurityConfig securityConfig
    ) {
        this.tokenProvider = tokenProvider;
        this.permissionChecker = permissionChecker;
        this.securityConfig = securityConfig;
        this.whitelist = buildWhitelist();
        LOG.infov("JwtAuthenticationFilter initialized, whitelist: {0}", whitelist);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        // 1. 检查是否在白名单中
        if (isWhitelisted(path)) {
            LOG.debugv("Request whitelisted: {0} {1}", method, path);
            return;
        }

        // 2. 提取Token
        String token = extractToken(requestContext);
        if (token == null) {
            LOG.debugv("No token found for: {0} {1}", method, path);
            // 不抛异常，让后续@PermitAll或其他机制处理
            return;
        }

        // 3. 验证并解析Token
        try {
            TokenClaims claims = tokenProvider.parseToken(token);

            if (claims.isExpired()) {
                LOG.warnv("Token expired for user: {0}", claims.username());
                // 可以在这里返回401，或者让SecurityContext为空
                return;
            }

            // 4. 获取用户权限
            List<String> permissions = List.of();
            if (claims.userId() != null) {
                permissions = permissionChecker.getUserPermissions(claims.userId());
            }

            // 5. 构建SecurityContext
            SecurityContext securityContext = SecurityContext.of(claims, permissions, null);
            SecurityContextHolder.setContext(securityContext);

            // 6. 同步到AppContext
            syncToAppContext(claims, requestContext);

            LOG.debugv("Authentication success: user={0}, path={1}", claims.username(), path);

        } catch (Exception e) {
            LOG.warnv("Token validation failed: {0}", e.getMessage());
            // 清除安全上下文
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
        throws IOException {
        // 请求结束时清理上下文
        SecurityContextHolder.clearContext();
    }

    // ===========================
    // 私有辅助方法
    // ===========================

    /**
     * 从请求头提取Token
     */
    private String extractToken(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString(SecurityConstants.Header.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(SecurityConstants.Header.BEARER_PREFIX)) {
            return authHeader.substring(SecurityConstants.Header.BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        // 统一处理路径格式
        String normalizedPath = path.startsWith("/") ? path : "/" + path;

        return whitelist.stream().anyMatch(pattern -> {
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                return normalizedPath.startsWith(prefix);
            }
            return normalizedPath.equals(pattern) || normalizedPath.startsWith(pattern + "/");
        });
    }

    /**
     * 构建白名单
     */
    private Set<String> buildWhitelist() {
        Stream<String> defaultPaths = Stream.empty();
        if (securityConfig.whitelist().enableDefaults()) {
            String defaults = securityConfig.whitelist().defaultPaths();
            if (defaults != null && !defaults.isBlank()) {
                defaultPaths = Arrays.stream(defaults.split(",")).map(String::trim);
            }
        }

        Stream<String> customPaths = securityConfig.whitelist().paths().stream();

        return Stream.concat(defaultPaths, customPaths)
            .filter(p -> p != null && !p.isBlank())
            .collect(Collectors.toSet());
    }

    /**
     * 同步到AppContext
     */
    private void syncToAppContext(TokenClaims claims, ContainerRequestContext requestContext) {
        String clientIp = extractClientIp(requestContext);
        String requestId = requestContext.getHeaderString(SecurityConstants.Header.REQUEST_ID);
        if (requestId == null) {
            requestId = java.util.UUID.randomUUID().toString();
        }

        AppContext appContext = AppContext.builder()
            .userId(claims.userId())
            .username(claims.username())
            .tenantId(claims.tenantId())
            .requestId(requestId)
            .clientIp(clientIp)
            .requestUri(requestContext.getUriInfo().getPath())
            .requestMethod(requestContext.getMethod())
            .roles(claims.roles() != null ? String.join(",", claims.roles()) : null)
            .build();

        AppContextHolder.setAppContext(appContext);
        AppContextHolder.syncAppContextToMdc();
    }

    /**
     * 提取客户端IP
     */
    private String extractClientIp(ContainerRequestContext requestContext) {
        String xForwardedFor = requestContext.getHeaderString(SecurityConstants.Header.X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = requestContext.getHeaderString(SecurityConstants.Header.X_REAL_IP);
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return "unknown";
    }
}
