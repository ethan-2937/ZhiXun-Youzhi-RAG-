package com.youzhi.zhixun.retrieval;

import com.youzhi.zhixun.vo.RetrievalDiagnosticsVO;

public interface RetrievalDiagnosticsService {
    RetrievalDiagnosticsVO diagnose(String question, String spaceId, int limit, String userId);
}
