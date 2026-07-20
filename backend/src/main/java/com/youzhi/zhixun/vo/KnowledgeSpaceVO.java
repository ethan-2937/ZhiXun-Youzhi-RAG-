package com.youzhi.zhixun.vo;

import java.util.List;

public record KnowledgeSpaceVO(
    String id,
    String name,
    String description,
    int documentCount,
    List<KnowledgeNodeVO> nodes
) {
}
