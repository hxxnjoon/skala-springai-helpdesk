package com.skala.helpdesk.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

/**
 * VectorStore를 모킹해 임베딩 API를 호출하지 않고도 "재인제스트 시 delete-by-source가 add
 * 전에 실행된다"는 것 자체를 검증한다 — 실제 임베딩 호출은 eval 테스트에서 다룬다.
 */
class IngestServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ResourceLoader resourceLoader = mock(ResourceLoader.class);
    private final IngestService ingestService = new IngestService(vectorStore, resourceLoader);

    @Test
    @DisplayName("인제스트는 같은 source를 delete한 뒤 청크를 add한다")
    void 재인제스트는_같은_source를_지우고_다시_넣는다() {
        var doc = new ByteArrayResource("반품은 7일 이내 가능합니다.".getBytes());

        ingestService.ingest(doc, "return-policy.md", "policy", "cs", "2026-08");

        ArgumentCaptor<Filter.Expression> filterCaptor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isNotNull();

        ArgumentCaptor<List<Document>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunksCaptor.capture());
        List<Document> chunks = chunksCaptor.getValue();
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("source", "return-policy.md")
                .containsEntry("docType", "policy")
                .containsEntry("dept", "cs")
                .containsEntry("version", "2026-08");
    }
}
