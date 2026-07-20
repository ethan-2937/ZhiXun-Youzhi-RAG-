package com.youzhi.zhixun.controller;

import com.youzhi.zhixun.security.AuthenticatedPrincipal;
import com.youzhi.zhixun.service.KnowledgeFileDownload;
import com.youzhi.zhixun.service.KnowledgeFileService;
import com.youzhi.zhixun.vo.KnowledgeFilePreviewVO;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/knowledge/files")
public class KnowledgeFileController {
    private final KnowledgeFileService knowledgeFileService;

    public KnowledgeFileController(KnowledgeFileService knowledgeFileService) {
        this.knowledgeFileService = knowledgeFileService;
    }

    @GetMapping("/{nodeId}")
    public KnowledgeFilePreviewVO preview(@PathVariable String nodeId, Authentication authentication) {
        return knowledgeFileService.preview(nodeId, subject(authentication));
    }

    @GetMapping("/{nodeId}/content")
    public ResponseEntity<Resource> download(@PathVariable String nodeId, Authentication authentication) {
        KnowledgeFileDownload download = knowledgeFileService.download(nodeId, subject(authentication));
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(download.fileName(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentType(MediaType.parseMediaType(download.contentType()))
            .contentLength(download.contentLength())
            .body(download.resource());
    }

    private String subject(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) return principal.userId();
        return authentication.getName();
    }
}
