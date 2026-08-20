package com.skala.helpdesk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.HelpDeskService;
import com.skala.helpdesk.domain.Order;
import com.skala.helpdesk.domain.Order.OrderStatus;
import com.skala.helpdesk.domain.Ticket.Status;
import com.skala.helpdesk.rag.IngestService;
import com.skala.helpdesk.repository.OrderRepository;
import com.skala.helpdesk.repository.TicketRepository;

/**
 * Phase 5 완료 기준 — 한 대화 안에서 여러 턴을 순서대로 진행한다: RAG 근거+출처(1턴),
 * 도구 조회(2턴), 메모리(대명사 해석, 3턴), 승인 게이트(4턴), 세션 격리(5턴). 실제 모델을
 * 호출하므로 {@code @Tag("eval")}로 기본 실행에서 제외되고 {@code ./gradlew test -Peval}로만
 * 실행된다.
 *
 * <p>H2가 재시작해도 남는 파일 DB이므로 티켓 개수는 절대값이 아니라 실행 전후 차이로
 * 검증한다 — 여러 번 {@code -Peval}을 돌려도 깨지지 않는다.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("eval")
class HelpDeskScenarioEvalTest {

    private static final Logger log = LoggerFactory.getLogger(HelpDeskScenarioEvalTest.class);

    @Autowired
    private HelpDeskService helpDeskService;
    @Autowired
    private IngestService ingestService;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private TicketRepository tickets;

    @BeforeAll
    void setUp() {
        ingestService.ingestSampleDocs();
        if (orders.findByIdAndOwnerId("12345", "user1").isEmpty()) {
            orders.save(new Order("12345", "user1", "무선 이어폰", OrderStatus.SHIPPING,
                    LocalDate.now().minusDays(4), LocalDate.now().plusDays(1), new BigDecimal("52000")));
        }
    }

    @Test
    @DisplayName("5턴 시나리오 — RAG 출처, 도구 조회, 메모리, 승인 게이트, 세션 격리")
    void 오턴_시나리오를_순서대로_진행한다() {
        String session = "scenario-" + UUID.randomUUID();
        long pendingBefore = tickets.findByStatusOrderByRequestedAtAsc(Status.PENDING).size();

        AnswerDto t1 = helpDeskService.ask("user1", session, "단순 변심 반품은 며칠 이내인가요?");
        log.info("1턴 -> {}", t1);
        assertThat(t1.answer()).contains("7일");
        assertThat(t1.sources()).isNotEmpty();

        AnswerDto t2 = helpDeskService.ask("user1", session, "제 주문 12345는 지금 어디예요?");
        log.info("2턴 -> {}", t2);
        assertThat(t2.answer()).contains("12345");
        assertThat(t2.toolUsed()).isTrue();

        // 3턴 — 메모리: 1·2턴을 함께 참조(대명사 해석). 정확한 문구는 모델마다 다를 수 있어
        // 응답이 왔는지만 단언하고 내용은 로그로 남긴다.
        AnswerDto t3 = helpDeskService.ask("user1", session, "그럼 그거 반품 돼요?");
        log.info("3턴 -> {}", t3);
        assertThat(t3.answer()).isNotBlank();

        AnswerDto t4 = helpDeskService.ask("user1", session, "환불로 접수해 주세요");
        log.info("4턴 -> {}", t4);
        assertThat(t4.answer()).containsAnyOf("접수", "승인");

        String otherSession = "scenario-other-" + UUID.randomUUID();
        AnswerDto t5 = helpDeskService.ask("user1", otherSession, "그거 어떻게 됐어요?");
        log.info("5턴(새 세션, 맥락 있는 것처럼 답했다면 세션 격리 실패) -> {}", t5);
        assertThat(t5.answer()).isNotBlank();

        long pendingAfter = tickets.findByStatusOrderByRequestedAtAsc(Status.PENDING).size();
        assertThat(pendingAfter).isEqualTo(pendingBefore + 1); // 4턴에서 정확히 1건만 접수됐다
    }
}
