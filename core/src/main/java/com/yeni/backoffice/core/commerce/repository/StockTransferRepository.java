package com.yeni.backoffice.core.commerce.repository;
import com.yeni.backoffice.core.commerce.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface StockTransferRepository extends JpaRepository<StockTransfer,Long>{List<StockTransfer> findAllByOrderByIdDesc();}
