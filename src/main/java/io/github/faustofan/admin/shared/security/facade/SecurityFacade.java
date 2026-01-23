package io.github.faustofan.admin.shared.security.facade;

import io.github.faustofan.admin.domain.security.authentication.AuthenticationResult;
import io.github.faustofan.admin.domain.security.authentication.Authenticator;
import io.github.faustofan.admin.domain.security.authentication.TokenProvider;
import io.github.faustofan.admin.domain.security.authorization.PermissionChecker;
import io.github.faustofan.admin.domain.security.event.LoginEvent;
import io.github.faustofan.admin.domain.security.event.LogoutEvent;
import io.github.faustofan.admin.domain.security.session.SessionManager;
import io.github.faustofan.admin.domain.security.valueobject.*;
import io.github.faustofan.admin.shared.messaging.core.DomainEvent;
import io.github.faustofan.admin.shared.messaging.facade.MessagingFacade;
import io.github.faustofan.admin.shared.security.config.SecurityConfig;
import io.github.faustofan.admin.shared.security.context.SecurityContext;
import io.github.faustofan.admin.shared.security.context.SecurityContextHolder;
import io.github.faustofan.admin.shared.security.exception.AuthenticationException;
import io.github.faustofan.admin.shared.security.exception.AuthorizationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 安全门面
 * <p>
 * 统一的安全模块入口，封装认证、授权、会话管理等功能。
 * 对外提供简洁的API，隐藏内部实现细节。
 *
 * <h3>核心功能：</h3>
 * <ul>
 *   <li>用户登录/登出</li>
 *   <li>Token刷新</li>
 *   <li>权限校验</li>
 *   <li>获取当前用户信息</li>
 * </ul>
 */
@ApplicationScoped
public class SecurityFacade {

    private static final Logger LOG = Logger.getLogger(SecurityFacade.class);

    private final SecurityConfig securityConfig;
    private final Authenticator authenticator;
    private final TokenProvider tokenProvider;
    private final SessionManager sessionManager;
    private final PermissionChecker permissionChecker;
    private final MessagingFacade messagingFacade;

    @Inject
    public SecurityFacade(
        SecurityConfig securityConfig,
        Authenticator authenticator,
        TokenProvider tokenProvider,
        SessionManager sessionManager,
        PermissionChecker permissionChecker,
        MessagingFacade messagingFacade
    ) {
        this.securityConfig = securityConfig;
        this.authenticator = authenticator;
        this.tokenProvider = tokenProvider;
        this.sessionManager = sessionManager;
        this.permissionChecker = permissionChecker;
        this.messagingFacade = messagingFacade;
        LOG.info("SecurityFacade initialized");
    }

    // ===========================
    // 认证相关
    // ===========================

    /**
     * 用户登录
     *
     * @param credential 登录凭证
     * @param clientIp   客户端IP
     * @param userAgent  用户代理
     * @return 登录信息（包含Token和用户信息）
     */
    public LoginInfo login(Credential credential, String clientIp, String userAgent) {
        LOG.infov("User login attempt: username={0}, tenantId={1}",
            credential.username(), credential.tenantId());

        try {
            // 1. 执行认证
            AuthenticationResult authResult = authenticator.authenticate(credential);

            // 2. 生成Token
            TokenClaims claims = TokenClaims.accessToken()
                .userId(authResult.userId())
                .username(authResult.username())
                .tenantId(authResult.tenantId())
                .deptId(authResult.deptId())
                .roles(authResult.roles())
                .issuedAt(Instant.now())
                .build();

            TokenPair tokenPair = tokenProvider.generateTokenPair(claims);

            // 3. 创建会话
            OnlineUser onlineUser = OnlineUser.create(
                null, // sessionId由SessionManager生成
                authResult.userId(),
                authResult.username(),
                authResult.nickname(),
                authResult.tenantId(),
                authResult.deptId(),
                clientIp,
                userAgent
            );
            String sessionId = sessionManager.createSession(onlineUser);

            // 4. 构建登录信息
            LoginInfo loginInfo = LoginInfo.builder()
                .userId(authResult.userId())
                .username(authResult.username())
                .nickname(authResult.nickname())
                .avatar(authResult.avatar())
                .tenantId(authResult.tenantId())
                .deptId(authResult.deptId())
                .roles(authResult.roles())
                .permissions(authResult.permissions())
                .tokenPair(tokenPair)
                .loginTime(Instant.now())
                .build();

            // 5. 发布登录成功事件
            publishLoginEvent(authResult, clientIp, userAgent, true, null);

            LOG.infov("User login success: userId={0}, username={1}",
                authResult.userId(), authResult.username());

            return loginInfo;

        } catch (Exception e) {
            // 发布登录失败事件
            publishLoginEvent(null, clientIp, userAgent, false, e.getMessage());
            LOG.warnv("User login failed: username={0}, reason={1}",
                credential.username(), e.getMessage());
            throw e;
        }
    }

    /**
     * 用户登出
     */
    public void logout() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (!context.isAuthenticated()) {
            LOG.debug("Logout called but user is not authenticated");
            return;
        }

        Long userId = context.requireUserId();
        String username = context.getUsername().orElse("unknown");
        Long tenantId = context.getTenantId().orElse(null);
        String sessionId = context.getSessionId().orElse(null);

        LOG.infov("User logout: userId={0}, username={1}", userId, username);

        // 1. 销毁会话
        sessionManager.destroyUserAllSessions(userId);

        // 2. 清除安全上下文
        SecurityContextHolder.clearContext();

        // 3. 发布登出事件
        publishLogoutEvent(userId, username, tenantId, sessionId);

        LOG.infov("User logout success: userId={0}", userId);
    }

    /**
     * 刷新Token
     *
     * @param refreshToken 刷新令牌
     * @return 新的Token对
     */
    public TokenPair refreshToken(String refreshToken) {
        LOG.debug("Refreshing token");

        // 1. 解析刷新令牌
        TokenClaims claims = tokenProvider.parseToken(refreshToken);

        // 2. 验证是否为刷新令牌
        if (!claims.isRefreshToken()) {
            throw AuthenticationException.invalidRefreshToken();
        }

        // 3. 验证会话是否存在
        if (!sessionManager.existsByUserId(claims.userId())) {
            throw AuthenticationException.sessionExpired();
        }

        // 4. 生成新的Token对
        TokenPair newTokenPair = tokenProvider.refreshToken(refreshToken);

        LOG.debugv("Token refreshed: userId={0}", claims.userId());

        return newTokenPair;
    }

    /**
     * 验证Token并解析
     *
     * @param token JWT字符串
     * @return Token声明
     */
    public TokenClaims validateAndParseToken(String token) {
        return tokenProvider.parseToken(token);
    }

    // ===========================
    // 授权相关
    // ===========================

    /**
     * 检查当前用户是否拥有指定权限
     */
    public boolean hasPermission(String permission) {
        SecurityContext context = SecurityContextHolder.getContext();
        if (!context.isAuthenticated()) {
            return false;
        }
        return context.hasPermission(permission);
    }

    /**
     * 检查当前用户是否拥有指定角色
     */
    public boolean hasRole(String role) {
        SecurityContext context = SecurityContextHolder.getContext();
        if (!context.isAuthenticated()) {
            return false;
        }
        return context.hasRole(role);
    }

    /**
     * 要求当前用户拥有指定权限，否则抛出异常
     */
    public void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw AuthorizationException.permissionDenied(permission);
        }
    }

    /**
     * 要求当前用户拥有指定角色，否则抛出异常
     */
    public void requireRole(String role) {
        if (!hasRole(role)) {
            throw AuthorizationException.roleDenied(role);
        }
    }

    /**
     * 获取用户权限列表
     */
    public List<String> getUserPermissions(Long userId) {
        return permissionChecker.getUserPermissions(userId);
    }

    /**
     * 获取用户角色列表
     */
    public List<String> getUserRoles(Long userId) {
        return permissionChecker.getUserRoles(userId);
    }

    // ===========================
    // 会话相关
    // ===========================

    /**
     * 获取当前用户信息
     */
    public Optional<SecurityContext> getCurrentUser() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (!context.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.of(context);
    }

    /**
     * 获取当前用户ID
     */
    public Optional<Long> getCurrentUserId() {
        return SecurityContextHolder.getContext().getUserId();
    }

    /**
     * 获取当前租户ID
     */
    public Optional<Long> getCurrentTenantId() {
        return SecurityContextHolder.getContext().getTenantId();
    }

    /**
     * 获取在线用户列表
     */
    public List<OnlineUser> getOnlineUsers() {
        return sessionManager.getOnlineUsers();
    }

    /**
     * 强制用户下线
     */
    public void forceOffline(Long userId, String reason) {
        sessionManager.forceOffline(userId, reason);
    }

    // ===========================
    // 私有辅助方法
    // ===========================

    private void publishLoginEvent(
        AuthenticationResult result, String clientIp, String userAgent,
        boolean success, String failReason
    ) {
        try {
            LoginEvent event = success
                ? LoginEvent.success(
                    result.userId(), result.username(), result.tenantId(),
                    clientIp, userAgent)
                : LoginEvent.failure(
                    result != null ? result.username() : "unknown",
                    result != null ? result.tenantId() : null,
                    clientIp, userAgent, failReason);

            String aggregateId = result != null ? String.valueOf(result.userId()) : "unknown";
            DomainEvent<LoginEvent> domainEvent = DomainEvent.created(aggregateId, "User", event);
            messagingFacade.fire(LoginEvent.TOPIC, domainEvent);
        } catch (Exception e) {
            LOG.warnv("Failed to publish login event: {0}", e.getMessage());
        }
    }

    private void publishLogoutEvent(Long userId, String username, Long tenantId, String sessionId) {
        try {
            LogoutEvent event = LogoutEvent.of(userId, username, tenantId, sessionId);
            DomainEvent<LogoutEvent> domainEvent = DomainEvent.created(String.valueOf(userId), "User", event);
            messagingFacade.fire(LogoutEvent.TOPIC, domainEvent);
        } catch (Exception e) {
            LOG.warnv("Failed to publish logout event: {0}", e.getMessage());
        }
    }
}
