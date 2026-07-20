package com.youzhi.zhixun.embedding;

import java.util.List;

public interface EmbeddingClient {
    List<float[]> embed(List<String> inputs);
}
