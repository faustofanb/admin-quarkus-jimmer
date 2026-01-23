package io.github.faustofan.admin.shared.security.constants;

/**
 * 安全模块常量
 * <p>
 * 集中管理安全相关的常量，避免魔法字符串。
 * 包含缓存Key前缀、HTTP Header名称、Token类型等。
 */
public final class SecurityConstants {

    private SecurityConstants() {
        // 禁止实例化
    }

    // ===========================
    // 缓存Key前缀
    // ===========================
    public static final class CacheKey {
        /** 用户权限缓存前缀 */
        public static final String PERMISSION = "security:permission:";
        /** 用户角色缓存前缀 */
        public static final String ROLE = "security:role:";
        /** 会话信息缓存前缀 */
        public static final String SESSION = "security:session:";
        /** 用户会话索引前缀（用户ID -> 会话ID） */
        public static final String USER_SESSION = "security:user:session:";
        /** 验证码缓存前缀（预留扩展） */
        public static final String CAPTCHA = "security:captcha:";
        /** Token黑名单前缀 */
        public static final String TOKEN_BLACKLIST = "security:token:blacklist:";
        /** 登录失败次数前缀 */
        public static final String LOGIN_FAIL_COUNT = "security:login:fail:";

        private CacheKey() {}
    }

    // ===========================
    // HTTP Header
    // ===========================
    public static final class Header {
        /** 授权头 */
        public static final String AUTHORIZATION = "Authorization";
        /** Bearer Token前缀 */
        public static final String BEARER_PREFIX = "Bearer ";
        /** 租户ID头 */
        public static final String TENANT_ID = "X-Tenant-Id";
        /** 客户端IP头（代理转发） */
        public static final String X_FORWARDED_FOR = "X-Forwarded-For";
        /** 真实IP头 */
        public static final String X_REAL_IP = "X-Real-IP";
        /** 用户代理 */
        public static final String USER_AGENT = "User-Agent";
        /** 请求ID（链路追踪） */
        public static final String REQUEST_ID = "X-Request-Id";

        private Header() {}
    }

    // ===========================
    // Token类型
    // ===========================
    public static final class TokenType {
        /** 访问令牌 */
        public static final String ACCESS = "access";
        /** 刷新令牌 */
        public static final String REFRESH = "refresh";

        private TokenType() {}
    }

    // ===========================
    // JWT Claims
    // ===========================
    public static final class JwtClaim {
        public static final String USER_ID = "userId";
        public static final String USERNAME = "username";
        public static final String TENANT_ID = "tenantId";
        public static final String TOKEN_TYPE = "tokenType";
        public static final String DEPT_ID = "deptId";
        public static final String NICKNAME = "nickname";

        private JwtClaim() {}
    }

    // ===========================
    // 角色编码
    // ===========================
    public static final class Role {
        /** 超级管理员 */
        public static final String SUPER_ADMIN = "super_admin";
        /** 租户管理员 */
        public static final String TENANT_ADMIN = "tenant_admin";
        /** 普通用户 */
        public static final String USER = "user";

        private Role() {}
    }

    // ===========================
    // 认证类型（预留扩展）
    // ===========================
    public static final class AuthType {
        /** 用户名密码登录 */
        public static final String PASSWORD = "password";
        /** 短信验证码登录（预留） */
        public static final String SMS = "sms";
        /** 邮箱验证码登录（预留） */
        public static final String EMAIL = "email";
        /** 第三方OAuth2登录（预留） */
        public static final String OAUTH2 = "oauth2";
        /** LDAP登录（预留） */
        public static final String LDAP = "ldap";

        private AuthType() {}
    }

    // ===========================
    // 消息Topic
    // ===========================
    public static final class Topic {
        /** 登录事件Topic */
        public static final String LOGIN_EVENT = "admin.security.login";
        /** 登出事件Topic */
        public static final String LOGOUT_EVENT = "admin.security.logout";
        /** 权限拒绝事件Topic */
        public static final String PERMISSION_DENIED_EVENT = "admin.security.permission.denied";
        /** 会话过期事件Topic */
        public static final String SESSION_EXPIRED_EVENT = "admin.security.session.expired";

        private Topic() {}
    }

    // ===========================
    // 默认值
    // ===========================
    public static final class Default {
        /** 默认租户ID */
        public static final long TENANT_ID = 1L;
        /** 系统用户ID */
        public static final long SYSTEM_USER_ID = 0L;
        /** 系统用户名 */
        public static final String SYSTEM_USERNAME = "system";

        private Default() {}
    }
}
