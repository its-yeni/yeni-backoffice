package com.yeni.backoffice.core.commerce.repository;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ProductVariantRepository extends JpaRepository<ProductVariant,Long>{
 List<ProductVariant> findByProductIdOrderBySortOrderAscIdAsc(Long productId);
 List<ProductVariant> findByProductIdInOrderByProductIdAscSortOrderAscIdAsc(Collection<Long> productIds);
 Optional<ProductVariant> findByProductIdAndCombinationKey(Long productId,String key);
 boolean existsBySku(String sku);
 Optional<ProductVariant> findByBarcode(String barcode);
 boolean existsByBarcodeAndIdNot(String barcode,Long id);
 List<ProductVariant> findAllByOrderByProductIdAscSortOrderAscIdAsc();
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from ProductVariant v where v.id in :ids order by v.id") List<ProductVariant> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from ProductVariant v where v.id=:id") Optional<ProductVariant> findByIdForUpdate(@Param("id") Long id);
}
