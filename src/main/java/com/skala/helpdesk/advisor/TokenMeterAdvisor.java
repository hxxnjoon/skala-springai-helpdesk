package com.skala.helpdesk.advisor;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;

/**
 * Phase 8 — 관찰: 토큰·지연을 계측한다. 가장 안쪽(order 900)에 둬서 실제 모델 호출을 가장
 * 가까이에서 잰다. {@code /actuator/metrics/ai.tokens}, {@code /actuator/metrics/ai.latency}로
 * 확인한다.
 */
public class TokenMeterAdvisor implements CallAdvisor {

    private final MeterRegistry registry;

    public TokenMeterAdvisor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "TokenMeterAdvisor";
    }

    @Override
    public int getOrder() {
        return 900;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long started = System.nanoTime();
        ChatClientResponse response = chain.nextCall(request);
        registry.timer("ai.latency").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);

        if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
            Usage usage = response.chatResponse().getMetadata().getUsage();
            if (usage != null) {
                registry.counter("ai.tokens", "type", "prompt")
                        .increment(nullSafe(usage.getPromptTokens()));
                registry.counter("ai.tokens", "type", "completion")
                        .increment(nullSafe(usage.getCompletionTokens()));
            }
        }
        return response;
    }

    private double nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
