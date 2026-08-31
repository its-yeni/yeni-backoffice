package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.PurchaseOrder;
import com.yeni.backoffice.core.commerce.enums.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findAllByOrderByIdDesc();
    List<PurchaseOrder> findByStatusInOrderByIdDesc(List<PurchaseOrderStatus> statuses);
    long countByPoNoStartingWith(String prefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);
}
