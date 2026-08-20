package com.skala.helpdesk.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
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
 */
@Service
public class HelpDeskService {

    private static final String TENANT_ID = "skala";

    private final ChatClient chatClient;

    public HelpDeskService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public AnswerDto ask(String userId, String sessionId, String message) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        String conversationId = conversationId(userId, sessionId);
        AtomicBoolean toolUsed = new AtomicBoolean(false);
        ChatClientResponse response = chatClient.prompt()
                .user(message)
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param("userId", userId)
                        .param("traceId", traceId))
                .toolContext(Map.of("userId", userId, "traceId", traceId, ToolUsage.CONTEXT_KEY, toolUsed))
                .call()
                .chatClientResponse();
        return assemble(response, toolUsed.get());
    }

    public static String conversationId(String userId, String sessionId) {
        return "%s:%s:%s".formatted(TENANT_ID, userId, sessionId);
    }

    AnswerDto assemble(ChatClientResponse response, boolean toolUsed) {
        String answer = response.chatResponse() != null
                ? response.chatResponse().getResult().getOutput().getText()
                : "";
        List<Source> sources = sourcesOf(response);
        return new AnswerDto(answer, sources, toolUsed);
    }

    @SuppressWarnings("unchecked")
    private List<Source> sourcesOf(ChatClientResponse response) {
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
