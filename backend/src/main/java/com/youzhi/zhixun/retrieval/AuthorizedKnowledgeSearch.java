package com.youzhi.zhixun.retrieval;

import java.util.List;

public interface AuthorizedKnowledgeSearch {
    boolean hasAuthorizedKnowledge(String spaceId, String userId);

    List<AuthorizedEvidence> search(
        String question,
        String spaceId,
        String userId,
        int limit,
        double minScore
    );
}
