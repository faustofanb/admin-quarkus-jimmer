package io.github.faustofan.admin.domain.security.event;

import io.github.faustofan.admin.shared.security.constants.SecurityConstants;
import java.time.Instant;
import java.util.UUID;

/**
 * 权限拒绝事件
 * <p>
 * 当用户访问受限资源被拒绝时发布此事件，用于：
 * <ul>
 *   <li>安全审计</li>
 *   <li>异常行为检测</li>
 *   <li>告警通知</li>
 * </ul>
 */
public record PermissionDeniedEvent(
    String eventId,
    Long userId,
    String username,
    Long tenantId,
    String requestUri,
    String requestMethod,
    String requiredPermission,
    String clientIp,
    Instant deniedAt
) {

    /**
     * 事件Topic
     */
    public static final String TOPIC = SecurityConstants.Topic.PERMISSION_DENIED_EVENT;

    /**
     * 创建权限拒绝事件
     */
    public static PermissionDeniedEvent of(
        Long userId,
        String username,
        Long tenantId,
        String requestUri,
        String requestMethod,
        String requiredPermission,
        String clientIp
    ) {
        return new PermissionDeniedEvent(
            UUID.randomUUID().toString(),
            userId,
            username,
            tenantId,
            requestUri,
            requestMethod,
            requiredPermission,
            clientIp,
            Instant.now()
        );
    }
}
