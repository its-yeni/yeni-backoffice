package com.yeni.backoffice.core.payment.repository;

import com.yeni.backoffice.core.payment.entity.PaymentRecoveryTask;
import com.yeni.backoffice.core.payment.enums.RecoveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRecoveryTaskRepository extends JpaRepository<PaymentRecoveryTask, Long> {

    Optional<PaymentRecoveryTask> findByTaskKey(String taskKey);

    List<PaymentRecoveryTask> findByPaymentIdOrderByIdAsc(Long paymentId);

    List<PaymentRecoveryTask> findByCancelIdOrderByIdAsc(Long cancelId);

    List<PaymentRecoveryTask> findByStatusInAndCreatedAtAfterOrderByLastTriedAtDesc(
            Collection<RecoveryStatus> statuses, LocalDateTime after, Pageable pageable);

    long countByStatusInAndCreatedAtAfter(Collection<RecoveryStatus> statuses, LocalDateTime after);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentRecoveryTask t
               set t.status = :processingStatus,
                   t.lastTriedAt = :now
             where t.id = :taskId
               and t.status in :claimableStatuses
               and t.retryCount < t.maxRetryCount
            """)
    int claimForRetry(
            @Param("taskId") Long taskId,
            @Param("processingStatus") RecoveryStatus processingStatus,
            @Param("claimableStatuses") Collection<RecoveryStatus> claimableStatuses,
            @Param("now") LocalDateTime now
    );
}
