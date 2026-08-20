package com.skala.helpdesk.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 감사 로그 — 한 요청을 처음부터 끝까지 traceId로 따라갈 수 있게 한 곳에 모은다.
 *
 * <p>{@link AuditAdvisor}(요청·응답)와 tools 패키지(도구 호출)가 같은 로거를 나눠 쓴다 —
 * Advisor 컨텍스트와 ToolContext는 서로 다른 통로라 traceId를 양쪽에 각각 심어 둬야 여기서
 * 다시 만난다.
 */
@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    public void request(String traceId, String userId, String question) {
        log.info("[{}] {}  질문=\"{}\"", traceId, userId, question);
    }

    public void toolCall(String traceId, String toolName, String arg, long elapsedMs, boolean ok) {
        log.info("[{}]    도구 {}({}) {}ms {}", traceId, toolName, arg, elapsedMs, ok ? "" : "FAIL");
    }

    public void response(String traceId, long elapsedMs, Integer promptTokens, Integer completionTokens) {
        log.info("[{}] 응답 {}ms · 프롬프트 {} · 완성 {} 토큰", traceId, elapsedMs, promptTokens, completionTokens);
    }
}
