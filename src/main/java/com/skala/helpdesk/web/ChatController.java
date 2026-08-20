package com.skala.helpdesk.web;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.chat.AnswerDto;
import com.skala.helpdesk.chat.AnswerDto.Source;
import com.skala.helpdesk.chat.ChatRequest;
import com.skala.helpdesk.chat.HelpDeskService;

/**
 * Phase 6 — 화면이 쓰기 좋게 답변·출처·도구사용 여부를 나눠 반환한다. 스트리밍 응답에도
 * 출처를 마지막 이벤트로 함께 내보낸다. 사용자 식별은 {@link Principal}에서 꺼낸다(Phase 7).
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final HelpDeskService helpDeskService;
    private final ObjectMapper objectMapper;

    public ChatController(HelpDeskService helpDeskService, ObjectMapper objectMapper) {
        this.helpDeskService = helpDeskService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public AnswerDto chat(@Valid @RequestBody ChatRequest request, Principal user) {
        return helpDeskService.ask(user.getName(), request.sessionId(), request.message());
    }

    /** 웹 UI 로그인 검증 + 관리자 패널 노출 여부 판단용. */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();
        return new MeResponse(authentication.getName(), roles);
    }

    public record MeResponse(String username, List<String> roles) {
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request, Principal user) {
        AtomicReference<List<Source>> lastSources = new AtomicReference<>(List.of());

        Flux<ServerSentEvent<String>> tokens = helpDeskService
                .stream(user.getName(), request.sessionId(), request.message())
                .doOnNext(response -> lastSources.set(helpDeskService.extractSources(response)))
                .map(this::text)
                .filter(text -> !text.isEmpty())
                .map(text -> ServerSentEvent.builder(text).event("token").build());

        Mono<ServerSentEvent<String>> sourcesEvent = Mono
                .fromCallable(() -> sourcesJson(lastSources.get()))
                .map(json -> ServerSentEvent.builder(json).event("sources").build());

        return tokens.concatWith(sourcesEvent).timeout(Duration.ofSeconds(60));
    }

    private String text(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return "";
        }
        Generation result = chatResponse.getResult();
        if (result == null || result.getOutput() == null || result.getOutput().getText() == null) {
            return "";
        }
        return result.getOutput().getText();
    }

    private String sourcesJson(List<Source> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        }
        catch (Exception e) {
            return "[]";
        }
    }
}
