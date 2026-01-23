package io.github.faustofan.admin.interfaces.rest.auth;

import io.github.faustofan.admin.domain.security.valueobject.Credential;
import io.github.faustofan.admin.domain.security.valueobject.LoginInfo;
import io.github.faustofan.admin.domain.security.valueobject.TokenPair;
import io.github.faustofan.admin.application.dto.auth.LoginCommand;
import io.github.faustofan.admin.application.dto.auth.RefreshTokenCommand;
import io.github.faustofan.admin.shared.security.context.SecurityContext;
import io.github.faustofan.admin.shared.security.context.SecurityContextHolder;
import io.github.faustofan.admin.shared.security.facade.SecurityFacade;
import io.github.faustofan.admin.shared.web.dtos.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * 认证REST接口
 * <p>
 * 提供用户认证相关的HTTP接口，包括登录、登出、刷新Token等。
 */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "认证管理", description = "用户登录、登出、Token刷新等接口")
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    SecurityFacade securityFacade;

    /**
     * 用户登录
     */
    @POST
    @Path("/login")
    @Operation(summary = "用户登录", description = "使用用户名密码登录，返回Token和用户信息")
    public ApiResponse<LoginInfo> login(@Valid LoginCommand command, @Context HttpHeaders headers) {
        command.validate();

        String clientIp = extractClientIp(headers);
        String userAgent = headers.getHeaderString("User-Agent");

        // 转换为Credential
        Credential credential = new Credential(
            command.username(),
            command.password(),
            command.captchaCode(),
            command.captchaKey(),
            command.tenantId(),
            "password"
        );

        LoginInfo loginInfo = securityFacade.login(credential, clientIp, userAgent);

        return ApiResponse.success(loginInfo);
    }

    /**
     * 用户登出
     */
    @POST
    @Path("/logout")
    @Operation(summary = "用户登出", description = "登出当前用户，销毁会话")
    public ApiResponse<Void> logout() {
        securityFacade.logout();
        return ApiResponse.success();
    }

    /**
     * 刷新Token
     */
    @POST
    @Path("/refresh")
    @Operation(summary = "刷新Token", description = "使用刷新令牌获取新的访问令牌")
    public ApiResponse<TokenPair> refreshToken(@Valid RefreshTokenCommand command) {
        TokenPair tokenPair = securityFacade.refreshToken(command.refreshToken());
        return ApiResponse.success(tokenPair);
    }

    /**
     * 获取当前用户信息
     */
    @GET
    @Path("/info")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的基本信息")
    public ApiResponse<UserInfoResponse> getCurrentUser() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (!context.isAuthenticated()) {
            return ApiResponse.success(null);
        }

        UserInfoResponse response = new UserInfoResponse(
            context.getUserId().orElse(null),
            context.getUsername().orElse(null),
            context.getTenantId().orElse(null),
            context.getDeptId().orElse(null),
            context.getRoles(),
            context.getPermissions()
        );

        return ApiResponse.success(response);
    }

    /**
     * 获取当前用户权限列表
     */
    @GET
    @Path("/permissions")
    @Operation(summary = "获取当前用户权限", description = "获取当前登录用户的权限标识列表")
    public ApiResponse<List<String>> getPermissions() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (!context.isAuthenticated()) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(context.getPermissions());
    }

    /**
     * 获取当前用户角色列表
     */
    @GET
    @Path("/roles")
    @Operation(summary = "获取当前用户角色", description = "获取当前登录用户的角色编码列表")
    public ApiResponse<List<String>> getRoles() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (!context.isAuthenticated()) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(context.getRoles());
    }

    // ===========================
    // 响应DTO
    // ===========================

    public record UserInfoResponse(
        Long userId,
        String username,
        Long tenantId,
        Long deptId,
        List<String> roles,
        List<String> permissions
    ) {}

    // ===========================
    // 私有辅助方法
    // ===========================

    private String extractClientIp(HttpHeaders headers) {
        String xForwardedFor = headers.getHeaderString("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // 取第一个IP（客户端真实IP）
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = headers.getHeaderString("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return "unknown";
    }
}
