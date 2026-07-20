package com.youzhi.zhixun.service.impl;

import com.youzhi.zhixun.common.AuthException;
import com.youzhi.zhixun.config.AppAuthProperties;
import com.youzhi.zhixun.dingtalk.DingTalkIdentity;
import com.youzhi.zhixun.dingtalk.DingTalkIdentityClient;
import com.youzhi.zhixun.security.AuthenticatedPrincipal;
import com.youzhi.zhixun.service.AuthService;
import com.youzhi.zhixun.vo.AuthConfigVO;
import com.youzhi.zhixun.vo.CurrentUserVO;
import com.youzhi.zhixun.vo.DingTalkLoginRequestVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthServiceImpl implements AuthService {
    private static final int AUTHORIZATION_CODE_MAX_LENGTH = 512;

    private final AppAuthProperties properties;
    private final DingTalkIdentityClient identityClient;
    private final Map<String, Instant> consumedCodeDigests = new ConcurrentHashMap<>();

    public AuthServiceImpl(AppAuthProperties properties, DingTalkIdentityClient identityClient) {
        this.properties = properties;
        this.identityClient = identityClient;
    }

    @Override
    public AuthConfigVO config(String csrfToken) {
        boolean ready = properties.getMode() == AppAuthProperties.Mode.DINGTALK
            && hasText(properties.getAllowedCorpId())
            && hasText(properties.getDingtalk().getClientId())
            && hasText(properties.getDingtalk().getClientSecret());
        return new AuthConfigVO(
            properties.getMode().name().toLowerCase(),
            properties.getAllowedCorpId(),
            ready,
            AUTHORIZATION_CODE_MAX_LENGTH,
            csrfToken
        );
    }

    @Override
    public CurrentUserVO authenticateDemo(HttpServletRequest request) {
        if (properties.getMode() != AppAuthProperties.Mode.DEMO) {
            throw new AuthException("DEMO_LOGIN_DISABLED", "当前环境未启用演示登录", HttpStatus.FORBIDDEN);
        }
        AppAuthProperties.Demo demo = properties.getDemo();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            demo.getUserId(),
            "",
            demo.getDisplayName(),
            demo.getDepartment(),
            "DEMO",
            List.of("EMPLOYEE")
        );
        establishSession(principal, request);
        return toCurrentUser(principal);
    }

    @Override
    public CurrentUserVO authenticateDingTalk(DingTalkLoginRequestVO loginRequest, HttpServletRequest request) {
        if (properties.getMode() != AppAuthProperties.Mode.DINGTALK) {
            throw new AuthException("DINGTALK_LOGIN_DISABLED", "当前环境未启用钉钉免登", HttpStatus.FORBIDDEN);
        }
        if (!hasText(properties.getAllowedCorpId()) || !properties.getAllowedCorpId().equals(loginRequest.corpId())) {
            throw new AuthException("CORP_NOT_ALLOWED", "当前企业不在应用授权范围内", HttpStatus.FORBIDDEN);
        }
        if (!dingtalkReady()) {
            throw new AuthException("DINGTALK_CONFIG_INCOMPLETE", "钉钉免登配置尚未完成", HttpStatus.SERVICE_UNAVAILABLE);
        }

        reserveAuthorizationCode(loginRequest.code());
        DingTalkIdentity identity = identityClient.exchangeAuthorizationCode(loginRequest.code());
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            identity.userId(),
            safe(identity.unionId()),
            hasText(identity.displayName()) ? identity.displayName() : "钉钉用户",
            "待同步",
            "DINGTALK",
            List.of("EMPLOYEE")
        );
        establishSession(principal, request);
        return toCurrentUser(principal);
    }

    @Override
    public CurrentUserVO currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new AuthException("AUTH_REQUIRED", "请先完成身份认证", HttpStatus.UNAUTHORIZED);
        }
        return toCurrentUser(principal);
    }

    private void establishSession(AuthenticatedPrincipal principal, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            authorities
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private boolean dingtalkReady() {
        return properties.getMode() == AppAuthProperties.Mode.DINGTALK
            && hasText(properties.getAllowedCorpId())
            && hasText(properties.getDingtalk().getClientId())
            && hasText(properties.getDingtalk().getClientSecret());
    }

    private void reserveAuthorizationCode(String code) {
        Instant now = Instant.now();
        consumedCodeDigests.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        String digest = digest(code);
        Instant expiresAt = now.plus(properties.getReplayTtl());
        Instant previous = consumedCodeDigests.putIfAbsent(digest, expiresAt);
        if (previous != null && previous.isAfter(now)) {
            throw new AuthException("AUTH_CODE_REPLAYED", "免登码已被使用，请重新进入应用", HttpStatus.CONFLICT);
        }
    }

    private String digest(String code) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private CurrentUserVO toCurrentUser(AuthenticatedPrincipal principal) {
        return new CurrentUserVO(
            principal.userId(),
            principal.displayName(),
            principal.department(),
            principal.authSource(),
            principal.roles()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
