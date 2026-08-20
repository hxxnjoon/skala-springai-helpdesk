package com.skala.helpdesk.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 주문 엔티티 — API로 나가지 않는다. {@code ownerId}·{@code cost}가 그대로 노출되면 그것이
 * 곧 사고다. 밖으로 나갈 때는 반드시 도구 응답 문자열/DTO로 바꾼다.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private String id;

    @Column(nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private String item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private LocalDate orderedAt;
    private LocalDate eta;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    protected Order() {
        // JPA 전용
    }

    public Order(String id, String ownerId, String item, OrderStatus status,
                 LocalDate orderedAt, LocalDate eta, BigDecimal cost) {
        this.id = id;
        this.ownerId = ownerId;
        this.item = item;
        this.status = status;
        this.orderedAt = orderedAt;
        this.eta = eta;
        this.cost = cost;
    }

    public enum OrderStatus {
        PAID("결제완료"), PREPARING("상품준비중"), SHIPPING("배송중"), DELIVERED("배송완료");

        private final String label;

        OrderStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public boolean isOwnedBy(String userId) {
        return ownerId.equals(userId);
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getItem() {
        return item;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDate getOrderedAt() {
        return orderedAt;
    }

    public LocalDate getEta() {
        return eta;
    }

    public BigDecimal getCost() {
        return cost;
    }
}
