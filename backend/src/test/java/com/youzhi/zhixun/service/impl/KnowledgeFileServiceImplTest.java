package com.youzhi.zhixun.service.impl;

import com.youzhi.zhixun.common.RagException;
import com.youzhi.zhixun.config.RagProperties;
import com.youzhi.zhixun.knowledge.KnowledgeDocument;
import com.youzhi.zhixun.knowledge.KnowledgeSource;
import com.youzhi.zhixun.service.KnowledgeFileDownload;
import com.youzhi.zhixun.vo.KnowledgeFilePreviewVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeFileServiceImplTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsAuthorizedPreviewAndOriginalDownload() throws IOException {
        Files.writeString(tempDir.resolve("虚构资料.pptx"), "fictional-file");
        KnowledgeFileServiceImpl service = service(List.of(
            document("doc-1", "node-1", "第一部分", "虚构正文一", "虚构资料.pptx", "test-user-1"),
            document("doc-2", "node-1", "第二部分", "虚构正文二", "虚构资料.pptx", "test-user-1")
        ));

        KnowledgeFilePreviewVO preview = service.preview("node-1", "test-user-1");
        KnowledgeFileDownload download = service.download("node-1", "test-user-1");

        assertThat(preview.sections()).hasSize(2);
        assertThat(preview.downloadAvailable()).isTrue();
        assertThat(download.fileName()).isEqualTo("虚构资料.pptx");
        assertThat(download.contentLength()).isEqualTo(14);
    }

    @Test
    void missingAndUnauthorizedFilesReturnTheSameSafeError() {
        KnowledgeFileServiceImpl service = service(List.of(
            document("doc-1", "node-private", "第一部分", "虚构正文", "虚构资料.pptx", "test-user-owner")
        ));

        assertNotFound(() -> service.preview("node-private", "test-user-other"));
        assertNotFound(() -> service.preview("node-missing", "test-user-other"));
    }

    @Test
    void archiveDownloadRequiresAuthorizationToEveryContainedDocument() throws IOException {
        Files.writeString(tempDir.resolve("虚构资料.zip"), "fictional-archive");
        KnowledgeFileServiceImpl service = service(List.of(
            document("doc-1", "node-visible", "公开部分", "虚构正文", "虚构资料.zip", "test-user-1"),
            document("doc-2", "node-private", "受限部分", "虚构正文", "虚构资料.zip", "test-user-2")
        ));

        KnowledgeFilePreviewVO preview = service.preview("node-visible", "test-user-1");

        assertThat(preview.downloadAvailable()).isFalse();
        assertNotFound(() -> service.download("node-visible", "test-user-1"));
    }

    @Test
    void rejectsRawFilePathTraversal() {
        KnowledgeFileServiceImpl service = service(List.of(
            document("doc-1", "node-1", "第一部分", "虚构正文", "../escape.pptx", "test-user-1")
        ));

        assertNotFound(() -> service.download("node-1", "test-user-1"));
    }

    @Test
    void boundsPreviewPayloadWithoutReturningAnotherSection() {
        KnowledgeFileServiceImpl service = service(List.of(
            document("doc-1", "node-1", "第一部分", "甲".repeat(80), "虚构资料.pptx", "test-user-1"),
            document("doc-2", "node-1", "第二部分", "乙".repeat(80), "虚构资料.pptx", "test-user-1")
        ));
        serviceProperties.setKnowledge(maxPreviewProperties(100));

        KnowledgeFilePreviewVO preview = service.preview("node-1", "test-user-1");

        assertThat(preview.truncated()).isTrue();
        assertThat(preview.sections()).hasSize(2);
        assertThat(preview.sections().stream().mapToInt(section -> section.content().length()).sum()).isEqualTo(100);
    }

    private RagProperties serviceProperties;

    private KnowledgeFileServiceImpl service(List<KnowledgeDocument> documents) {
        serviceProperties = new RagProperties();
        serviceProperties.setEnabled(true);
        serviceProperties.getKnowledge().setRawDirectory(tempDir.toString());
        serviceProperties.getKnowledge().setMaxRawFileBytes(1024);
        KnowledgeSource source = () -> documents;
        return new KnowledgeFileServiceImpl(serviceProperties, source);
    }

    private RagProperties.Knowledge maxPreviewProperties(int maxPreviewChars) {
        RagProperties.Knowledge knowledge = serviceProperties.getKnowledge();
        knowledge.setMaxPreviewChars(maxPreviewChars);
        return knowledge;
    }

    private KnowledgeDocument document(
        String documentId,
        String nodeId,
        String section,
        String content,
        String sourceFile,
        String allowedUserId
    ) {
        return new KnowledgeDocument(
            documentId,
            "虚构资料",
            "space-test",
            "虚构空间",
            nodeId,
            "虚构资料",
            section,
            "2026-07-01",
            content,
            sourceFile,
            sourceFile.endsWith(".zip") ? "zip" : "pptx",
            List.of(allowedUserId)
        );
    }

    private void assertNotFound(ThrowingRunnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(RagException.class)
            .extracting(exception -> ((RagException) exception).getCode())
            .isEqualTo("KNOWLEDGE_FILE_NOT_FOUND");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
