package com.youzhi.zhixun.embedding;

import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class OpenAiCompatibleEmbeddingClientTest {
    @Test
    void callsOpenAiCompatibleEndpointAndOrdersVectorsByIndex() {
        RagProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.invalid/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.invalid/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer fictional-embedding-key"))
            .andExpect(content().json("""
                {"model":"text-embedding-test","input":["文本一","文本二"],"encoding_format":"float"}
                """))
            .andRespond(withSuccess("""
                {"object":"list","model":"text-embedding-test","data":[
                  {"index":1,"embedding":[0.0,1.0,0.0]},
                  {"index":0,"embedding":[1.0,0.0,0.0]}
                ]}
                """, MediaType.APPLICATION_JSON));
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties, builder.build());

        List<float[]> vectors = client.embed(List.of("文本一", "文本二"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.getFirst()).containsExactly(1.0f, 0.0f, 0.0f);
        assertThat(vectors.getLast()).containsExactly(0.0f, 1.0f, 0.0f);
        server.verify();
    }

    @Test
    void rejectsWrongVectorDimensionWithoutReturningModelPayload() {
        RagProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.invalid/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.invalid/v1/embeddings"))
            .andRespond(withSuccess("""
                {"data":[{"index":0,"embedding":[1.0,0.0]}]}
                """, MediaType.APPLICATION_JSON));
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties, builder.build());

        assertThatThrownBy(() -> client.embed(List.of("测试文本")))
            .isInstanceOf(RagException.class)
            .hasMessage("Embedding 服务返回维度不一致");
        server.verify();
    }

    @Test
    void convertsTransportFailureToStableSafeError() {
        RagProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.invalid/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.invalid/v1/embeddings"))
            .andRespond(withException(new IOException("fictional timeout detail")));
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties, builder.build());

        assertThatThrownBy(() -> client.embed(List.of("测试文本")))
            .isInstanceOf(RagException.class)
            .hasMessage("Embedding 服务暂时不可用");
        server.verify();
    }

    @Test
    void rejectsOversizedInputBeforeAnyRequest() {
        RagProperties properties = properties();
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
            properties,
            RestClient.builder().baseUrl("https://example.invalid/v1").build()
        );

        assertThatThrownBy(() -> client.embed(List.of("字".repeat(101))))
            .isInstanceOf(RagException.class)
            .hasMessage("Embedding 输入超过允许范围");
    }

    private RagProperties properties() {
        RagProperties properties = new RagProperties();
        properties.getEmbedding().setApiKey("fictional-embedding-key");
        properties.getEmbedding().setModel("text-embedding-test");
        properties.getEmbedding().setDimension(3);
        properties.getEmbedding().setBatchSize(4);
        properties.getEmbedding().setMaxInputChars(100);
        return properties;
    }
}
