package com.yeni.backoffice.core.payment.repository;

import com.yeni.backoffice.core.payment.entity.PgApiLog;
import com.yeni.backoffice.core.payment.enums.LogResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface PgApiLogRepository extends JpaRepository<PgApiLog, Long> {

    List<PgApiLog> findByPaymentIdOrderByLoggedAtDesc(Long paymentId);

    List<PgApiLog> findByOrderNoOrderByLoggedAtDesc(String orderNo);

    List<PgApiLog> findByResultStatusAndLoggedAtAfterOrderByLoggedAtDesc(
            LogResultStatus resultStatus, LocalDateTime after, Pageable pageable);

    long countByResultStatusAndLoggedAtAfter(LogResultStatus resultStatus, LocalDateTime after);
}
