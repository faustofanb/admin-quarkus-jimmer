package io.github.faustofan.admin.shared.security.config;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.time.Duration;

public interface SessionConfig {
    @WithDefault("PT30M")
    @WithName("timeout")
    Duration timeout();

    @WithDefault("false")
    @WithName("single-device-login")
    boolean singleDeviceLogin();

    @WithDefault("PT5M")
    @WithName("renew-threshold")
    Duration renewThreshold();

    @WithDefault("0")
    @WithName("max-online-users")
    int maxOnlineUsers();
}
