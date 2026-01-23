package io.github.faustofan.admin.infrastructure.security.session;

import io.github.faustofan.admin.domain.security.session.SessionManager;
import io.github.faustofan.admin.domain.security.valueobject.OnlineUser;
import io.github.faustofan.admin.shared.cache.CacheFacade;
import io.github.faustofan.admin.shared.distributed.DistributedFacade;
import io.github.faustofan.admin.shared.security.config.SecurityConfig;
import io.github.faustofan.admin.shared.security.constants.SecurityConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Redis会话管理器实现
 * <p>
 * 基于Redis实现分布式会话管理，利用现有的CacheFacade和DistributedFacade。
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>会话创建与销毁</li>
 *   <li>在线用户查询</li>
 *   <li>会话续期</li>
 *   <li>强制下线</li>
 *   <li>单设备登录支持</li>
 * </ul>
 */
@ApplicationScoped
public class RedisSessionManager implements SessionManager {

    private static final Logger LOG = Logger.getLogger(RedisSessionManager.class);

    private final CacheFacade cacheFacade;
    private final DistributedFacade distributedFacade;
    private final SecurityConfig securityConfig;

    @Inject
    public RedisSessionManager(
        CacheFacade cacheFacade,
        DistributedFacade distributedFacade,
        SecurityConfig securityConfig
    ) {
        this.cacheFacade = cacheFacade;
        this.distributedFacade = distributedFacade;
        this.securityConfig = securityConfig;
    }

    @Override
    public String createSession(OnlineUser onlineUser) {
        String sessionId = generateSessionId();
        String sessionKey = SecurityConstants.CacheKey.SESSION + sessionId;
        String userSessionKey = SecurityConstants.CacheKey.USER_SESSION + onlineUser.userId();

        Duration sessionTimeout = securityConfig.session().timeout();

        // 1. 检查用户是否已有会话（单设备登录场景）
        if (securityConfig.session().singleDeviceLogin()) {
            destroyUserAllSessions(onlineUser.userId());
        }

        // 2. 创建带sessionId的OnlineUser
        OnlineUser sessionUser = new OnlineUser(
            sessionId,
            onlineUser.userId(),
            onlineUser.username(),
            onlineUser.nickname(),
            onlineUser.tenantId(),
            onlineUser.deptId(),
            onlineUser.clientIp(),
            onlineUser.userAgent(),
            onlineUser.loginTime(),
            onlineUser.lastAccessTime()
        );

        // 3. 存储会话
        cacheFacade.put(sessionKey, sessionUser, sessionTimeout);

        // 4. 建立用户->会话的索引
        cacheFacade.put(userSessionKey, sessionId, sessionTimeout);

        LOG.infov("Session created: sessionId={0}, userId={1}", sessionId, onlineUser.userId());

        return sessionId;
    }

    @Override
    public Optional<OnlineUser> getSession(String sessionId) {
        String sessionKey = SecurityConstants.CacheKey.SESSION + sessionId;
        return cacheFacade.get(sessionKey, OnlineUser.class);
    }

    @Override
    public void destroySession(String sessionId) {
        Optional<OnlineUser> sessionOpt = getSession(sessionId);
        if (sessionOpt.isPresent()) {
            OnlineUser session = sessionOpt.get();
            String sessionKey = SecurityConstants.CacheKey.SESSION + sessionId;
            String userSessionKey = SecurityConstants.CacheKey.USER_SESSION + session.userId();

            cacheFacade.invalidate(sessionKey);
            cacheFacade.invalidate(userSessionKey);

            LOG.infov("Session destroyed: sessionId={0}, userId={1}", sessionId, session.userId());
        }
    }

    @Override
    public void renewSession(String sessionId) {
        Optional<OnlineUser> sessionOpt = getSession(sessionId);
        if (sessionOpt.isPresent()) {
            OnlineUser onlineUser = sessionOpt.get().touch();
            String sessionKey = SecurityConstants.CacheKey.SESSION + sessionId;
            String userSessionKey = SecurityConstants.CacheKey.USER_SESSION + onlineUser.userId();

            Duration sessionTimeout = securityConfig.session().timeout();
            cacheFacade.put(sessionKey, onlineUser, sessionTimeout);
            cacheFacade.put(userSessionKey, sessionId, sessionTimeout);

            LOG.debugv("Session renewed: sessionId={0}", sessionId);
        }
    }

    @Override
    public boolean existsByUserId(Long userId) {
        String userSessionKey = SecurityConstants.CacheKey.USER_SESSION + userId;
        return cacheFacade.get(userSessionKey, String.class).isPresent();
    }

    @Override
    public Optional<OnlineUser> getSessionByUserId(Long userId) {
        String userSessionKey = SecurityConstants.CacheKey.USER_SESSION + userId;
        Optional<String> sessionIdOpt = cacheFacade.get(userSessionKey, String.class);
        return sessionIdOpt.flatMap(this::getSession);
    }

    @Override
    public void destroyUserAllSessions(Long userId) {
        Optional<String> sessionIdOpt = cacheFacade.get(
            SecurityConstants.CacheKey.USER_SESSION + userId, String.class);
        sessionIdOpt.ifPresent(this::destroySession);
    }

    @Override
    public List<OnlineUser> getOnlineUsers() {
        // 注意：此实现需要使用Redis SCAN命令获取所有会话
        // 当前简化版直接返回空列表，后续可扩展
        LOG.debug("getOnlineUsers: Not fully implemented, returning empty list");
        return List.of();
    }

    @Override
    public long getOnlineUserCount() {
        // 当前简化版，后续可通过维护计数器优化
        return getOnlineUsers().size();
    }

    @Override
    public void forceOffline(Long userId, String reason) {
        LOG.infov("Force offline: userId={0}, reason={1}", userId, reason);
        destroyUserAllSessions(userId);
        // 可以在这里发布强制下线事件，通知前端
    }

    // ===========================
    // 私有辅助方法
    // ===========================

    private String generateSessionId() {
        return distributedFacade.nextIdStr();
    }
}
