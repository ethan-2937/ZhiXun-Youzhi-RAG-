package com.youzhi.zhixun.dingtalk;

import com.youzhi.zhixun.config.AppAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenApiDingTalkIdentityClientTest {
    @Test
    void exchangesApplicationTokenAndOneTimeCodeWithoutNetwork() {
        AppAuthProperties properties = new AppAuthProperties();
        properties.getDingtalk().setClientId("fictional-client-id");
        properties.getDingtalk().setClientSecret("fictional-client-secret");
        properties.getDingtalk().setAccessTokenUrl("https://example.invalid/access-token");
        properties.getDingtalk().setUserByCodeUrl("https://example.invalid/user-by-code");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.invalid/access-token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {"appKey":"fictional-client-id","appSecret":"fictional-client-secret"}
                """))
            .andRespond(withSuccess("""
                {"accessToken":"fictional-access-token-value","expireIn":7200}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.invalid/user-by-code?access_token=fictional-access-token-value"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {"code":"fictional-one-time-code"}
                """))
            .andRespond(withSuccess("""
                {"errcode":0,"result":{"userid":"test-user-ding-003","unionid":"test-union-ding-003"}}
                """, MediaType.APPLICATION_JSON));
        OpenApiDingTalkIdentityClient client = new OpenApiDingTalkIdentityClient(properties, builder);

        DingTalkIdentity identity = client.exchangeAuthorizationCode("fictional-one-time-code");

        assertThat(identity.userId()).isEqualTo("test-user-ding-003");
        assertThat(identity.unionId()).isEqualTo("test-union-ding-003");
        server.verify();
    }
}
