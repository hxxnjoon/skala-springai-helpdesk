package com.skala.helpdesk.config;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.skala.helpdesk.rag.IngestService;

/**
 * 인메모리 RAG 벡터스토어(SimpleVectorStore) — 외부 DB 없이 동작한다. {@code data/vectorstore.json}
 * 스냅샷이 있으면 재기동 시 그대로 불러와 임베딩 API를 다시 호출하지 않는다(비용 절감) —
 * 스냅샷이 없을 때만 {@code PolicyDocsRunner}가 처음 인제스트한다.
 */
@Configuration
class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File snapshot = IngestService.snapshotFile();
        if (snapshot.exists()) {
            store.load(snapshot);
            log.info("벡터스토어 스냅샷 로드: {}", snapshot);
        }
        return store;
    }
}
