package com.youzhi.zhixun.vo;

public record KnowledgeNodeVO(
    String id,
    String title,
    String type,
    int itemCount,
    String fileType,
    String updatedAt
) {
    public KnowledgeNodeVO(String id, String title, String type, int itemCount) {
        this(id, title, type, itemCount, "", "");
    }
}
