package com.youzhi.zhixun.knowledge;

import java.util.List;

public record KnowledgeChunk(
    String chunkId,
    String documentId,
    String title,
    String spaceId,
    String spaceName,
    String nodeId,
    String nodeName,
    String section,
    String updatedAt,
    String content,
    List<String> allowedUserIds
) {
}
