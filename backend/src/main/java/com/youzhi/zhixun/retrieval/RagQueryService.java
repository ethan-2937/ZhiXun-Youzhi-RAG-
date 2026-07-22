package com.youzhi.zhixun.retrieval;

import com.youzhi.zhixun.vo.DemoChatResponseVO;
import com.youzhi.zhixun.vo.WorkspaceVO;

public interface RagQueryService {
    boolean isReady();

    default String mode() {
        return isReady() ? "REAL_EMBEDDING_RETRIEVAL" : "DEMO_FIXTURE";
    }

    WorkspaceVO workspace(String userId);

    DemoChatResponseVO answer(String question, String spaceId, String userId);
}
