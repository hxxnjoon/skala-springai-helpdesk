package com.skala.helpdesk.tools;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.ai.chat.model.ToolContext;

/**
 * 도구 호출 여부를 모델의 자기 보고에 맡기지 않고 서버 측에서 직접 기록한다. 컨트롤러가
 * {@code ToolContext}에 심어 둔 {@link AtomicBoolean}을 도구가 호출 시점에 true로 뒤집고,
 * 서비스가 응답 조립 시 다시 읽는다.
 */
public final class ToolUsage {

    public static final String CONTEXT_KEY = "toolUsedTracker";

    private ToolUsage() {
    }

    public static void mark(ToolContext context) {
        Object tracker = context == null ? null : context.getContext().get(CONTEXT_KEY);
        if (tracker instanceof AtomicBoolean flag) {
            flag.set(true);
        }
    }
}
