package com.skala.helpdesk.web;

import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.rag.IngestService;

/**
 * Phase 2 — 인제스트는 성공 메시지가 아니라 실제 결과로 확인해야 한다. 청크 조회 창구를
 * 열어 두면 여기서 안 잡힌 문제가 Phase 3에서 계속 이어지지 않는다.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final VectorStore vectorStore;
    private final IngestService ingestService;

    public AdminController(VectorStore vectorStore, IngestService ingestService) {
        this.vectorStore = vectorStore;
        this.ingestService = ingestService;
    }

    @GetMapping("/chunks")
    public List<Map<String, Object>> inspect(@RequestParam String q, @RequestParam(defaultValue = "5") int topK) {
        var hits = vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(topK).build());
        return hits.stream()
                .map(d -> Map.<String, Object>of(
                        "source", d.getMetadata().get("source"),
                        "version", d.getMetadata().get("version"),
                        "score", d.getScore(),
                        "preview", d.getText().substring(0, Math.min(160, d.getText().length()))))
                .toList();
    }

    @PostMapping("/ingest")
    public List<IngestService.IngestResult> reingest() {
        var results = ingestService.ingestSampleDocs();
        ingestService.persistSnapshot();
        return results;
    }
}
