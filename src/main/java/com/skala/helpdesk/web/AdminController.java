package com.skala.helpdesk.web;

import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.advisor.AuditLog;
import com.skala.helpdesk.domain.Ticket;
import com.skala.helpdesk.rag.IngestService;
import com.skala.helpdesk.repository.TicketRepository;

/**
 * Phase 2 — 인제스트는 성공 메시지가 아니라 실제 결과로 확인해야 한다. 청크 조회 창구를
 * 열어 두면 여기서 안 잡힌 문제가 Phase 3에서 계속 이어지지 않는다.
 *
 * <p>Phase 4 — 승인 게이트의 사람 쪽. 이 컨트롤러의 메서드는 어디에도 {@code @Tool}로
 * 등록돼 있지 않다 — 모델이 아무리 설득당해도 여기엔 원천적으로 닿을 수 없다. Phase 7부터는
 * {@code @PreAuthorize}로 ADMIN 롤만 접근을 허용한다.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final VectorStore vectorStore;
    private final IngestService ingestService;
    private final TicketRepository tickets;
    private final AuditLog auditLog;

    public AdminController(VectorStore vectorStore, IngestService ingestService,
                            TicketRepository tickets, AuditLog auditLog) {
        this.vectorStore = vectorStore;
        this.ingestService = ingestService;
        this.tickets = tickets;
        this.auditLog = auditLog;
    }

    @GetMapping("/chunks")
    public List<Map<String, Object>> inspect(@RequestParam String q, @RequestParam(defaultValue = "5") int topK) {
        var hits = vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(topK).build());
        return hits.stream()
                .map(d -> Map.<String, Object>of(
                        "source", d.getMetadata().get("source"),
                        "version", d.getMetadata().get("version"),
                        "score", d.getScore(),
                        "preview", d.getText().substring(0, Math.min(160, d.getText().length()))))
                .toList();
    }

    @PostMapping("/ingest")
    public List<IngestService.IngestResult> reingest() {
        var results = ingestService.ingestSampleDocs();
        ingestService.persistSnapshot();
        return results;
    }

    @GetMapping("/tickets/pending")
    public List<TicketView> pendingTickets() {
        return tickets.findByStatusOrderByRequestedAtAsc(Ticket.Status.PENDING).stream()
                .map(TicketView::from)
                .toList();
    }

    @PostMapping("/tickets/{id}/approve")
    @Transactional // findById가 반환하는 detached 엔티티에 approve()만 호출하면 DB에 반영되지
                    // 않는다 — 트랜잭션 안에서 관리되는(managed) 엔티티여야 커밋 시점에
                    // dirty checking으로 자동 flush된다(실제로 재승인이 막히지 않는 버그로 발견).
    public TicketView approveTicket(@PathVariable Long id) {
        Ticket ticket = tickets.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + id));
        ticket.approve(); // 실행 버튼은 사람이 누른다 — 모델이 닿지 못하는 경로다.
        auditLog.toolCall("-", "adminApprove", String.valueOf(id), 0, true);
        return TicketView.from(ticket);
    }

    public record TicketView(Long id, String orderId, String type, String status, String reason) {
        static TicketView from(Ticket t) {
            return new TicketView(t.getId(), t.getOrderId(), t.getType().name(), t.getStatus().name(), t.getReason());
        }
    }
}
