package com.skala.helpdesk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.skala.helpdesk.domain.Order;
import com.skala.helpdesk.domain.Order.OrderStatus;
import com.skala.helpdesk.repository.OrderRepository;

/**
 * 데모용 초기 주문 데이터. H2 파일 DB는 재시작해도 남으므로 이미 데이터가 있으면 건너뛴다.
 * 실무에서는 마이그레이션 도구(Flyway·Liquibase)를 쓴다.
 */
@Component
public class SeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    private final OrderRepository orderRepository;

    public SeedDataRunner(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (orderRepository.count() > 0) {
            return;
        }
        orderRepository.saveAll(List.of(
                new Order("12345", "user1", "무선 이어폰", OrderStatus.SHIPPING,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), new BigDecimal("52000")),
                new Order("12346", "user1", "USB-C 케이블", OrderStatus.DELIVERED,
                        LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 31), new BigDecimal("4000")),
                new Order("12347", "user1", "기계식 키보드", OrderStatus.PREPARING,
                        LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 16), new BigDecimal("98000")),
                new Order("99999", "admin1", "노트북 스탠드", OrderStatus.PAID,
                        LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 17), new BigDecimal("21000"))));

        log.info("초기 주문 데이터 {}건 적재", orderRepository.count());
    }
}
