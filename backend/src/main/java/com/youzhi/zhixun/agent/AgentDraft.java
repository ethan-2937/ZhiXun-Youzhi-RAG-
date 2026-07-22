package com.youzhi.zhixun.agent;

import java.util.List;

public record AgentDraft(String status, String answer, List<String> citationDocumentIds) {
}
