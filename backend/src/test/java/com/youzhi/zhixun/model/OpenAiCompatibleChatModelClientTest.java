package com.youzhi.zhixun.model;

import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleChatModelClientTest {
    @Test
    void callsCompatibleChatEndpointWithBoundedDeterministicRequest() {
        RagProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.invalid/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.invalid/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer fictional-chat-key"))
            .andExpect(content().json("""
                {"model":"chat-test","messages":[
                  {"role":"system","content":"只返回 JSON"},
                  {"role":"user","content":"虚构问题"}
                ],"temperature":0.0,"max_tokens":64,"stream":false,
                "response_format":{"type":"json_object"}}
                """))
            .andRespond(withSuccess("""
                {"choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}
                """, MediaType.APPLICATION_JSON));
        OpenAiCompatibleChatModelClient client = new OpenAiCompatibleChatModelClient(
            properties, builder.build()
        );

        String response = client.complete("只返回 JSON", "虚构问题", 64);

        assertThat(response).isEqualTo("OK");
        server.verify();
    }

    @Test
    void rejectsOversizedPromptBeforeNetworkCall() {
        RagProperties properties = properties();
        properties.getChat().setMaxRequestChars(20);
        OpenAiCompatibleChatModelClient client = new OpenAiCompatibleChatModelClient(
            properties, RestClient.builder().baseUrl("https://example.invalid/v1").build()
        );

        assertThatThrownBy(() -> client.complete("系统提示", "字".repeat(30), 64))
            .isInstanceOf(RagException.class)
            .hasMessage("对话模型请求超过允许范围");
    }

    @Test
    void rejectsResponseLargerThanContract() {
        RagProperties properties = properties();
        properties.getChat().setMaxResponseChars(5);
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.invalid/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.invalid/v1/chat/completions"))
            .andRespond(withSuccess("""
                {"choices":[{"index":0,"message":{"role":"assistant","content":"123456"}}]}
                """, MediaType.APPLICATION_JSON));
        OpenAiCompatibleChatModelClient client = new OpenAiCompatibleChatModelClient(
            properties, builder.build()
        );

        assertThatThrownBy(() -> client.complete("系统", "问题", 64))
            .isInstanceOf(RagException.class)
            .hasMessage("对话模型响应超过允许范围");
        server.verify();
    }

    @Test
    void convertsTransportFailureToSafeError() {
        RagProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.invalid/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.invalid/v1/chat/completions"))
            .andRespond(withException(new IOException("fictional timeout detail")));
        OpenAiCompatibleChatModelClient client = new OpenAiCompatibleChatModelClient(
            properties, builder.build()
        );

        assertThatThrownBy(() -> client.complete("系统", "问题", 64))
            .isInstanceOf(RagException.class)
            .hasMessage("对话模型暂时不可用");
        server.verify();
    }

    private RagProperties properties() {
        RagProperties properties = new RagProperties();
        properties.getChat().setApiKey("fictional-chat-key");
        properties.getChat().setModel("chat-test");
        properties.getChat().setMaxRequestChars(1000);
        properties.getChat().setMaxResponseChars(1000);
        properties.getChat().setMaxOutputTokens(100);
        return properties;
    }
}
