package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.CommerceDelivery;
import com.yeni.backoffice.core.commerce.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommerceDeliveryRepository extends JpaRepository<CommerceDelivery, Long> {
    /** 주문에 배송이 정확히 1건뿐이던 시절의 호환용 조회 — 부분배송으로 여러 건이 생기면 그중 하나(첫 건)만 돌려준다.
     * 여러 배송을 전부 봐야 하면 {@link #findAllByOrderIdOrderByIdAsc}를 쓴다. */
    Optional<CommerceDelivery> findByOrderId(Long orderId);
    List<CommerceDelivery> findAllByOrderIdOrderByIdAsc(Long orderId);
    List<CommerceDelivery> findByOrderIdIn(java.util.Collection<Long> orderIds);
    List<CommerceDelivery> findByStatusOrderByIdDesc(DeliveryStatus status);
    List<CommerceDelivery> findAllByOrderByIdDesc();
}
