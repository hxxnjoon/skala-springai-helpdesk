package com.skala.helpdesk.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import reactor.core.publisher.Flux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.skala.helpdesk.chat.AnswerDto.Source;
import com.skala.helpdesk.tools.ToolUsage;

/**
 * Phase 3 — 근거 문서는 모델이 스스로 보고하게 두지 않고, Advisor 컨텍스트에서 직접 꺼낸다.
 * "근거 없음 -> 모른다"는 시스템 프롬프트의 grounding 지시로 강제한다({@code AiConfig} 참고) —
 * QuestionAnswerAdvisor가 항상 체인에 있으므로 별도로 모델 호출을 건너뛰는 분기는 두지 않는다.
 *
 * <p>Phase 4 — {@code userId}는 Advisor 파라미터(ToolContext 밖)와 ToolContext(도구용) 양쪽에
 * 동시에 심는다 — 두 채널이 서로 다르기 때문이다. {@code toolUsed}는 모델 자기 보고가 아니라
 * {@link ToolUsage}로 서버 측에서 직접 기록한다.
 *
 * <p>Phase 5 — 대화 ID는 {@code tenantId:userId:sessionId} 규칙으로 만든다. 이 프로젝트는
 * 단일 테넌트라 tenantId는 상수로 고정한다. 같은 규칙을 한 곳에서만 만들어야 세션이 섞이지
 * 않는다.
 *
 * <p>Phase 8 — {@code ask()}는 서킷브레이커로 감싸 주 모델 호출이 실패하면 폴백 모델로
 * 넘어간다. {@code stream()}은 이 폴백 범위 밖이다 — Reactor용 서킷브레이커 배선이 별도로
 * 필요해 이 프로젝트 범위에서는 동기 경로만 지원한다(README 참고).
 */
@Service
public class HelpDeskService {

    private static final Logger log = LoggerFactory.getLogger(HelpDeskService.class);
    private static final String TENANT_ID = "skala";

    private final ChatClient primaryChatClient;
    private final ChatClient fallbackChatClient;

    public HelpDeskService(@Qualifier("primaryChatClient") ChatClient primaryChatClient,
                            @Qualifier("fallbackChatClient") ChatClient fallbackChatClient) {
        this.primaryChatClient = primaryChatClient;
        this.fallbackChatClient = fallbackChatClient;
    }

    @CircuitBreaker(name = "chatModel", fallbackMethod = "askFallback")
    public AnswerDto ask(String userId, String sessionId, String message) {
        return callWith(primaryChatClient, userId, sessionId, message);
    }

    /** 주 모델 호출이 실패하면(예외 1건으로 즉시 트리거) 폴백 모델로 재시도한다. */
    private AnswerDto askFallback(String userId, String sessionId, String message, Throwable ex) {
        log.warn("주 모델 호출 실패 — 폴백 모델로 전환합니다: {}", ex.toString());
        return callWith(fallbackChatClient, userId, sessionId, message);
    }

    public Flux<ChatClientResponse> stream(String userId, String sessionId, String message) {
        AtomicBoolean toolUsed = new AtomicBoolean(false); // 스트림 소비 중 도구가 호출되면 true로 뒤집힌다
        return request(primaryChatClient, userId, sessionId, message, toolUsed).stream().chatClientResponse();
    }

    private AnswerDto callWith(ChatClient chatClient, String userId, String sessionId, String message) {
        AtomicBoolean toolUsed = new AtomicBoolean(false);
        ChatClientResponse response = request(chatClient, userId, sessionId, message, toolUsed)
                .call()
                .chatClientResponse();
        return assemble(response, toolUsed.get());
    }

    private ChatClient.ChatClientRequestSpec request(ChatClient chatClient, String userId, String sessionId,
                                                       String message, AtomicBoolean toolUsed) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        String conversationId = conversationId(userId, sessionId);
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param("userId", userId)
                        .param("traceId", traceId))
                .toolContext(Map.of("userId", userId, "traceId", traceId, ToolUsage.CONTEXT_KEY, toolUsed));
    }

    public static String conversationId(String userId, String sessionId) {
        return "%s:%s:%s".formatted(TENANT_ID, userId, sessionId);
    }

    AnswerDto assemble(ChatClientResponse response, boolean toolUsed) {
        String answer = response.chatResponse() != null
                ? response.chatResponse().getResult().getOutput().getText()
                : "";
        List<Source> sources = extractSources(response);
        return new AnswerDto(answer, sources, toolUsed);
    }

    @SuppressWarnings("unchecked")
    public List<Source> extractSources(ChatClientResponse response) {
        Object raw = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(raw instanceof List<?> docs)) {
            return List.of();
        }
        return ((List<Document>) docs).stream()
                .map(d -> new Source(
                        String.valueOf(d.getMetadata().get("source")),
                        String.valueOf(d.getMetadata().get("version"))))
                .distinct()
                .toList();
    }
}
