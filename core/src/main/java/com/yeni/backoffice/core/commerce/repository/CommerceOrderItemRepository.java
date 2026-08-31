package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.CommerceOrderItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommerceOrderItemRepository extends JpaRepository<CommerceOrderItem, Long> {

    List<CommerceOrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    long countByOrderId(Long orderId);

    List<CommerceOrderItem> findByOrderIdInAndShippedYnFalseAndProductVariantIdIsNotNullOrderByIdAsc(Collection<Long> orderIds);

    /** 운영 대시보드의 "오늘 출고 처리" 진행률 계산용 — 오늘 실제로 출고 완료 처리된 항목 수를 센다. */
    long countByShippedYnTrueAndShippedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);

    long countByOrderIdInAndShippedYnTrueAndShippedAtBetween(
            Collection<Long> orderIds,
            java.time.LocalDateTime start,
            java.time.LocalDateTime end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CommerceOrderItem i where i.id = :id")
    Optional<CommerceOrderItem> findByIdForUpdate(@Param("id") Long id);
}
