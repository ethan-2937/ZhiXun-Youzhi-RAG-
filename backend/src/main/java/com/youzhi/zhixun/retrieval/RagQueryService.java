package com.youzhi.zhixun.retrieval;

import com.youzhi.zhixun.vo.DemoChatResponseVO;
import com.youzhi.zhixun.vo.WorkspaceVO;

public interface RagQueryService {
    boolean isReady();

    WorkspaceVO workspace(String userId);

    DemoChatResponseVO answer(String question, String spaceId, String userId);
}
