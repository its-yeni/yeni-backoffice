package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.InventoryTransaction;
import com.yeni.backoffice.core.commerce.enums.InventoryTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    List<InventoryTransaction> findTop200ByOrderByIdDesc();
    List<InventoryTransaction> findByVariantIdOrderByIdDesc(Long variantId);
    List<InventoryTransaction> findTop200ByTypeOrderByIdDesc(InventoryTransactionType type);
    List<InventoryTransaction> findByTypeAndCreatedAtGreaterThanEqual(InventoryTransactionType type, java.time.LocalDateTime createdAt);
}
