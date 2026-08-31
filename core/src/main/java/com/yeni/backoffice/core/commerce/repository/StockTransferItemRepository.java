package com.yeni.backoffice.core.commerce.repository;
import com.yeni.backoffice.core.commerce.entity.StockTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface StockTransferItemRepository extends JpaRepository<StockTransferItem,Long>{List<StockTransferItem> findByTransferIdOrderById(Long transferId);}
