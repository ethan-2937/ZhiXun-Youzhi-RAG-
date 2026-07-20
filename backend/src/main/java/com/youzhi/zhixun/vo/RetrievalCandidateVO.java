package com.youzhi.zhixun.vo;

public record RetrievalCandidateVO(
    int rank,
    String documentId,
    String chunkId,
    double score
) {
}
