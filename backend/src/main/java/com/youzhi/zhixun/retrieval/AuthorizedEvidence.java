package com.youzhi.zhixun.retrieval;

public record AuthorizedEvidence(
    String documentId,
    String chunkId,
    String title,
    String section,
    String content,
    String updatedAt,
    double score
) {
}
