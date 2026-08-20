package com.skala.helpdesk.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skala.helpdesk.domain.Order;

/**
 * 소유자 조건을 쿼리 자체에 넣는다. {@code findById()}로 꺼낸 뒤 자바 코드에서 소유자를
 * 비교하는 방식은 위험하다 — 호출하는 곳 중 한 군데에서만 그 비교를 빠뜨려도 남의 데이터가
 * 그대로 나간다. 조건이 쿼리에 있으면 빠뜨릴 여지 자체가 없다.
 */
public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByIdAndOwnerId(String id, String ownerId);
}
