package io.github.faustofan.admin.shared.security.config;

public interface SecurityConfig {
    boolean enabled();
    JwtConfig jwt();
    SessionConfig session();
    PasswordConfig password();
    WhitelistConfig whitelist();
}
