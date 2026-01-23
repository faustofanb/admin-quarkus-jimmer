package io.github.faustofan.admin.application.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录命令
 * <p>
 * 封装用户登录请求参数。
 */
public record LoginCommand(
    @NotBlank(message = "用户名不能为空")
    String username,

    @NotBlank(message = "密码不能为空")
    String password,

    String captchaCode,
    String captchaKey,
    Long tenantId
) {

    /**
     * 验证命令参数
     */
    public void validate() {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
    }
}
