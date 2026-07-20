package com.youzhi.zhixun.service.impl;

import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import com.youzhi.zhixun.knowledge.KnowledgeDocument;
import com.youzhi.zhixun.knowledge.KnowledgeSource;
import com.youzhi.zhixun.service.KnowledgeFileDownload;
import com.youzhi.zhixun.service.KnowledgeFileService;
import com.youzhi.zhixun.vo.KnowledgeFilePreviewVO;
import com.youzhi.zhixun.vo.KnowledgeFileSectionVO;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeFileServiceImpl implements KnowledgeFileService {
    private final RagProperties properties;
    private final KnowledgeSource knowledgeSource;

    public KnowledgeFileServiceImpl(RagProperties properties, KnowledgeSource knowledgeSource) {
        this.properties = properties;
        this.knowledgeSource = knowledgeSource;
    }

    @Override
    public KnowledgeFilePreviewVO preview(String nodeId, String userId) {
        List<KnowledgeDocument> documents = loadDocuments();
        List<KnowledgeDocument> nodeDocuments = authorizedNodeDocuments(documents, nodeId, userId);
        KnowledgeDocument first = nodeDocuments.getFirst();
        int remaining = properties.getKnowledge().getMaxPreviewChars();
        boolean truncated = false;
        List<KnowledgeFileSectionVO> sections = new ArrayList<>();
        for (KnowledgeDocument document : nodeDocuments) {
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            String content = document.content();
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
                truncated = true;
            }
            sections.add(new KnowledgeFileSectionVO(document.documentId(), document.section(), content));
            remaining -= content.length();
        }
        return new KnowledgeFilePreviewVO(
            first.nodeId(),
            first.nodeName(),
            fileName(first.sourceFile()),
            first.sourceFormat(),
            first.updatedAt(),
            nodeDocuments.size(),
            truncated,
            sourceFullyAuthorized(documents, first.sourceFile(), userId),
            List.copyOf(sections)
        );
    }

    @Override
    public KnowledgeFileDownload download(String nodeId, String userId) {
        List<KnowledgeDocument> documents = loadDocuments();
        KnowledgeDocument first = authorizedNodeDocuments(documents, nodeId, userId).getFirst();
        if (!sourceFullyAuthorized(documents, first.sourceFile(), userId)) throw notFound();
        Path target = resolveRawFile(first.sourceFile());
        try {
            long size = Files.size(target);
            if (!Files.isRegularFile(target) || size <= 0 || size > properties.getKnowledge().getMaxRawFileBytes()) {
                throw notFound();
            }
            return new KnowledgeFileDownload(
                new FileSystemResource(target),
                target.getFileName().toString(),
                contentType(first.sourceFormat()),
                size
            );
        } catch (IOException exception) {
            throw notFound();
        }
    }

    private List<KnowledgeDocument> loadDocuments() {
        if (!properties.isEnabled()) throw notFound();
        return knowledgeSource.load();
    }

    private List<KnowledgeDocument> authorizedNodeDocuments(
        List<KnowledgeDocument> documents,
        String nodeId,
        String userId
    ) {
        if (nodeId == null || nodeId.isBlank() || nodeId.length() > 128 || userId == null || userId.isBlank()) {
            throw notFound();
        }
        List<KnowledgeDocument> authorized = documents.stream()
            .filter(document -> nodeId.equals(document.nodeId()))
            .filter(document -> authorized(document, userId))
            .toList();
        if (authorized.isEmpty()) throw notFound();
        String sourceFile = authorized.getFirst().sourceFile();
        if (authorized.stream().anyMatch(document -> !sourceFile.equals(document.sourceFile()))) throw notFound();
        return authorized;
    }

    private boolean sourceFullyAuthorized(List<KnowledgeDocument> documents, String sourceFile, String userId) {
        List<KnowledgeDocument> sourceDocuments = documents.stream()
            .filter(document -> sourceFile.equals(document.sourceFile()))
            .toList();
        return !sourceDocuments.isEmpty() && sourceDocuments.stream().allMatch(document -> authorized(document, userId));
    }

    private boolean authorized(KnowledgeDocument document, String userId) {
        return document.allowedUserIds() != null && document.allowedUserIds().contains(userId);
    }

    private Path resolveRawFile(String sourceFile) {
        String normalizedSource = sourceFile.replace('\\', '/');
        if (normalizedSource.startsWith("/")
            || normalizedSource.matches("^[A-Za-z]:.*")
            || normalizedSource.chars().anyMatch(character -> character < 32)
            || List.of(normalizedSource.split("/")).contains("..")) {
            throw notFound();
        }
        Path root = Path.of(properties.getKnowledge().getRawDirectory()).toAbsolutePath().normalize();
        Path target = root.resolve(normalizedSource).normalize();
        if (!target.startsWith(root)) throw notFound();
        try {
            if (Files.isSymbolicLink(target)) throw notFound();
            Path realRoot = root.toRealPath();
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(realRoot)) throw notFound();
            return realTarget;
        } catch (IOException exception) {
            throw notFound();
        }
    }

    private String fileName(String sourceFile) {
        String normalized = sourceFile.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private String contentType(String format) {
        return switch (format) {
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "zip" -> "application/zip";
            case "md" -> "text/markdown;charset=UTF-8";
            case "txt" -> "text/plain;charset=UTF-8";
            default -> "application/octet-stream";
        };
    }

    private RagException notFound() {
        return new RagException("KNOWLEDGE_FILE_NOT_FOUND", "资料不存在或当前账号无权访问", HttpStatus.NOT_FOUND);
    }
}
