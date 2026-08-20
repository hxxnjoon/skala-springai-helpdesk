package com.skala.helpdesk.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 앱 최초 기동 시 샘플 정책 문서를 자동 인제스트한다. {@code data/vectorstore.json} 스냅샷이
 * 이미 있으면 건너뛴다 — 재시작마다 임베딩 API를 다시 호출하지 않기 위해서다. 문서를 수정한
 * 뒤 재인제스트하려면 관리자 엔드포인트({@code POST /api/admin/ingest})를 수동으로 호출한다.
 */
@Component
public class PolicyDocsRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicyDocsRunner.class);

    private final IngestService ingestService;

    public PolicyDocsRunner(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (IngestService.snapshotFile().exists()) {
            log.info("벡터스토어 스냅샷이 이미 있어 자동 인제스트를 건너뜁니다.");
            return;
        }
        var results = ingestService.ingestSampleDocs();
        ingestService.persistSnapshot();
        log.info("정책 문서 자동 인제스트 완료: {}", results);
    }
}
