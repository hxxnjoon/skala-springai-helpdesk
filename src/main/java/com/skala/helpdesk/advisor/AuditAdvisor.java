package com.skala.helpdesk.advisor;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.Ordered;

/**
 * 가장 바깥(order 최우선)에 둬서, 이후 어떤 Advisor가 요청을 바꾸거나 막아도(예:
 * SafeGuardAdvisor의 거절) "요청이 들어왔다"는 사실만은 항상 남긴다.
 *
 * <p>{@link BaseAdvisor} 하나만 구현하면 call·stream 양쪽을 다 커버한다 — CallAdvisor만
 * 구현하면 스트리밍 응답에서 감사가 누락된다.
 */
public class AuditAdvisor implements BaseAdvisor {

    private static final String START_KEY = "_auditStartNanos";

    private final AuditLog auditLog;

    public AuditAdvisor(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String traceId = contextValue(request.context(), "traceId");
        String userId = contextValue(request.context(), "userId");
        String question = request.prompt().getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .reduce((first, second) -> second)
                .map(m -> m.getText())
                .orElse("");
        auditLog.request(traceId, userId, question);
        request.context().put(START_KEY, System.nanoTime());
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        String traceId = contextValue(response.context(), "traceId");
        long started = (long) response.context().getOrDefault(START_KEY, System.nanoTime());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        Integer promptTokens = null;
        Integer completionTokens = null;
        if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
            Usage usage = response.chatResponse().getMetadata().getUsage();
            if (usage != null) {
                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
            }
        }
        auditLog.response(traceId, elapsedMs, promptTokens, completionTokens);
        return response;
    }

    private String contextValue(Map<String, Object> context, String key) {
        Object value = context.get(key);
        return value == null ? "-" : value.toString();
    }
}
