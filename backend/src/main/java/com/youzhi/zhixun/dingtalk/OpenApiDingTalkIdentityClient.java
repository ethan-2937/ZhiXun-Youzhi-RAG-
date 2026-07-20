package com.youzhi.zhixun.dingtalk;

import com.youzhi.zhixun.common.AuthException;
import com.youzhi.zhixun.config.AppAuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class OpenApiDingTalkIdentityClient implements DingTalkIdentityClient {
    private final AppAuthProperties properties;
    private final RestClient restClient;
    private volatile CachedToken cachedToken;

    public OpenApiDingTalkIdentityClient(AppAuthProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public DingTalkIdentity exchangeAuthorizationCode(String code) {
        try {
            String accessToken = applicationAccessToken();
            URI uri = UriComponentsBuilder.fromUriString(properties.getDingtalk().getUserByCodeUrl())
                .queryParam("access_token", accessToken)
                .build(true)
                .toUri();
            Map<String, Object> response = restClient.post()
                .uri(uri)
                .body(Map.of("code", code))
                .retrieve()
                .body(Map.class);
            assertAccepted(response);
            Map<String, Object> result = nestedMap(response, "result");
            String userId = stringValue(result, List.of("userid", "userId"));
            String unionId = stringValue(result, List.of("unionid", "unionId", "associated_unionid"));
            if (!hasText(userId)) {
                throw authFailure("DINGTALK_IDENTITY_MISSING", "钉钉未返回稳定用户标识");
            }
            return new DingTalkIdentity(userId, unionId, "钉钉用户");
        } catch (AuthException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw authFailure("DINGTALK_AUTH_FAILED", "钉钉身份校验暂时失败");
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized String applicationAccessToken() {
        if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cachedToken.value();
        }
        AppAuthProperties.DingTalk config = properties.getDingtalk();
        if (!hasText(config.getClientId()) || !hasText(config.getClientSecret())) {
            throw authFailure("DINGTALK_CONFIG_INCOMPLETE", "钉钉应用凭据尚未配置");
        }
        try {
            Map<String, Object> response = restClient.post()
                .uri(config.getAccessTokenUrl())
                .body(Map.of("appKey", config.getClientId(), "appSecret", config.getClientSecret()))
                .retrieve()
                .body(Map.class);
            String value = stringValue(response, List.of("accessToken", "access_token"));
            if (!hasText(value)) {
                throw authFailure("DINGTALK_TOKEN_MISSING", "钉钉应用凭据获取失败");
            }
            long expiresIn = longValue(response, List.of("expireIn", "expiresIn", "expires_in"), 7200L);
            cachedToken = new CachedToken(value, Instant.now().plusSeconds(Math.max(120, expiresIn)));
            return value;
        } catch (AuthException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw authFailure("DINGTALK_TOKEN_FAILED", "钉钉应用凭据获取失败");
        }
    }

    private void assertAccepted(Map<String, Object> response) {
        Object errorCode = response == null ? null : response.get("errcode");
        if (errorCode != null && !"0".equals(String.valueOf(errorCode))) {
            throw authFailure("DINGTALK_CODE_REJECTED", "钉钉免登码无效或已过期");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> value, String key) {
        if (value != null && value.get(key) instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return value == null ? Map.of() : value;
    }

    private String stringValue(Map<String, Object> value, List<String> keys) {
        if (value == null) {
            return "";
        }
        for (String key : keys) {
            Object candidate = value.get(key);
            if (candidate != null && hasText(String.valueOf(candidate))) {
                return String.valueOf(candidate);
            }
        }
        return "";
    }

    private long longValue(Map<String, Object> value, List<String> keys, long fallback) {
        String candidate = stringValue(value, keys);
        try {
            return hasText(candidate) ? Long.parseLong(candidate) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private AuthException authFailure(String code, String message) {
        return new AuthException(code, message, HttpStatus.BAD_GATEWAY);
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
