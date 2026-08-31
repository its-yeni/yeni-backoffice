package com.yeni.backoffice.core.payment.repository;

import com.yeni.backoffice.core.payment.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop500ByOrderByLoggedAtDesc();
    List<AuditLog> findByDomainTypeAndReferenceKeyOrderByLoggedAtDesc(String domainType, String referenceKey);
}
