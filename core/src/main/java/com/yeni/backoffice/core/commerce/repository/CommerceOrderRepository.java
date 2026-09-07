package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommerceOrderRepository extends JpaRepository<CommerceOrder, Long> {

    Optional<CommerceOrder> findByOrderNo(String orderNo);

    List<CommerceOrder> findByOrderNoIn(java.util.Collection<String> orderNos);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CommerceOrder o where o.orderNo = :orderNo")
    Optional<CommerceOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    List<CommerceOrder> findAllByOrderByIdDesc();

    List<CommerceOrder> findByStoreIdOrderByIdDesc(Long storeId);
    Page<CommerceOrder> findByStoreIdOrderByIdDesc(Long storeId, Pageable pageable);

    List<CommerceOrder> findByStoreIdInOrderByIdDesc(java.util.Collection<Long> storeIds);

    List<CommerceOrder> findByOrderStatus(OrderStatus orderStatus);
}
