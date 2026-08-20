package com.skala.helpdesk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.skala.helpdesk.chat.HelpDeskService;
import com.skala.helpdesk.rag.IngestService;

/**
 * 비기능 요구사항 — P95 응답 5초 이내(비스트리밍). HTTP 서블릿 컨테이너 오버헤드를 빼고
 * 순수 모델 호출 지연만 재려고 {@link HelpDeskService}를 직접 호출한다. 동시 호출 수는
 * 임베딩·챗 API 비용을 고려해 30건으로 제한한다.
 */
@SpringBootTest
@Tag("eval")
class LoadTestP95Test {

    private static final Logger log = LoggerFactory.getLogger(LoadTestP95Test.class);
    private static final int TOTAL_CALLS = 30;
    private static final int CONCURRENCY = 10;

    @Autowired
    private HelpDeskService helpDeskService;
    @Autowired
    private IngestService ingestService;

    @Test
    @DisplayName("동시 요청 30건의 P95 응답 시간이 5초 이내다")
    void p95_응답은_5초_이내다() throws Exception {
        ingestService.ingestSampleDocs();

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < TOTAL_CALLS; i++) {
            String session = "loadtest-" + UUID.randomUUID();
            futures.add(pool.submit(() -> {
                long started = System.nanoTime();
                helpDeskService.ask("user1", session, "단순 변심 반품은 며칠 이내인가요?");
                return (System.nanoTime() - started) / 1_000_000;
            }));
        }

        List<Long> latenciesMs = new ArrayList<>();
        for (Future<Long> f : futures) {
            latenciesMs.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        Collections.sort(latenciesMs);
        long p95 = latenciesMs.get((int) Math.ceil(latenciesMs.size() * 0.95) - 1);
        log.info("지연 분포(ms, 정렬됨): {}", latenciesMs);
        log.info("P95: {}ms", p95);
        writeReport(latenciesMs, p95);

        assertThat(p95).isLessThanOrEqualTo(5000);
    }

    private void writeReport(List<Long> latenciesMs, long p95) throws IOException {
        Path dir = Path.of("loadtest");
        Files.createDirectories(dir);
        String report = "요청 수: %d, 동시성: %d\nP95: %dms\n전체 지연(ms): %s\n"
                .formatted(TOTAL_CALLS, CONCURRENCY, p95, latenciesMs);
        Files.writeString(dir.resolve("p95-report.txt"), report);
    }
}
