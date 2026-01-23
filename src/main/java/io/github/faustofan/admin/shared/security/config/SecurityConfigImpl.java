package io.github.faustofan.admin.shared.security.config;

import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Singleton
public class SecurityConfigImpl implements SecurityConfig {

    @ConfigProperty(name = "app.security.enabled", defaultValue = "true")
    boolean enabled;

    // JWT
    @ConfigProperty(name = "app.security.jwt.issuer", defaultValue = "admin-quarkus-jimmer")
    String jwtIssuer;
    @ConfigProperty(name = "app.security.jwt.access-token-expiration", defaultValue = "PT30M")
    Duration jwtAccessTokenExpiration;
    @ConfigProperty(name = "app.security.jwt.refresh-token-expiration", defaultValue = "P7D")
    Duration jwtRefreshTokenExpiration;
    @ConfigProperty(name = "app.security.jwt.clock-skew", defaultValue = "PT1M")
    Duration jwtClockSkew;

    // Session
    @ConfigProperty(name = "app.security.session.timeout", defaultValue = "PT30M")
    Duration sessionTimeout;
    @ConfigProperty(name = "app.security.session.single-device-login", defaultValue = "false")
    boolean sessionSingleDeviceLogin;
    @ConfigProperty(name = "app.security.session.renew-threshold", defaultValue = "PT5M")
    Duration sessionRenewThreshold;
    @ConfigProperty(name = "app.security.session.max-online-users", defaultValue = "0")
    int sessionMaxOnlineUsers;

    // Password
    @ConfigProperty(name = "app.security.password.min-length", defaultValue = "6")
    int passwordMinLength;
    @ConfigProperty(name = "app.security.password.max-length", defaultValue = "32")
    int passwordMaxLength;
    @ConfigProperty(name = "app.security.password.bcrypt-strength", defaultValue = "10")
    int passwordBcryptStrength;
    @ConfigProperty(name = "app.security.password.expire-days", defaultValue = "0")
    int passwordExpireDays;
    @ConfigProperty(name = "app.security.password.lock-attempts", defaultValue = "5")
    int passwordLockAttempts;
    @ConfigProperty(name = "app.security.password.lock-duration", defaultValue = "PT30M")
    Duration passwordLockDuration;

    // Whitelist
    @ConfigProperty(name = "app.security.whitelist.paths")
    Optional<List<String>> whitelistPaths;
    
    @ConfigProperty(name = "app.security.whitelist.default-paths", defaultValue = "/auth/login,/auth/refresh,/q/health,/q/metrics,/openapi,/swagger-ui")
    String whitelistDefaultPaths;
    @ConfigProperty(name = "app.security.whitelist.enable-defaults", defaultValue = "true")
    boolean whitelistEnableDefaults;

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public JwtConfig jwt() {
        return new JwtConfig() {
            public String issuer() { return jwtIssuer; }
            public Duration accessTokenExpiration() { return jwtAccessTokenExpiration; }
            public Duration refreshTokenExpiration() { return jwtRefreshTokenExpiration; }
            public Duration clockSkew() { return jwtClockSkew; }
        };
    }

    @Override
    public SessionConfig session() {
        return new SessionConfig() {
            public Duration timeout() { return sessionTimeout; }
            public boolean singleDeviceLogin() { return sessionSingleDeviceLogin; }
            public Duration renewThreshold() { return sessionRenewThreshold; }
            public int maxOnlineUsers() { return sessionMaxOnlineUsers; }
        };
    }

    @Override
    public PasswordConfig password() {
        return new PasswordConfig() {
            public int minLength() { return passwordMinLength; }
            public int maxLength() { return passwordMaxLength; }
            public int bcryptStrength() { return passwordBcryptStrength; }
            public int expireDays() { return passwordExpireDays; }
            public int lockAttempts() { return passwordLockAttempts; }
            public Duration lockDuration() { return passwordLockDuration; }
        };
    }

    @Override
    public WhitelistConfig whitelist() {
        return new WhitelistConfig() {
            public List<String> paths() { return whitelistPaths.orElse(Collections.emptyList()); }
            public String defaultPaths() { return whitelistDefaultPaths; }
            public boolean enableDefaults() { return whitelistEnableDefaults; }
        };
    }
}
