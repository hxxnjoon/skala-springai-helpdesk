package com.skala.helpdesk.tools;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.skala.helpdesk.advisor.AuditLog;
import com.skala.helpdesk.domain.Order;
import com.skala.helpdesk.repository.OrderRepository;

/**
 * 모델은 코드를 보지 않는다 — {@link Tool#description()}만 본다. 사용자 ID는
 * {@link ToolParam}이 아니라 {@link ToolContext}로 받는다 — 모델이 프롬프트로 흉내 낼 수
 * 있는 파라미터가 아니라, 컨트롤러가 인증에서 꺼내 심어 준 값만 신뢰한다.
 */
@Component
public class OrderTools {

    private final OrderRepository orders;
    private final AuditLog auditLog;
    private final MeterRegistry registry;

    public OrderTools(OrderRepository orders, AuditLog auditLog, MeterRegistry registry) {
        this.orders = orders;
        this.auditLog = auditLog;
        this.registry = registry;
    }

    @Tool(description = """
            주문 상태를 조회한다. 사용자가 주문번호를 말하거나
            '내 주문', '배송 언제' 처럼 물으면 이 도구를 쓴다.
            """)
    public String orderStatus(
            @ToolParam(description = "조회할 주문번호. 예: 12345") String orderId,
            ToolContext context) {
        ToolUsage.mark(context);
        String userId = currentUser(context);
        long started = System.nanoTime();
        String result = orders.findByIdAndOwnerId(orderId, userId) // 권한은 쿼리 안에 — 있음/없음/남의 것을 구분하지 않는다
                .map(this::describe)
                .orElse("해당 주문을 찾을 수 없습니다.");
        record(context, "orderStatus", orderId, started, true);
        return result;
    }

    private String describe(Order o) {
        return "%s: %s, 도착예정 %s".formatted(o.getId(), o.getStatus().label(), o.getEta());
    }

    static String currentUser(ToolContext context) {
        Object userId = context == null ? null : context.getContext().get("userId");
        if (userId == null) {
            throw new IllegalStateException("ToolContext에 userId가 없습니다 — 컨트롤러에서 넣어야 합니다.");
        }
        return userId.toString();
    }

    private void record(ToolContext context, String tool, String arg, long startedNanos, boolean ok) {
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000;
        String traceId = context.getContext().getOrDefault("traceId", "-").toString();
        auditLog.toolCall(traceId, tool, arg, elapsedMs, ok);
        registry.counter("ai.tool.calls", "tool", tool, "result", ok ? "ok" : "fail").increment();
    }
}
