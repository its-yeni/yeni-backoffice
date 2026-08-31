package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByStoreCodeAndProductCode(String storeCode,String productCode);

    List<Product> findByCategory(String category);
    List<Product> findByStoreCode(String storeCode);
    List<Product> findByStoreCodeAndCategory(String storeCode,String category);

    @Query("""
            select p from Product p
             where (:keyword is null
                    or lower(p.productCode) like lower(concat('%', :keyword, '%'))
                    or lower(p.productName) like lower(concat('%', :keyword, '%')))
               and (:saleStatus is null or p.saleStatus = :saleStatus)
               and (:storeCode is null or p.storeCode = :storeCode)
            """)
    Page<Product> search(@Param("keyword") String keyword, @Param("saleStatus") ProductSaleStatus saleStatus,@Param("storeCode") String storeCode, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
