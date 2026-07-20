package com.youzhi.zhixun.service;

import com.youzhi.zhixun.vo.KnowledgeFilePreviewVO;

public interface KnowledgeFileService {
    KnowledgeFilePreviewVO preview(String nodeId, String userId);

    KnowledgeFileDownload download(String nodeId, String userId);
}
