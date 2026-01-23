package io.github.faustofan.admin.infrastructure.security.jwt;

import io.github.faustofan.admin.domain.security.authentication.TokenProvider;
import io.github.faustofan.admin.domain.security.valueobject.TokenClaims;
import io.github.faustofan.admin.domain.security.valueobject.TokenPair;
import io.github.faustofan.admin.shared.security.config.SecurityConfig;
import io.github.faustofan.admin.shared.security.constants.SecurityConstants;
import io.github.faustofan.admin.shared.security.exception.AuthenticationException;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/**
 * JWT Token提供者实现
 * <p>
 * 基于 SmallRye JWT 实现Token的生成和解析。
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>双Token机制（Access + Refresh）</li>
 *   <li>RS256 非对称加密签名</li>
 *   <li>支持Token刷新</li>
 * </ul>
 */
@ApplicationScoped
public class JwtTokenProvider implements TokenProvider {

    private static final Logger LOG = Logger.getLogger(JwtTokenProvider.class);

    private final SecurityConfig securityConfig;
    private final JWTParser jwtParser;

    @Inject
    public JwtTokenProvider(SecurityConfig securityConfig, JWTParser jwtParser) {
        this.securityConfig = securityConfig;
        this.jwtParser = jwtParser;
    }

    @Override
    public TokenPair generateTokenPair(TokenClaims claims) {
        LOG.debugv("Generating token pair for user: {0}", claims.userId());

        Instant now = Instant.now();
        Duration accessExpiration = securityConfig.jwt().accessTokenExpiration();
        Duration refreshExpiration = securityConfig.jwt().refreshTokenExpiration();

        // 生成Access Token
        Instant accessExpireTime = now.plus(accessExpiration);
        String accessToken = buildToken(claims, SecurityConstants.TokenType.ACCESS, accessExpireTime);

        // 生成Refresh Token
        Instant refreshExpireTime = now.plus(refreshExpiration);
        String refreshToken = buildToken(claims, SecurityConstants.TokenType.REFRESH, refreshExpireTime);

        return new TokenPair(accessToken, refreshToken, accessExpireTime, refreshExpireTime);
    }

    @Override
    public TokenClaims parseToken(String token) {
        try {
            // 使用注入的JWTParser解析Token
            JsonWebToken parsedJwt = jwtParser.parse(token);

            Long userId = parsedJwt.getClaim(SecurityConstants.JwtClaim.USER_ID);
            String username = parsedJwt.getClaim(SecurityConstants.JwtClaim.USERNAME);
            Long tenantId = parsedJwt.getClaim(SecurityConstants.JwtClaim.TENANT_ID);
            Long deptId = parsedJwt.getClaim(SecurityConstants.JwtClaim.DEPT_ID);
            String tokenType = parsedJwt.getClaim(SecurityConstants.JwtClaim.TOKEN_TYPE);
            List<String> roles = List.copyOf(parsedJwt.getGroups());

            Instant issuedAt = Instant.ofEpochSecond(parsedJwt.getIssuedAtTime());
            Instant expiresAt = Instant.ofEpochSecond(parsedJwt.getExpirationTime());

            return new TokenClaims(userId, username, tenantId, deptId, tokenType, roles, issuedAt, expiresAt);
        } catch (Exception e) {
            LOG.warnv("Failed to parse token: {0}", e.getMessage());
            throw AuthenticationException.invalidToken();
        }
    }

    @Override
    public TokenPair refreshToken(String refreshToken) {
        TokenClaims claims = parseToken(refreshToken);

        if (claims.isExpired()) {
            throw AuthenticationException.refreshTokenExpired();
        }

        if (!claims.isRefreshToken()) {
            throw AuthenticationException.invalidRefreshToken();
        }

        // 使用原有的claims信息生成新的Token对
        return generateTokenPair(claims);
    }

    @Override
    public boolean validateToken(String token) {
        try {
            TokenClaims claims = parseToken(token);
            return !claims.isExpired();
        } catch (Exception e) {
            return false;
        }
    }

    // ===========================
    // 私有辅助方法
    // ===========================

    private String buildToken(TokenClaims claims, String tokenType, Instant expireTime) {
        String issuer = securityConfig.jwt().issuer();

        JwtClaimsBuilder builder = Jwt.claims()
            .issuer(issuer)
            .subject(claims.username())
            .issuedAt(Instant.now())
            .expiresAt(expireTime)
            .claim(SecurityConstants.JwtClaim.USER_ID, claims.userId())
            .claim(SecurityConstants.JwtClaim.USERNAME, claims.username())
            .claim(SecurityConstants.JwtClaim.TOKEN_TYPE, tokenType);

        // 添加租户ID
        if (claims.tenantId() != null) {
            builder.claim(SecurityConstants.JwtClaim.TENANT_ID, claims.tenantId());
        }

        // 添加部门ID
        if (claims.deptId() != null) {
            builder.claim(SecurityConstants.JwtClaim.DEPT_ID, claims.deptId());
        }

        // 添加角色
        if (claims.roles() != null && !claims.roles().isEmpty()) {
            builder.groups(new HashSet<>(claims.roles()));
        }

        return builder.sign();
    }
}
