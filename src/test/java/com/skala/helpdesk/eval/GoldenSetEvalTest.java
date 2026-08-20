package com.skala.helpdesk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.HelpDeskService;
import com.skala.helpdesk.rag.IngestService;

/**
 * 완료 기준 — 근거 키워드·출처가 모두 맞아야 한다. 실제 OpenAI 모델을 호출하므로 기본
 * {@code ./gradlew test}에서는 제외된다. {@code OPENAI_API_KEY}를 설정한 뒤
 * {@code ./gradlew test -Peval}로 실행한다.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("eval")
class GoldenSetEvalTest {

    private static final Logger log = LoggerFactory.getLogger(GoldenSetEvalTest.class);

    @Autowired
    private IngestService ingestService;
    @Autowired
    private HelpDeskService helpDeskService;

    @BeforeAll
    void ingest() {
        ingestService.ingestSampleDocs();
    }

    @Test
    @DisplayName("골든 세트 평가 — 근거 키워드·출처가 모두 맞아야 한다")
    void 골든_세트_평가() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Golden> golden;
        try (InputStream in = new ClassPathResource("eval/golden.json").getInputStream()) {
            golden = mapper.readValue(in, new TypeReference<List<Golden>>() {
            });
        }

        int pass = 0;
        for (Golden g : golden) {
            // 질문마다 새 세션 — 이전 질문의 대화 기록이 다음 답변에 섞이지 않게 한다.
            AnswerDto a = helpDeskService.ask("user1", "golden-" + UUID.randomUUID(), g.q());
            boolean hit = g.must().stream().allMatch(k -> a.answer().contains(k));
            boolean cite = g.src() == null
                    || a.sources().stream().anyMatch(s -> s.document().contains(g.src()));
            if (hit && cite) {
                pass++;
            }
            else {
                log.warn("실패: {}\n  답변: {}\n  출처: {}", g.q(), a.answer(), a.sources());
            }
        }
        log.info("통과 {}/{}", pass, golden.size());
        assertThat(pass).isGreaterThanOrEqualTo(8);
    }

    private record Golden(String q, List<String> must, String src) {
    }
}
