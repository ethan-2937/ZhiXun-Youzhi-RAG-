package com.youzhi.zhixun.vo;

import java.util.List;

public record DemoChatResponseVO(
    String status,
    String answer,
    boolean grounded,
    String mode,
    List<CitationVO> citations,
    List<String> suggestedQuestions
) {
}
