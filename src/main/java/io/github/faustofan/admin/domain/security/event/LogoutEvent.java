package io.github.faustofan.admin.domain.security.event;

import io.github.faustofan.admin.shared.security.constants.SecurityConstants;
import java.time.Instant;
import java.util.UUID;

/**
 * 登出事件
 * <p>
 * 当用户登出时发布此事件，用于：
 * <ul>
 *   <li>记录登出日志</li>
 *   <li>清理用户会话</li>
 *   <li>安全审计</li>
 * </ul>
 */
public record LogoutEvent(
    String eventId,
    Long userId,
    String username,
    Long tenantId,
    String sessionId,
    Instant logoutTime
) {

    /**
     * 事件Topic
     */
    public static final String TOPIC = SecurityConstants.Topic.LOGOUT_EVENT;

    /**
     * 创建登出事件
     */
    public static LogoutEvent of(
        Long userId,
        String username,
        Long tenantId,
        String sessionId
    ) {
        return new LogoutEvent(
            UUID.randomUUID().toString(),
            userId,
            username,
            tenantId,
            sessionId,
            Instant.now()
        );
    }
}
