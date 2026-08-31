package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {
    @org.springframework.data.jpa.repository.Query("select l from InventoryLot l order by l.expirationDate asc nulls last, l.id asc")
    List<InventoryLot> findAllByOrderByExpirationDateAscIdAsc();
    Optional<InventoryLot> findByStoreIdAndVariantIdAndLotNo(Long storeId, Long variantId, String lotNo);
    long countByLotNoStartingWith(String prefix);
    @org.springframework.data.jpa.repository.Query("select l.storeId, l.variantId, sum(l.availableQuantity) from InventoryLot l group by l.storeId, l.variantId")
    java.util.List<Object[]> sumAvailableByStoreVariant();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from InventoryLot l where l.storeId=:storeId and l.variantId=:variantId and l.availableQuantity>0 order by l.expirationDate asc nulls last, l.id asc")
    List<InventoryLot> findAvailableForFefo(@org.springframework.data.repository.query.Param("storeId") Long storeId,
            @org.springframework.data.repository.query.Param("variantId") Long variantId);
}
