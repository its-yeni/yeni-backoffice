package com.yeni.backoffice.core.commerce.repository;
import com.yeni.backoffice.core.commerce.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProductCategoryRepository extends JpaRepository<ProductCategory,Long>{
    List<ProductCategory> findAllByOrderBySortOrderAscIdAsc();
    List<ProductCategory> findByStoreCodeOrderBySortOrderAscIdAsc(String storeCode);
    Optional<ProductCategory> findByCategoryName(String categoryName);
    Optional<ProductCategory> findByStoreCodeAndCategoryName(String storeCode,String categoryName);
}
