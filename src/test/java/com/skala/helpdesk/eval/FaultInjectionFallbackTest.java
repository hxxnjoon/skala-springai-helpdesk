package com.skala.helpdesk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.HelpDeskService;

/**
 * 비기능 요구사항 — 주 모델 장애 시 폴백으로 응답을 지속한다. 주 모델을 존재하지 않는
 * 모델 ID로 오버라이드해 결정적으로 실패를 재현한다({@code helpdesk.model.fallback}은
 * application.yml 기본값(gpt-4.1-mini)을 그대로 쓰므로 유효하다) — 서킷브레이커가 예외
 * 1건만으로도 fallbackMethod로 전환하므로 재시도를 여러 번 만들 필요가 없다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.ai.openai.chat.options.model=not-a-real-helpdesk-model-xyz")
@Tag("eval")
class FaultInjectionFallbackTest {

    @Autowired
    private HelpDeskService helpDeskService;

    @Test
    @DisplayName("주 모델이 존재하지 않는 모델 ID로 실패해도 폴백 모델이 응답한다")
    void 주_모델_장애_시_폴백_모델로_응답한다() {
        AnswerDto answer = helpDeskService.ask("user1", "fault-" + UUID.randomUUID(), "안녕하세요, 반품 규정 알려주세요");
        assertThat(answer.answer()).isNotBlank();
    }
}
