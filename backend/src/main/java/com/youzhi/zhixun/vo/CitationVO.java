package com.youzhi.zhixun.vo;

public record CitationVO(
    String documentId,
    String title,
    String section,
    String excerpt,
    String updatedAt
) {
}
