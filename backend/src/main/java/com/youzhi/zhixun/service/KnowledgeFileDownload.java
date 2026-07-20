package com.youzhi.zhixun.service;

import org.springframework.core.io.Resource;

public record KnowledgeFileDownload(
    Resource resource,
    String fileName,
    String contentType,
    long contentLength
) {
}
