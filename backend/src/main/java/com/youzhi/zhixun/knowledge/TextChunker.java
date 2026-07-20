package com.youzhi.zhixun.knowledge;

import java.util.ArrayList;
import java.util.List;

public final class TextChunker {
    private TextChunker() {
    }

    public static List<KnowledgeChunk> split(KnowledgeDocument document, int chunkChars, int overlapChars) {
        if (chunkChars < 100 || overlapChars < 0 || overlapChars >= chunkChars) {
            throw new IllegalArgumentException("Invalid chunking configuration");
        }
        List<KnowledgeChunk> chunks = new ArrayList<>();
        String content = document.content().strip();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkChars);
            if (end < content.length()) {
                end = findBoundary(content, start, end, chunkChars / 2);
            }
            String text = content.substring(start, end).strip();
            if (!text.isEmpty()) {
                chunks.add(toChunk(document, chunks.size(), text));
            }
            if (end >= content.length()) break;
            start = Math.max(start + 1, end - overlapChars);
        }
        return chunks;
    }

    private static int findBoundary(String content, int start, int end, int minimumLength) {
        int minimum = start + minimumLength;
        for (char separator : new char[]{'\n', '。', '！', '？', '；'}) {
            int candidate = content.lastIndexOf(separator, end - 1);
            if (candidate >= minimum) return candidate + 1;
        }
        return end;
    }

    private static KnowledgeChunk toChunk(KnowledgeDocument document, int index, String content) {
        return new KnowledgeChunk(
            document.documentId() + "#" + index,
            document.documentId(),
            document.title(),
            document.spaceId(),
            document.spaceName(),
            document.nodeId(),
            document.nodeName(),
            document.section(),
            document.updatedAt(),
            content,
            List.copyOf(document.allowedUserIds())
        );
    }
}
