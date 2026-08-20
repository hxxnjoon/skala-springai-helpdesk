package com.skala.helpdesk.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 1 — 공급자·모델·임계값을 코드가 아니라 설정으로 뺀다. 여기 없는 값은 전부
 * application.yml의 {@code spring.ai.*}(모델 이름, temperature 등) 표준 프로퍼티를 그대로 쓴다.
 */
@ConfigurationProperties(prefix = "helpdesk")
public record HelpDeskProperties(Rag rag, Memory memory, Tokens tokens, Model model, Safety safety) {

    public record Rag(int topK, double threshold) {
    }

    public record Memory(int maxMessages) {
    }

    /** budgetPerQuery는 강제하지 않고 {@code ai.tokens} 지표로 리뷰하는 용도다. */
    public record Tokens(int budgetPerQuery) {
    }

    public record Model(String primary, String fallback) {
    }

    public record Safety(List<String> sensitiveWords) {
    }
}
