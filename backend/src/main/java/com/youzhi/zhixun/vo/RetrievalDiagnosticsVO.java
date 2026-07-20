package com.youzhi.zhixun.vo;

import java.util.List;

public record RetrievalDiagnosticsVO(
    String mode,
    boolean hasAuthorizedCandidate,
    List<RetrievalCandidateVO> candidates
) {
}
