package io.github.faustofan.admin.domain.security.session;

import io.github.faustofan.admin.domain.security.valueobject.OnlineUser;

import java.util.List;
import java.util.Optional;

/**
 * 会话管理器接口
 * <p>
 * 定义用户会话管理的核心契约。
 *
 * <h3>职责：</h3>
 * <ul>
 *   <li>会话创建与销毁</li>
 *   <li>在线用户查询</li>
 *   <li>会话续期</li>
 *   <li>强制下线</li>
 * </ul>
 *
 * <h3>实现：</h3>
 * <ul>
 *   <li>RedisSessionManager - 基于Redis的分布式会话管理（默认）</li>
 * </ul>
 */
public interface SessionManager {

    /**
     * 创建会话
     *
     * @param onlineUser 在线用户信息
     * @return 会话ID
     */
    String createSession(OnlineUser onlineUser);

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return 在线用户信息（不存在返回空）
     */
    Optional<OnlineUser> getSession(String sessionId);

    /**
     * 销毁会话
     *
     * @param sessionId 会话ID
     */
    void destroySession(String sessionId);

    /**
     * 续期会话
     * <p>
     * 更新会话的最后访问时间
     *
     * @param sessionId 会话ID
     */
    void renewSession(String sessionId);

    /**
     * 检查用户是否存在会话
     *
     * @param userId 用户ID
     * @return true-存在会话
     */
    boolean existsByUserId(Long userId);

    /**
     * 根据用户ID获取会话
     *
     * @param userId 用户ID
     * @return 在线用户信息（不存在返回空）
     */
    Optional<OnlineUser> getSessionByUserId(Long userId);

    /**
     * 销毁用户的所有会话
     * <p>
     * 用于单设备登录场景或强制下线
     *
     * @param userId 用户ID
     */
    void destroyUserAllSessions(Long userId);

    /**
     * 获取所有在线用户
     *
     * @return 在线用户列表
     */
    List<OnlineUser> getOnlineUsers();

    /**
     * 获取在线用户数量
     *
     * @return 在线用户数量
     */
    long getOnlineUserCount();

    /**
     * 强制某用户下线
     *
     * @param userId 用户ID
     * @param reason 下线原因
     */
    void forceOffline(Long userId, String reason);
}
