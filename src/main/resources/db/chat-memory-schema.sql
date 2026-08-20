-- Spring AI 기본 스키마(schema-h2.sql)를 그대로 가져오되 conversation_id만 넓힌다.
-- 기본값 VARCHAR(36)은 순수 UUID 하나 기준이라, 우리가 쓰는
-- "tenantId:userId:sessionId"(예: "skala:admin1:<UUID>", 40자 이상) 형식이 들어가면
-- INSERT가 DataIntegrityViolationException으로 매번 실패한다(실제 운영 중 발견).
CREATE TABLE SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(255) NOT NULL,
    content LONGVARCHAR NOT NULL,
    type VARCHAR(10) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX ON SPRING_AI_CHAT_MEMORY(conversation_id, timestamp DESC);

ALTER TABLE SPRING_AI_CHAT_MEMORY ADD CONSTRAINT TYPE_CHECK CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'));
