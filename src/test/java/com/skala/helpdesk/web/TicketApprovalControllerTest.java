package com.skala.helpdesk.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.skala.helpdesk.domain.Ticket;
import com.skala.helpdesk.domain.Ticket.Status;
import com.skala.helpdesk.repository.TicketRepository;

/**
 * 실제로 겪은 버그의 회귀 테스트 — {@code AdminController.approveTicket}이
 * {@code findById()}로 꺼낸 detached 엔티티에 {@code approve()}만 호출하고 저장하지
 * 않아서, 응답 JSON은 APPROVED로 보이지만 DB에는 반영되지 않았고 재승인도 막히지
 * 않았다(수동 curl 테스트로 발견). {@code @DataJpaTest}는 테스트 전체가 하나의
 * 트랜잭션으로 묶여 엔티티가 계속 managed 상태로 남기 때문에 이 버그를 잡지 못한다 —
 * 그래서 실제 요청 경계를 흉내 내는 {@code @SpringBootTest + MockMvc}로 검증한다.
 *
 * <p>개발 중 띄워 둔 {@code bootRun}과 같은 H2 파일을 두고 충돌하지 않도록 이 테스트만
 * 별도의 인메모리 DB를 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ticket-approval-test;DB_CLOSE_DELAY=-1")
class TicketApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TicketRepository tickets;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("승인은 실제로 DB에 반영되고, 재승인은 409로 막힌다")
    void 승인은_DB에_반영되고_재승인은_막힌다() throws Exception {
        Ticket saved = tickets.save(new Ticket("t-order", "user1", Ticket.Type.REFUND, "테스트"));

        mockMvc.perform(post("/api/admin/tickets/{id}/approve", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 컨트롤러 호출과 별개로 다시 조회해 실제로 DB에 반영됐는지 확인한다.
        Ticket reloaded = tickets.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Status.APPROVED);

        mockMvc.perform(post("/api/admin/tickets/{id}/approve", saved.getId()))
                .andExpect(status().isConflict());
    }
}
