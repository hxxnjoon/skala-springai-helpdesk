package com.skala.helpdesk.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.skala.helpdesk.domain.Ticket;
import com.skala.helpdesk.domain.Ticket.Status;
import com.skala.helpdesk.domain.Ticket.Type;

/**
 * 완료 기준 — 승인 게이트. 도구는 PENDING만 만들 수 있고, APPROVED로의 전이는
 * {@link Ticket#approve()}를 통해서만 — 이미 처리된 티켓을 다시 승인하면 예외로 막힌다
 * (멱등하게 조용히 넘어가지 않는다 — 이중 승인은 실수의 신호다).
 */
@DataJpaTest
class TicketApprovalTest {

    @Autowired
    private TicketRepository tickets;

    @Test
    @DisplayName("접수 직후 상태는 PENDING이다")
    void 접수하면_PENDING이다() {
        Ticket saved = tickets.save(new Ticket("12345", "user1", Type.REFUND, "단순 변심"));
        assertThat(saved.getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    @DisplayName("승인하면 APPROVED로 전이한다")
    void 승인하면_APPROVED다() {
        Ticket saved = tickets.save(new Ticket("12345", "user1", Type.REFUND, "단순 변심"));
        saved.approve();
        assertThat(saved.getStatus()).isEqualTo(Status.APPROVED);
    }

    @Test
    @DisplayName("이미 승인된 티켓을 다시 승인하면 예외가 난다")
    void 재승인은_예외다() {
        Ticket saved = tickets.save(new Ticket("12345", "user1", Type.REFUND, "단순 변심"));
        saved.approve();
        assertThatThrownBy(saved::approve).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("대기 목록에는 PENDING만 나온다")
    void 대기_목록은_PENDING만() {
        Ticket pending = tickets.save(new Ticket("12345", "user1", Type.EXCHANGE, "단순 변심"));
        Ticket approved = tickets.save(new Ticket("99999", "user2", Type.REFUND, "오배송"));
        approved.approve();
        tickets.saveAndFlush(approved);

        assertThat(tickets.findByStatusOrderByRequestedAtAsc(Status.PENDING))
                .extracting(Ticket::getId)
                .containsExactly(pending.getId());
    }
}
