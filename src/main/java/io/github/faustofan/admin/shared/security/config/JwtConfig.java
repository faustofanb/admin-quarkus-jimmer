package io.github.faustofan.admin.shared.security.config;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.time.Duration;

public interface JwtConfig {
    @WithDefault("admin-quarkus-jimmer")
    @WithName("issuer")
    String issuer();

    @WithDefault("PT30M")
    @WithName("access-token-expiration")
    Duration accessTokenExpiration();

    @WithDefault("P7D")
    @WithName("refresh-token-expiration")
    Duration refreshTokenExpiration();

    @WithDefault("PT1M")
    @WithName("clock-skew")
    Duration clockSkew();
}
