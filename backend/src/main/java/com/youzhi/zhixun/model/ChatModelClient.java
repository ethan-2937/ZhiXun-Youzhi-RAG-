package com.youzhi.zhixun.model;

public interface ChatModelClient {
    String complete(String systemPrompt, String userPrompt, int maxOutputTokens);
}
