package io.github.faustofan.admin.domain.security.event;

import io.github.faustofan.admin.shared.security.constants.SecurityConstants;
import java.time.Instant;
import java.util.UUID;

/**
 * 登录事件
 * <p>
 * 当用户登录（成功或失败）时发布此事件，用于：
 * <ul>
 *   <li>记录登录日志</li>
 *   <li>更新用户最后登录信息</li>
 *   <li>安全审计</li>
 *   <li>异常登录检测</li>
 * </ul>
 */
public record LoginEvent(
    String eventId,
    Long userId,
    String username,
    Long tenantId,
    String clientIp,
    String userAgent,
    boolean success,
    String failReason,
    Instant loginTime
) {

    /**
     * 事件Topic
     */
    public static final String TOPIC = SecurityConstants.Topic.LOGIN_EVENT;

    /**
     * 创建登录成功事件
     */
    public static LoginEvent success(
        Long userId,
        String username,
        Long tenantId,
        String clientIp,
        String userAgent
    ) {
        return new LoginEvent(
            UUID.randomUUID().toString(),
            userId,
            username,
            tenantId,
            clientIp,
            userAgent,
            true,
            null,
            Instant.now()
        );
    }

    /**
     * 创建登录失败事件
     */
    public static LoginEvent failure(
        String username,
        Long tenantId,
        String clientIp,
        String userAgent,
        String failReason
    ) {
        return new LoginEvent(
            UUID.randomUUID().toString(),
            null,
            username,
            tenantId,
            clientIp,
            userAgent,
            false,
            failReason,
            Instant.now()
        );
    }
}
