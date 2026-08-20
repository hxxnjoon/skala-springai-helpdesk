package com.skala.helpdesk.advisor;

import java.util.List;
import java.util.regex.Pattern;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * Spring AI 내장 {@link org.springframework.ai.chat.client.advisor.SafeGuardAdvisor}는
 * 설정된 단어 목록의 리터럴 포함 여부만 본다({@code Prompt.getContents().contains(word)}) —
 * 프롬프트 인젝션 문구나 과도하게 긴 입력은 걸러내지 못한다. 그 틈을 코드로 막는다.
 *
 * <p>{@code chain.nextCall()}/{@code chain.nextStream()}을 아예 호출하지 않고 거절 응답을
 * 즉시 돌려준다 — 모델을 부르지 않으니 비용도 안 든다. {@code MessageChatMemoryAdvisor}
 * (기억, order 200)보다 앞(order 100 이전)에 둬야 차단된 문장이 메모리에 저장되지 않는다.
 *
 * <p>간접 인젝션(검색된 문서 안에 심어진 지시)은 사용자 메시지가 아니라 문서 내용이라
 * 여기서 못 잡는다 — 그건 시스템 프롬프트로 막는다(AiConfig 참고).
 */
public class InjectionGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int MAX_LENGTH = 2000;

    // 정교한 NLP 탐지가 아니라, 뚫렸던 경로를 코드로 막는 최소한의 휴리스틱이다.
    private static final Pattern INJECTION = Pattern.compile(
            "이전\\s*지시|시스템\\s*프롬프트|프롬프트를?\\s*(출력|공개|보여)|지시.*무시|무시하고|"
                    + "지금부터\\s*너는|당신은\\s*이제|DAN\\s*모드|제한\\s*없이|롤플레이");

    @Override
    public String getName() {
        return "InjectionGuardAdvisor";
    }

    @Override
    public int getOrder() {
        return 90;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String reason = blockReason(request);
        return reason != null ? refuse(reason) : chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String reason = blockReason(request);
        return reason != null ? Flux.just(refuse(reason)) : chain.nextStream(request);
    }

    private String blockReason(ChatClientRequest request) {
        String text = latestUserText(request);
        if (text.length() > MAX_LENGTH) {
            return "입력이 너무 깁니다(%d자 제한).".formatted(MAX_LENGTH);
        }
        if (INJECTION.matcher(text).find()) {
            return "이전 지시를 무시하거나 시스템 프롬프트를 요구하는 요청은 처리할 수 없습니다.";
        }
        return null;
    }

    private String latestUserText(ChatClientRequest request) {
        return request.prompt().getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .reduce((first, second) -> second)
                .map(Message::getText)
                .orElse("");
    }

    private ChatClientResponse refuse(String reason) {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(reason))));
        return ChatClientResponse.builder().chatResponse(chatResponse).build();
    }
}
