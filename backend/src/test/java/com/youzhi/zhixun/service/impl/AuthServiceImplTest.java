package com.youzhi.zhixun.service.impl;

import com.youzhi.zhixun.common.AuthException;
import com.youzhi.zhixun.config.AppAuthProperties;
import com.youzhi.zhixun.dingtalk.DingTalkIdentity;
import com.youzhi.zhixun.dingtalk.DingTalkIdentityClient;
import com.youzhi.zhixun.vo.CurrentUserVO;
import com.youzhi.zhixun.vo.DingTalkLoginRequestVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceImplTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiedStableIdentityCreatesSessionWithoutNameBasedAuthorization() {
        AppAuthProperties properties = dingtalkProperties();
        DingTalkIdentityClient client = code -> new DingTalkIdentity(
            "test-user-ding-001",
            "test-union-ding-001",
            "同名也只作展示"
        );
        AuthServiceImpl service = new AuthServiceImpl(properties, client);
        MockHttpServletRequest request = new MockHttpServletRequest();

        CurrentUserVO user = service.authenticateDingTalk(
            new DingTalkLoginRequestVO("fictional-code-001", "corp-test-001"),
            request
        );

        assertThat(user.userId()).isEqualTo("test-user-ding-001");
        assertThat(user.displayName()).isEqualTo("同名也只作展示");
        assertThat(user.authSource()).isEqualTo("DINGTALK");
        assertThat(request.getSession(false)).isNotNull();
    }

    @Test
    void wrongCorporationFailsBeforeCallingDingTalkAdapter() {
        AppAuthProperties properties = dingtalkProperties();
        AtomicInteger calls = new AtomicInteger();
        DingTalkIdentityClient client = code -> {
            calls.incrementAndGet();
            return new DingTalkIdentity("test-user", "test-union", "测试用户");
        };
        AuthServiceImpl service = new AuthServiceImpl(properties, client);

        assertThatThrownBy(() -> service.authenticateDingTalk(
            new DingTalkLoginRequestVO("fictional-code-002", "corp-not-allowed"),
            new MockHttpServletRequest()
        ))
            .isInstanceOf(AuthException.class)
            .hasMessage("当前企业不在应用授权范围内");
        assertThat(calls).hasValue(0);
    }

    @Test
    void authorizationCodeCannotBeReplayed() {
        AppAuthProperties properties = dingtalkProperties();
        AtomicInteger calls = new AtomicInteger();
        DingTalkIdentityClient client = code -> {
            calls.incrementAndGet();
            return new DingTalkIdentity("test-user-ding-002", "", "测试用户");
        };
        AuthServiceImpl service = new AuthServiceImpl(properties, client);
        DingTalkLoginRequestVO login = new DingTalkLoginRequestVO("fictional-replay-code", "corp-test-001");

        service.authenticateDingTalk(login, new MockHttpServletRequest());
        assertThatThrownBy(() -> service.authenticateDingTalk(login, new MockHttpServletRequest()))
            .isInstanceOf(AuthException.class)
            .hasMessage("免登码已被使用，请重新进入应用");
        assertThat(calls).hasValue(1);
    }

    @Test
    void demoLoginIsDisabledInDingTalkMode() {
        AuthServiceImpl service = new AuthServiceImpl(dingtalkProperties(), code -> null);

        assertThatThrownBy(() -> service.authenticateDemo(new MockHttpServletRequest()))
            .isInstanceOf(AuthException.class)
            .hasMessage("当前环境未启用演示登录");
    }

    private AppAuthProperties dingtalkProperties() {
        AppAuthProperties properties = new AppAuthProperties();
        properties.setMode(AppAuthProperties.Mode.DINGTALK);
        properties.setAllowedCorpId("corp-test-001");
        properties.getDingtalk().setClientId("fictional-client-id");
        properties.getDingtalk().setClientSecret("fictional-client-secret");
        return properties;
    }
}
