package com.skala.helpdesk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.skala.helpdesk.chat.HelpDeskService;

/**
 * 실제로 겪은 버그의 회귀 테스트 — Spring AI 기본 JDBC 챗메모리 스키마는
 * {@code conversation_id VARCHAR(36)}(UUID 하나 기준)인데, 이 프로젝트의
 * {@code tenantId:userId:sessionId} 형식(예: "skala:admin1:&lt;UUID&gt;")은 40자를
 * 넘어 매 INSERT가 {@code DataIntegrityViolationException}으로 실패했다(웹 UI에서
 * 채팅을 보낼 때마다 503이 나는 것으로 발견). {@code db/chat-memory-schema.sql}로
 * 컬럼을 넓힌 뒤 이 테스트로 재발을 막는다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:chat-memory-schema-test;DB_CLOSE_DELAY=-1")
class ChatMemorySchemaTest {

    @Autowired
    private ChatMemory chatMemory;

    @Test
    @DisplayName("tenantId:userId:sessionId 형식의 긴 conversationId도 저장된다")
    void 긴_conversationId도_저장된다() {
        String conversationId = HelpDeskService.conversationId("admin1", UUID.randomUUID().toString());
        assertThat(conversationId.length()).isGreaterThan(36); // 기본 스키마 한계(36자)를 실제로 넘는지 확인

        assertThatCode(() -> chatMemory.add(conversationId, List.of(new UserMessage("반품 규정 알려줘"))))
                .doesNotThrowAnyException();
        assertThat(chatMemory.get(conversationId)).hasSize(1);
    }
}
