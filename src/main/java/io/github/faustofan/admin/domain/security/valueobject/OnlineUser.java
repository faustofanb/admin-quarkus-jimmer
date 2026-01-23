package io.github.faustofan.admin.domain.security.valueobject;

import java.time.Instant;

/**
 * 在线用户值对象
 * <p>
 * 封装在线用户的会话信息，用于会话管理和在线用户监控。
 *
 * @param sessionId      会话ID
 * @param userId         用户ID
 * @param username       用户名
 * @param nickname       昵称
 * @param tenantId       租户ID
 * @param deptId         部门ID
 * @param clientIp       客户端IP
 * @param userAgent      用户代理
 * @param loginTime      登录时间
 * @param lastAccessTime 最后访问时间
 */
public record OnlineUser(
    String sessionId,
    Long userId,
    String username,
    String nickname,
    Long tenantId,
    Long deptId,
    String clientIp,
    String userAgent,
    Instant loginTime,
    Instant lastAccessTime
) {

    /**
     * 创建在线用户
     */
    public static OnlineUser create(
        String sessionId,
        Long userId,
        String username,
        String nickname,
        Long tenantId,
        Long deptId,
        String clientIp,
        String userAgent
    ) {
        Instant now = Instant.now();
        return new OnlineUser(
            sessionId, userId, username, nickname, tenantId, deptId,
            clientIp, userAgent, now, now
        );
    }

    /**
     * 会话是否活跃（最后访问时间在指定分钟内）
     */
    public boolean isActive(int minutes) {
        if (lastAccessTime == null) {
            return false;
        }
        return Instant.now().minusSeconds(minutes * 60L).isBefore(lastAccessTime);
    }

    /**
     * 更新最后访问时间
     */
    public OnlineUser touch() {
        return new OnlineUser(
            sessionId, userId, username, nickname, tenantId, deptId,
            clientIp, userAgent, loginTime, Instant.now()
        );
    }

    /**
     * 在线时长（秒）
     */
    public long onlineSeconds() {
        if (loginTime == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - loginTime.getEpochSecond();
    }

    /**
     * 在线时长描述（如 "1小时30分钟"）
     */
    public String onlineDuration() {
        long seconds = onlineSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes);
        } else {
            return String.format("%d分钟", minutes);
        }
    }
}
