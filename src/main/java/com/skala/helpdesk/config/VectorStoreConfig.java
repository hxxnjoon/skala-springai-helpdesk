package com.skala.helpdesk.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 인메모리 RAG 벡터스토어(SimpleVectorStore) — 외부 DB 없이 동작한다. 실제 문서 적재·스냅샷
 * 영속화는 {@code rag.IngestService}/{@code rag.PolicyDocsRunner}(Phase 2)가 담당한다.
 */
@Configuration
class VectorStoreConfig {

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
