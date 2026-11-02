package com.fishmatrix.reader.config;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.auth.IdentityHeaders;
import com.fishmatrix.common.auth.IdentitySigner;
import com.fishmatrix.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 身份验签的降级开关。
 *
 * <p><b>默认行为不变</b>：仍然用 {@code FM_IDENTITY_SECRET} 对身份头做 HMAC 校验，
 * 密钥不一致就拒绝（{@code INVALID_IDENTITY}）。</p>
 *
 * <p><b>为什么需要这个开关</b>：2026-10-29 集中机整体搬迁后，新网关的
 * {@code FM_IDENTITY_SECRET} 与分机侧不一致，而网关的部署配置在集中机上、分机这边拿不到，
 * 导致所有带身份的请求都被拒。把签名校验关掉可以让服务先恢复可用，
 * 但这等于「只要请求能连到分机，就能伪造任意用户」——是明确的安全降级。</p>
 *
 * <p><b>前提</b>：分机入站 8103/18103 的防火墙只允许网关所在机器（{@code 10.28.13.112}），
 * 关掉验签后安全性完全依赖这条防火墙规则。</p>
 *
 * <p><b>恢复方式</b>：拿到网关侧的密钥后，两侧配成同一个值，
 * 再把 {@code FM_AUTH_TRUST_UNSIGNED_HEADERS} 置回 {@code false}。</p>
 */
@Configuration
public class IdentityTrustConfig {

    private static final Logger log = LoggerFactory.getLogger(IdentityTrustConfig.class);

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    /**
     * 覆盖 tf-common 自动配置里的 {@link IdentitySigner}（那边标了
     * {@code @ConditionalOnMissingBean}，所以这里提供一个就不会再创建默认实现）。
     */
    @Bean
    @Primary
    public IdentitySigner fishIdentitySigner(
            @Value("${fm.auth.identity-secret:}") String identitySecret,
            @Value("${fm.auth.clock-skew-seconds:60}") long clockSkewSeconds,
            @Value("${fm.auth.trust-unsigned-headers:false}") boolean trustUnsignedHeaders) {
        IdentitySigner defaultSigner = new IdentitySigner(identitySecret, clockSkewSeconds);
        if (!trustUnsignedHeaders) {
            return defaultSigner;
        }
        log.warn("验签已降级：只解析身份头，不校验签名。" + "仅应在两侧密钥无法对齐时临时开启，配置 fm.auth.trust-unsigned-headers=false 可恢复");
        long clockSkew = Math.max(0, clockSkewSeconds);
        return new IdentitySigner(identitySecret, clockSkew) {
            @Override
            public CurrentUser verify(HttpServletRequest request) {
                return verifyWithoutSignature(request, clockSkew);
            }
        };
    }

    /**
     * 与 {@link IdentitySigner#verify} 相同的解析与过期校验，只跳过最后一步签名比对。
     */
    private static CurrentUser verifyWithoutSignature(HttpServletRequest request, long clockSkew) {
        String userId = request.getHeader(IdentityHeaders.USER_ID);
        String username = request.getHeader(IdentityHeaders.USERNAME);
        String nickname = request.getHeader(IdentityHeaders.NICKNAME);
        String role = request.getHeader(IdentityHeaders.ROLE);
        String roles = request.getHeader(IdentityHeaders.ROLES);
        String permissions = request.getHeader(IdentityHeaders.PERMISSIONS);
        String expiresAt = request.getHeader(IdentityHeaders.EXPIRES_AT);

        if (userId == null) {
            return null;
        }
        if (username == null
                || nickname == null
                || role == null
                || roles == null
                || permissions == null
                || expiresAt == null) {
            throw invalidIdentity();
        }

        long id;
        long exp;
        try {
            id = Long.parseLong(userId);
            exp = Long.parseLong(expiresAt);
        } catch (NumberFormatException exception) {
            throw invalidIdentity();
        }
        long now = Instant.now().getEpochSecond();
        if (exp + clockSkew < now) {
            throw ApiException.unauthorized("IDENTITY_EXPIRED", "登录状态已过期，请重新登录");
        }
        if (exp > now + IdentityHeaders.MAX_TTL_SECONDS + clockSkew) {
            throw invalidIdentity();
        }

        return new CurrentUser(
                id, decode(username), decode(nickname), decode(role), split(roles), Set.copyOf(split(permissions)));
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        Set<String> items = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) items.add(trimmed);
        }
        return new ArrayList<>(items);
    }

    private static String decode(String value) {
        try {
            return new String(B64.decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalidIdentity();
        }
    }

    private static ApiException invalidIdentity() {
        return ApiException.unauthorized("INVALID_IDENTITY", "身份信息无效，请重新登录");
    }
}
