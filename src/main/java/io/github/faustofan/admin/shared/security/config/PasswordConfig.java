package io.github.faustofan.admin.shared.security.config;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.time.Duration;

public interface PasswordConfig {
    @WithDefault("6")
    @WithName("min-length")
    int minLength();

    @WithDefault("32")
    @WithName("max-length")
    int maxLength();

    @WithDefault("10")
    @WithName("bcrypt-strength")
    int bcryptStrength();

    @WithDefault("0")
    @WithName("expire-days")
    int expireDays();

    @WithDefault("5")
    @WithName("lock-attempts")
    int lockAttempts();

    @WithDefault("PT30M")
    @WithName("lock-duration")
    Duration lockDuration();
}
