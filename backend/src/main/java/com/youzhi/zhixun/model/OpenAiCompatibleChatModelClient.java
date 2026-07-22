package com.youzhi.zhixun.model;

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
import java.util.List;

@Component
public class OpenAiCompatibleChatModelClient implements ChatModelClient {
    private final RagProperties.Chat config;
    private final RestClient restClient;

    @Autowired
    public OpenAiCompatibleChatModelClient(RagProperties properties, RestClient.Builder builder) {
        this.config = properties.getChat();
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

    OpenAiCompatibleChatModelClient(RagProperties properties, RestClient restClient) {
        this.config = properties.getChat();
        this.restClient = restClient;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int maxOutputTokens) {
        validate(systemPrompt, userPrompt, maxOutputTokens);
        try {
            ChatResponse response = restClient.post()
                .uri("/chat/completions")
                .headers(headers -> headers.setBearerAuth(config.getApiKey()))
                .body(new ChatRequest(
                    config.getModel(),
                    List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                    0,
                    maxOutputTokens,
                    false,
                    new ResponseFormat("json_object")
                ))
                .retrieve()
                .body(ChatResponse.class);
            return validateResponse(response);
        } catch (RagException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable("对话模型暂时不可用");
        }
    }

    private void validate(String systemPrompt, String userPrompt, int maxOutputTokens) {
        boolean invalid = config.getApiKey() == null || config.getApiKey().isBlank()
            || config.getModel() == null || config.getModel().isBlank()
            || systemPrompt == null || systemPrompt.isBlank()
            || userPrompt == null || userPrompt.isBlank()
            || systemPrompt.length() + userPrompt.length() > config.getMaxRequestChars()
            || maxOutputTokens < 1 || maxOutputTokens > config.getMaxOutputTokens();
        if (invalid) throw unavailable("对话模型请求超过允许范围");
    }

    private String validateResponse(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().size() != 1) {
            throw unavailable("对话模型响应格式不合法");
        }
        Message message = response.choices().getFirst().message();
        String content = message == null ? null : message.content();
        if (content == null || content.isBlank() || content.length() > config.getMaxResponseChars()) {
            throw unavailable("对话模型响应超过允许范围");
        }
        return content.strip();
    }

    private RagException unavailable(String message) {
        return new RagException("RAG_CHAT_MODEL_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record ChatRequest(
        String model,
        List<Message> messages,
        double temperature,
        int max_tokens,
        boolean stream,
        ResponseFormat response_format
    ) {
    }

    private record ResponseFormat(String type) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(int index, Message message, String finish_reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String role, String content) {
    }
}
