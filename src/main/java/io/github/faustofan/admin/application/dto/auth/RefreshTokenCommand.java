package io.github.faustofan.admin.application.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新Token命令
 */
public record RefreshTokenCommand(
    @NotBlank(message = "刷新令牌不能为空")
    String refreshToken
) {
}
