package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.StockCountLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockCountLineRepository extends JpaRepository<StockCountLine, Long> {
    List<StockCountLine> findByStockCountIdOrderByIdAsc(Long stockCountId);
    Optional<StockCountLine> findByIdAndStockCountId(Long id, Long stockCountId);
}
