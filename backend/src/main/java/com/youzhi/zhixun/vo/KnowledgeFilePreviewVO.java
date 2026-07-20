package com.youzhi.zhixun.vo;

import java.util.List;

public record KnowledgeFilePreviewVO(
    String id,
    String title,
    String fileName,
    String fileType,
    String updatedAt,
    int sectionCount,
    boolean truncated,
    boolean downloadAvailable,
    List<KnowledgeFileSectionVO> sections
) {
}
