package io.github.faustofan.admin.shared.security.config;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.util.List;

public interface WhitelistConfig {
    @WithName("paths")
    @WithDefault("")
    List<String> paths();

    @WithDefault("/auth/login,/auth/refresh,/q/health,/q/metrics,/openapi,/swagger-ui")
    @WithName("default-paths")
    String defaultPaths();

    @WithDefault("true")
    @WithName("enable-defaults")
    boolean enableDefaults();
}
