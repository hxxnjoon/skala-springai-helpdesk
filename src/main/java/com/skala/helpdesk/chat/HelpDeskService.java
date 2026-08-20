package com.skala.helpdesk.chat;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.skala.helpdesk.chat.AnswerDto.Source;

/**
 * Phase 3 — 근거 문서는 모델이 스스로 보고하게 두지 않고, Advisor 컨텍스트에서 직접 꺼낸다.
 * "근거 없음 -> 모른다"는 시스템 프롬프트의 grounding 지시로 강제한다({@code AiConfig} 참고) —
 * QuestionAnswerAdvisor가 항상 체인에 있으므로 별도로 모델 호출을 건너뛰는 분기는 두지 않는다.
 */
@Service
public class HelpDeskService {

    private final ChatClient chatClient;

    public HelpDeskService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public AnswerDto ask(String conversationId, String message) {
        ChatClientResponse response = chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatClientResponse();
        return assemble(response);
    }

    AnswerDto assemble(ChatClientResponse response) {
        String answer = response.chatResponse() != null
                ? response.chatResponse().getResult().getOutput().getText()
                : "";
        List<Source> sources = sourcesOf(response);
        return new AnswerDto(answer, sources, false);
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
