package com.skala.helpdesk.tools;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.skala.helpdesk.advisor.AuditLog;
import com.skala.helpdesk.domain.Order;
import com.skala.helpdesk.domain.Ticket;
import com.skala.helpdesk.repository.OrderRepository;
import com.skala.helpdesk.repository.TicketRepository;

/**
 * 이 도구는 PENDING 티켓만 만든다 — 승인({@link Ticket#approve()})은 관리자 컨트롤러에서만
 * 호출되고, 그 메서드는 어디에도 {@code @Tool}로 노출돼 있지 않다. 모델이 아무리 설득당해도
 * 승인 경로에는 원천적으로 닿을 수 없다.
 */
@Component
public class TicketTools {

    private final OrderRepository orders;
    private final TicketRepository tickets;
    private final AuditLog auditLog;
    private final MeterRegistry registry;

    public TicketTools(OrderRepository orders, TicketRepository tickets, AuditLog auditLog, MeterRegistry registry) {
        this.orders = orders;
        this.tickets = tickets;
        this.auditLog = auditLog;
        this.registry = registry;
    }

    @Tool(description = """
            교환·환불 티켓을 접수한다. 이 도구는 요청을 접수만 하며, 실제 처리는 되지 않는다 —
            담당자 승인 후 처리된다. 사용자가 '환불해줘', '교환하고 싶어요', '취소하고 싶어요'
            라고 하면 쓴다.
            """)
    public String createTicket(
            @ToolParam(description = "교환·환불할 주문번호") String orderId,
            @ToolParam(description = "EXCHANGE(교환) 또는 REFUND(환불)") String type,
            @ToolParam(description = "사유") String reason,
            ToolContext context) {
        ToolUsage.mark(context);
        String userId = OrderTools.currentUser(context);
        long started = System.nanoTime();

        Order order = orders.findByIdAndOwnerId(orderId, userId).orElse(null);
        if (order == null) {
            record(context, "createTicket", orderId, started, false);
            return "해당 주문을 찾을 수 없습니다.";
        }

        Ticket.Type ticketType = parseType(type);
        Ticket ticket = tickets.save(new Ticket(orderId, userId, ticketType, reason)); // 접수만 — 처리는 사람이 한다
        record(context, "createTicket", orderId, started, true);
        return "%s 요청을 %d번으로 접수했습니다. 담당자 승인 후 처리됩니다."
                .formatted(ticketType == Ticket.Type.REFUND ? "환불" : "교환", ticket.getId());
    }

    private Ticket.Type parseType(String type) {
        try {
            return Ticket.Type.valueOf(type.trim().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return Ticket.Type.REFUND; // 모델이 애매한 값을 보내도 접수 자체는 막지 않는다
        }
    }

    private void record(ToolContext context, String tool, String arg, long startedNanos, boolean ok) {
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000;
        String traceId = context.getContext().getOrDefault("traceId", "-").toString();
        auditLog.toolCall(traceId, tool, arg, elapsedMs, ok);
        registry.counter("ai.tool.calls", "tool", tool, "result", ok ? "ok" : "fail").increment();
    }
}
