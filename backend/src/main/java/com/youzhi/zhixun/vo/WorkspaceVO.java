package com.youzhi.zhixun.vo;

import java.util.List;

public record WorkspaceVO(
    String productName,
    String releaseLabel,
    DemoUserVO user,
    List<KnowledgeSpaceVO> spaces,
    List<String> sampleQuestions,
    int indexedDocuments,
    int availableSpaces
) {
}
