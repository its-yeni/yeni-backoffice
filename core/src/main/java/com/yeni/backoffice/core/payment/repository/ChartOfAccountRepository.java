package com.yeni.backoffice.core.payment.repository;

import com.yeni.backoffice.core.payment.entity.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {

    Optional<ChartOfAccount> findByCode(String code);

    List<ChartOfAccount> findAllByOrderBySortOrderAsc();
}
