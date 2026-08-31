package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.StockCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {
    List<StockCount> findAllByOrderByIdDesc();
    long countByCountNoStartingWith(String prefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StockCount c where c.id = :id")
    Optional<StockCount> findByIdForUpdate(@Param("id") Long id);
}
