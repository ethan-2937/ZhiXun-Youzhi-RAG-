package com.youzhi.zhixun.knowledge;

import java.util.List;

public record KnowledgeDocument(
    String documentId,
    String title,
    String spaceId,
    String spaceName,
    String nodeId,
    String nodeName,
    String section,
    String updatedAt,
    String content,
    String sourceFile,
    String sourceFormat,
    List<String> allowedUserIds
) {
}
