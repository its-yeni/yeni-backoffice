package com.yeni.backoffice.core.commerce.repository;
import com.yeni.backoffice.core.commerce.entity.StoreProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface StoreProductRepository extends JpaRepository<StoreProduct,Long>{List<StoreProduct> findByStoreIdOrderByIdAsc(Long storeId);Optional<StoreProduct> findByStoreIdAndProductId(Long storeId,Long productId);}
