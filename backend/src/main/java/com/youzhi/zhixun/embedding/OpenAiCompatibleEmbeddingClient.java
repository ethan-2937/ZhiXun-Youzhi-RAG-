package com.youzhi.zhixun.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.Comparator;
import java.util.List;

@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private final RagProperties.Embedding config;
    private final RestClient restClient;

    @Autowired
    public OpenAiCompatibleEmbeddingClient(RagProperties properties, RestClient.Builder builder) {
        this.config = properties.getEmbedding();
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(config.getConnectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.getReadTimeout());
        this.restClient = builder.clone()
            .baseUrl(trimTrailingSlash(config.getBaseUrl()))
            .requestFactory(requestFactory)
            .build();
    }

    OpenAiCompatibleEmbeddingClient(RagProperties properties, RestClient restClient) {
        this.config = properties.getEmbedding();
        this.restClient = restClient;
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        validateInputs(inputs);
        try {
            EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .headers(headers -> headers.setBearerAuth(config.getApiKey()))
                .body(new EmbeddingRequest(config.getModel(), inputs, "float"))
                .retrieve()
                .body(EmbeddingResponse.class);
            return validateResponse(response, inputs.size());
        } catch (RagException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable("Embedding 服务暂时不可用");
        }
    }

    private void validateInputs(List<String> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > config.getBatchSize()) {
            throw unavailable("Embedding 请求批次不合法");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw unavailable("Embedding 配置尚未完成");
        }
        for (String input : inputs) {
            if (input == null || input.isBlank() || input.length() > config.getMaxInputChars()) {
                throw unavailable("Embedding 输入超过允许范围");
            }
        }
    }

    private List<float[]> validateResponse(EmbeddingResponse response, int expectedCount) {
        if (response == null || response.data() == null || response.data().size() != expectedCount) {
            throw unavailable("Embedding 服务返回数量不一致");
        }
        List<EmbeddingData> ordered = response.data().stream()
            .sorted(Comparator.comparingInt(EmbeddingData::index))
            .toList();
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).index() != index) {
                throw unavailable("Embedding 服务返回索引不连续");
            }
        }
        return ordered.stream().map(this::toVector).toList();
    }

    private float[] toVector(EmbeddingData data) {
        if (data.embedding() == null || data.embedding().size() != config.getDimension()) {
            throw unavailable("Embedding 服务返回维度不一致");
        }
        float[] vector = new float[data.embedding().size()];
        for (int index = 0; index < vector.length; index++) {
            float value = data.embedding().get(index).floatValue();
            if (!Float.isFinite(value)) {
                throw unavailable("Embedding 服务返回无效数值");
            }
            vector[index] = value;
        }
        return vector;
    }

    private RagException unavailable(String message) {
        return new RagException("RAG_EMBEDDING_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record EmbeddingRequest(String model, List<String> input, String encoding_format) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(String object, String model, List<EmbeddingData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(int index, List<Double> embedding) {
    }
}
