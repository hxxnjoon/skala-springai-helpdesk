package com.skala.helpdesk.rag;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Phase 2 — 사내 정책 문서를 청크로 나누고 메타데이터를 붙여 벡터스토어에 적재한다.
 *
 * <p><b>재인제스트 시 중복 방지</b> — 같은 source를 다시 넣으면 델타 없이 무조건 delete
 * 후 add한다. add만 반복하면 같은 문서의 청크가 계속 쌓여 검색 결과가 오염된다.
 */
@Service
public class IngestService {

    public static final String DOC_TYPE = "policy";
    public static final String DEPT = "cs";
    public static final String VERSION = "2026-08";

    private static final List<String> SAMPLE_SOURCES = List.of("return-policy", "shipping-policy", "membership");

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;

    public IngestService(VectorStore vectorStore, ResourceLoader resourceLoader) {
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
    }

    public List<IngestResult> ingestSampleDocs() {
        return SAMPLE_SOURCES.stream()
                .map(name -> ingest(resourceLoader.getResource("classpath:docs/" + name + ".md"),
                        name + ".md", DOC_TYPE, DEPT, VERSION))
                .toList();
    }

    public IngestResult ingest(Resource doc, String source, String docType, String dept, String version) {
        var reader = new TikaDocumentReader(doc);
        List<Document> raw = reader.get(); // Tika는 커스텀 메타데이터 훅이 없어 get() 이후 직접 부여한다

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", source);
        metadata.put("docType", docType);
        metadata.put("dept", dept);
        metadata.put("version", version);
        metadata.put("date", LocalDate.now().toString());
        raw.forEach(d -> d.getMetadata().putAll(metadata));

        var splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(350)
                .build();
        List<Document> chunks = splitter.apply(raw);

        vectorStore.delete(new FilterExpressionBuilder().eq("source", source).build()); // 재인덱싱 — 같은 출처를 지우고
        vectorStore.add(chunks); // 다시 넣는다
        return new IngestResult(source, chunks.size());
    }

    /** {@code data/vectorstore.json}으로 현재 인덱스를 스냅샷 저장한다(재시작 시 재사용). */
    public void persistSnapshot() {
        if (vectorStore instanceof SimpleVectorStore simple) {
            try {
                File file = snapshotFile();
                file.getParentFile().mkdirs();
                simple.save(file);
            }
            catch (Exception e) {
                throw new IllegalStateException("벡터스토어 스냅샷 저장 실패", e);
            }
        }
    }

    public static File snapshotFile() {
        return new File("data/vectorstore.json");
    }

    public record IngestResult(String source, int chunkCount) {
    }
}
