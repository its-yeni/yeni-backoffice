package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.PurchaseOrderItem;
import com.yeni.backoffice.core.commerce.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    List<PurchaseOrderItem> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);
    void deleteByPurchaseOrderId(Long purchaseOrderId);

    /** ORDERED/PARTIALLY_RECEIVED 발주의 미입고 수량 = "입고 예정" 계산용. */
    @Query("select i from PurchaseOrderItem i join PurchaseOrder p on p.id = i.purchaseOrderId "
            + "where p.status in :statuses and i.receivedQuantity < i.orderedQuantity")
    List<PurchaseOrderItem> findOutstanding(@Param("statuses") List<PurchaseOrderStatus> statuses);
}
